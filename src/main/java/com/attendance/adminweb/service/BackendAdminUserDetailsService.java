package com.attendance.adminweb.service;

import com.attendance.adminweb.client.BackendAdminAuthApiClient;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class BackendAdminUserDetailsService implements UserDetailsService {

    private final BackendAdminAuthApiClient backendAdminAuthApiClient;

    public BackendAdminUserDetailsService(BackendAdminAuthApiClient backendAdminAuthApiClient) {
        this.backendAdminAuthApiClient = backendAdminAuthApiClient;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        BackendAdminAuthApiClient.AdminUserDetailsResponse userDetails =
            backendAdminAuthApiClient.getAdminUserDetails(username);

        return new User(
            userDetails.employeeCode(),
            userDetails.password(),
            userDetails.active(),
            true,
            true,
            true,
            List.of(new SimpleGrantedAuthority("ROLE_" + userDetails.role()))
        );
    }
}
