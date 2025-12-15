package com.company.company_clean_hub_be.security;

import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private Long id;
    private String username;
    private String password;
    private String email;
    private String userType;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal create(User user) {
        // Load authorities from role name + permissions
        Collection<GrantedAuthority> authorities = new java.util.ArrayList<>();
        
        // Add role as authority (ROLE_ prefix for Spring Security)
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
        
        // Add permissions as authorities
        if (user.getRole().getPermissions() != null) {
            user.getRole().getPermissions().forEach(permission -> 
                authorities.add(new SimpleGrantedAuthority(permission.name()))
            );
        }

        String userType = "USER";
        if (user instanceof Employee) {
            userType = "EMPLOYEE";
        } else if (user instanceof Customer) {
            userType = "CUSTOMER";
        }

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getEmail(),
                userType,
                authorities
        );
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
