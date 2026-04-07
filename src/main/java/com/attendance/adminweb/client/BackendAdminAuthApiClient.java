package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class BackendAdminAuthApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminAuthApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public AdminUserDetailsResponse getAdminUserDetails(String employeeCode) {
        try {
            AdminUserDetailsResponse response = restClient.get()
                .uri("/api/internal/admin/auth/users/{employeeCode}", employeeCode)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .retrieve()
                .body(AdminUserDetailsResponse.class);

            if (response == null) {
                throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
            }

            return response;
        } catch (HttpStatusCodeException exception) {
            throw new UsernameNotFoundException(extractMessage(exception));
        }
    }

    private String extractMessage(HttpStatusCodeException exception) {
        String body = exception.getResponseBodyAsString();
        if (StringUtils.hasText(body) && body.contains("\"message\"")) {
            int keyIndex = body.indexOf("\"message\"");
            int colonIndex = body.indexOf(':', keyIndex);
            int startQuote = body.indexOf('"', colonIndex + 1);
            int endQuote = body.indexOf('"', startQuote + 1);
            if (startQuote >= 0 && endQuote > startQuote) {
                return body.substring(startQuote + 1, endQuote);
            }
        }
        return "사용자를 찾을 수 없습니다.";
    }

    public record AdminUserDetailsResponse(
        String employeeCode,
        String password,
        String role,
        boolean active
    ) {
    }

    public static class UsernameNotFoundException extends org.springframework.security.core.userdetails.UsernameNotFoundException {
        public UsernameNotFoundException(String message) {
            super(message);
        }
    }
}
