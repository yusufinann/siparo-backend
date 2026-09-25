package com.siparo.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/public/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("RESTAURANT_ADMIN")
                .requestMatchers("/api/v1/customers/**").hasRole("CUSTOMER")
                .requestMatchers("/api/v1/couriers/**").hasRole("COURIER")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                // İstemci sözleşmesi: oturum yok/geçersiz → 401 UNAUTHORIZED, yetki yok → 403 FORBIDDEN (JSON).
                .authenticationEntryPoint((request, response, ex) -> writeError(response, 401, "Unauthorized", "UNAUTHORIZED"))
                .accessDeniedHandler((request, response, ex) -> writeError(response, 403, "Forbidden", "FORBIDDEN")))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String error, String code)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"code\":\"" + code
                + "\",\"message\":\"" + error + "\",\"timestamp\":\"" + LocalDateTime.now() + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
