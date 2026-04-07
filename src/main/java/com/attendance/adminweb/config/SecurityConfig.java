package com.attendance.adminweb.config;

import com.attendance.adminweb.service.BackendAdminUserDetailsService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   BackendAdminUserDetailsService backendAdminUserDetailsService,
                                                   AuthenticationFailureHandler authenticationFailureHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**").permitAll()
                        .requestMatchers("/js/**").permitAll()
                        .requestMatchers("/vendor/**").permitAll()
                        .requestMatchers("/app/**").permitAll()
                        .requestMatchers("/login").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", true)
                        .failureHandler(authenticationFailureHandler)
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .alwaysRemember(true)
                        .tokenValiditySeconds(31536000)
                        .key("attendance-admin-remember-me")
                        .userDetailsService(backendAdminUserDetailsService)
                )
                .logout(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String message = URLEncoder.encode(resolveFailureMessage(exception), StandardCharsets.UTF_8);
            response.sendRedirect("/login?error&message=" + message);
        };
    }

    private String resolveFailureMessage(AuthenticationException exception) {
        String baseMessage = "로그인에 실패했습니다. 관리자 또는 사업장 관리자 계정으로 로그인해 주세요.";
        String detail = exception.getMessage();

        if (detail == null || detail.isBlank()) {
            return baseMessage + " 비밀번호도 다시 확인해 주세요.";
        }

        return baseMessage + " 원인: " + detail;
    }
}
