package com.siparo.auth;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt in with AUTH_TEST_DB_URL pointing to an isolated PostgreSQL database. Never uses the application DB. */
@EnabledIfEnvironmentVariable(named="AUTH_TEST_DB_URL", matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=${AUTH_TEST_DB_URL}", "spring.datasource.username=siparo_test", "spring.datasource.password=",
    "spring.flyway.url=${AUTH_TEST_DB_URL}", "spring.flyway.user=siparo_test", "spring.flyway.password=",
    "siparo.auth.reset-secret=integration-test-secret-at-least-32-characters"
})
@AutoConfigureMockMvc
class AuthPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean PasswordResetMailService mail;
    @MockBean GoogleIdentityVerifier google;
    Map<String,String> codes = new ConcurrentHashMap<>();
    @BeforeEach void setup() {
        doAnswer(i -> { codes.put(i.getArgument(0), i.getArgument(1)); return null; }).when(mail).send(anyString(), anyString(), any());
    }
    JsonNode postJson(String path, Object body, int status) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/" + path).contentType("application/json").content(mapper.writeValueAsBytes(body)))
            .andExpect(status().is(status)).andReturn();
        String content = result.getResponse().getContentAsString();
        return content.isEmpty() ? mapper.createObjectNode() : mapper.readTree(content);
    }
    Map<String,Object> registration(String role, String email, String phone) {
        return Map.of("email",email,"phoneNumber",phone,"password","old-password","fullName","Test Customer","restaurantName","Test Business","termsAccepted",true,"kvkkAccepted",true);
    }
    @Test void localCustomerFullResetRevokesAccessAndRotatedRefresh() throws Exception {
        String email="local-"+UUID.randomUUID()+"@example.com", phone="05"+System.nanoTime();
        var session=postJson("customer/register",registration("customer",email,phone),200);
        var rotated=postJson("refresh",Map.of("refreshToken",session.get("refreshToken").asText()),200);
        postJson("customer/login",Map.of("phoneNumber",phone,"password","old-password"),200);
        postJson("customer/password-reset/request",Map.of("email",email),200);
        var verified=postJson("customer/password-reset/verify",Map.of("email",email,"code",codes.get(email)),200);
        postJson("customer/password-reset/confirm",Map.of("resetToken",verified.get("resetToken").asText(),"newPassword","new-password"),204);
        mvc.perform(get("/api/v1/customers/me").header("Authorization","Bearer "+session.get("token").asText())).andExpect(status().isUnauthorized());
        postJson("refresh",Map.of("refreshToken",session.get("refreshToken").asText()),401);
        postJson("refresh",Map.of("refreshToken",rotated.get("refreshToken").asText()),401);
        postJson("customer/login",Map.of("phoneNumber",phone,"password","old-password"),401);
        var next=postJson("customer/login",Map.of("phoneNumber",phone,"password","new-password"),200);
        mvc.perform(get("/api/v1/customers/me").header("Authorization","Bearer "+next.get("token").asText())).andExpect(status().isOk());
        postJson("customer/password-reset/verify",Map.of("email",email,"code",codes.get(email)),400);
        postJson("customer/password-reset/confirm",Map.of("resetToken",verified.get("resetToken").asText(),"newPassword","another-password"),400);
    }
    @Test void businessResetRevokesWebJwt() throws Exception {
        String email="business-"+UUID.randomUUID()+"@example.com",phone="05"+System.nanoTime();
        var session=postJson("admin/register",registration("admin",email,phone),200);
        String id=jdbc.queryForObject("select id::text from restaurants where email=?",String.class,email);
        postJson("admin/password-reset/request",Map.of("email",email),200);
        var grant=postJson("admin/password-reset/verify",Map.of("email",email,"code",codes.get(email)),200);
        postJson("admin/password-reset/confirm",Map.of("resetToken",grant.get("resetToken").asText(),"newPassword","new-password"),204);
        mvc.perform(get("/api/v1/restaurants/"+id+"/settings").header("Authorization","Bearer "+session.get("token").asText())).andExpect(status().isUnauthorized());
        postJson("admin/login",Map.of("phoneNumber",phone,"password","new-password"),200);
    }
    @Test void unknownEmailAndKnownEmailHaveSameResponseAndCooldown() throws Exception {
        String email="unknown-"+UUID.randomUUID()+"@example.com";
        var response=postJson("customer/password-reset/request",Map.of("email",email),200);
        assertThat(response.get("expiresInSeconds").asInt()).isEqualTo(600);
        assertThat(codes.containsKey(email)).isFalse();
        postJson("customer/password-reset/request",Map.of("email",email),429);
        String key=AuthAbuseGuard.hash("customer:"+email);
        for(int i=0;i<5;i++) postJson("customer/password-reset/verify",Map.of("email",email,"code","000000"),400);
        assertThat(jdbc.queryForObject("select attempts from email_password_resets where reset_key=?",Integer.class,key)).isEqualTo(5);
    }
    @Test void googleRepeatedSignInAndLocalLinkDoNotDuplicateAccounts() throws Exception {
        String email="google-"+UUID.randomUUID()+"@example.com",subject=UUID.randomUUID().toString();
        when(google.verify("customer","verified-google")).thenReturn(new GoogleIdentityVerifier.Identity(subject,email,"Google Test"));
        var body=Map.of("idToken","verified-google","phoneNumber","05"+System.nanoTime(),"termsAccepted",true,"kvkkAccepted",true);
        postJson("customer/google",body,200); postJson("customer/google",Map.of("idToken","verified-google"),200);
        assertThat(jdbc.queryForObject("select count(*) from customers where email=?",Integer.class,email)).isEqualTo(1);
        postJson("customer/password-reset/request",Map.of("email",email),200); assertThat(codes.containsKey(email)).isFalse();
        String local="link-"+UUID.randomUUID()+"@example.com";
        postJson("customer/register",registration("customer",local,"05"+System.nanoTime()),200);
        when(google.verify("customer","link-token")).thenReturn(new GoogleIdentityVerifier.Identity(UUID.randomUUID().toString(),local,"Verified"));
        var required=postJson("customer/google",Map.of("idToken","link-token"),400);
        assertThat(required.get("code").asText()).isEqualTo("GOOGLE_LINK_REQUIRED");
        postJson("customer/google",Map.of("idToken","link-token","password","old-password"),200);
        assertThat(jdbc.queryForObject("select count(*) from customers where email=?",Integer.class,local)).isEqualTo(1);
    }
    @Test void simultaneousGrantConsumptionAllowsOneWinner() throws Exception {
        String email="race-"+UUID.randomUUID()+"@example.com";
        postJson("customer/register",registration("customer",email,"05"+System.nanoTime()),200);
        postJson("customer/password-reset/request",Map.of("email",email),200);
        var grant=postJson("customer/password-reset/verify",Map.of("email",email,"code",codes.get(email)),200);
        byte[] body=mapper.writeValueAsBytes(Map.of("resetToken",grant.get("resetToken").asText(),"newPassword","new-password"));
        var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> action=() -> mvc.perform(post("/api/v1/auth/customer/password-reset/confirm").contentType("application/json").content(body)).andReturn().getResponse().getStatus();
            var a=pool.submit(action); var b=pool.submit(action);
            assertThat(List.of(a.get(),b.get())).containsExactlyInAnyOrder(204,400);
        } finally { pool.shutdownNow(); }
    }
}
