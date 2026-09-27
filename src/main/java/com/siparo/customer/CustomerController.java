package com.siparo.customer;

import com.siparo.auth.AuthService;
import com.siparo.common.security.CurrentUser;
import com.siparo.restaurant.RestaurantDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerController {

    private final CustomerService customerService;

    // ---------- Profil ----------

    @GetMapping("/me")
    public ResponseEntity<CustomerDto> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(customerService.getCustomer(CurrentUser.id(authentication)));
    }

    @PutMapping("/me")
    public ResponseEntity<CustomerDto> updateProfile(Authentication authentication, @Valid @RequestBody CustomerRequests.UpdateProfile request) {
        return ResponseEntity.ok(customerService.updateProfile(CurrentUser.id(authentication), request));
    }

    /** Şifre değişince diğer cihazlardaki oturumlar kapanır; bu cihaz yeni oturumu ({ token, role, refreshToken }) kullanır. */
    @PostMapping("/me/password")
    public ResponseEntity<AuthService.Session> changePassword(Authentication authentication, @Valid @RequestBody CustomerRequests.ChangePassword request) {
        return ResponseEntity.ok(customerService.changePassword(CurrentUser.id(authentication), request));
    }

    @PutMapping("/me/notification-preferences")
    public ResponseEntity<CustomerDto> updatePreferences(Authentication authentication,
                                                         @Valid @RequestBody CustomerRequests.NotificationPreferences request) {
        return ResponseEntity.ok(customerService.updatePreferences(CurrentUser.id(authentication), request));
    }

    /** Hesap silme: şifre onayı gerekir (POST, çünkü DELETE gövdesi bazı istemcilerde desteklenmez). */
    @PostMapping("/me/delete")
    public ResponseEntity<Void> deleteAccount(Authentication authentication, @Valid @RequestBody CustomerRequests.DeleteAccount request) {
        customerService.deleteAccount(CurrentUser.id(authentication), request);
        return ResponseEntity.noContent().build();
    }

    // ---------- Restoranlarım ----------

    @GetMapping("/me/restaurants")
    public ResponseEntity<List<RestaurantDto>> getMyRestaurants(Authentication authentication) {
        return ResponseEntity.ok(customerService.getCustomerRestaurants(CurrentUser.id(authentication)));
    }

    @PostMapping("/me/restaurants/{restaurantId}")
    public ResponseEntity<CustomerService.LinkResult> addRestaurant(Authentication authentication, @PathVariable UUID restaurantId,
                                                                    @Valid @RequestBody(required = false) CustomerRequests.LinkRestaurant request) {
        return ResponseEntity.ok(customerService.linkCustomerToRestaurant(
                CurrentUser.id(authentication), restaurantId, request == null ? null : request.source()));
    }

    @PostMapping("/me/restaurants/by-code/{code}")
    public ResponseEntity<CustomerService.LinkResult> addRestaurantByCode(Authentication authentication, @PathVariable String code,
                                                                          @Valid @RequestBody(required = false) CustomerRequests.LinkRestaurant request) {
        return ResponseEntity.ok(customerService.linkByPublicCode(
                CurrentUser.id(authentication), code, request == null ? null : request.source()));
    }

    @PutMapping("/me/restaurants/{restaurantId}/favorite")
    public ResponseEntity<Void> setFavorite(Authentication authentication, @PathVariable UUID restaurantId,
                                            @Valid @RequestBody CustomerRequests.Favorite request) {
        customerService.setFavorite(CurrentUser.id(authentication), restaurantId, request.favorite());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/restaurants/{restaurantId}")
    public ResponseEntity<Void> removeRestaurant(Authentication authentication, @PathVariable UUID restaurantId) {
        customerService.removeRestaurant(CurrentUser.id(authentication), restaurantId);
        return ResponseEntity.noContent().build();
    }

    // ---------- Adresler ----------

    @GetMapping("/me/addresses")
    public ResponseEntity<List<CustomerAddressDto>> getMyAddresses(Authentication authentication) {
        return ResponseEntity.ok(customerService.getCustomerAddresses(CurrentUser.id(authentication)));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<CustomerAddressDto> addAddress(Authentication authentication, @Valid @RequestBody CreateCustomerAddressRequest request) {
        return ResponseEntity.ok(customerService.addCustomerAddress(CurrentUser.id(authentication), request));
    }

    @PutMapping("/me/addresses/{addressId}")
    public ResponseEntity<CustomerAddressDto> updateAddress(Authentication authentication, @PathVariable UUID addressId,
                                                            @Valid @RequestBody CreateCustomerAddressRequest request) {
        return ResponseEntity.ok(customerService.updateCustomerAddress(CurrentUser.id(authentication), addressId, request));
    }

    @DeleteMapping("/me/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(Authentication authentication, @PathVariable UUID addressId) {
        customerService.deleteCustomerAddress(CurrentUser.id(authentication), addressId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/addresses/{addressId}/default")
    public ResponseEntity<CustomerAddressDto> setDefaultAddress(Authentication authentication, @PathVariable UUID addressId) {
        return ResponseEntity.ok(customerService.setDefaultAddress(CurrentUser.id(authentication), addressId));
    }
}
