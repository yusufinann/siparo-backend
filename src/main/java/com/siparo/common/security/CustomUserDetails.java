package com.siparo.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public class CustomUserDetails implements UserDetails {

    private final String id;
    private final String username; // can be phone number or email
    private final String password;
    private final String restaurantId; // null if customer
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(String id, String username, String password, String restaurantId, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.restaurantId = restaurantId;
        this.authorities = authorities;
    }

    public String getId() {
        return id;
    }

    public String getRestaurantId() {
        return restaurantId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
