package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.CompanyLocationView;
import com.attendance.adminweb.model.WorkplaceLocationForm;
import com.attendance.adminweb.model.WorkplaceLocationView;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class BackendAdminLocationApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String ADMIN_CODE_HEADER = "X-Admin-Employee-Code";
    private static final String DEFAULT_MOBILE_SKIN_KEY = "classic";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminLocationApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public LocationSettingsResponse getLocationSettings(String adminEmployeeCode) {
        try {
            LocationSettingsResponse response = restClient.get()
                .uri("/api/internal/admin/settings/location")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(LocationSettingsResponse.class);

            if (response == null) {
                throw new IllegalStateException("위치 설정 응답이 비어 있습니다.");
            }

            return response;
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void updateCompanyLocation(String adminEmployeeCode, CompanyLocationForm form) {
        try {
            restClient.patch()
                .uri("/api/internal/admin/settings/location")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(new CompanyLocationRequest(
                    form.getCompanyName(),
                    form.getLatitude(),
                    form.getLongitude(),
                    form.getAllowedRadiusMeters(),
                    form.getNoticeMessage(),
                    normalizeMobileSkinKey(form.getMobileSkinKey()),
                    form.isEnforceSingleDeviceLogin()
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void createWorkplace(String adminEmployeeCode, WorkplaceLocationForm form) {
        try {
            restClient.post()
                .uri("/api/internal/admin/settings/location/workplaces")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(new WorkplaceLocationRequest(
                    form.getName(),
                    form.getLatitude(),
                    form.getLongitude(),
                    form.getAllowedRadiusMeters(),
                    form.getNoticeMessage()
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void updateWorkplace(String adminEmployeeCode, Long workplaceId, WorkplaceLocationForm form) {
        try {
            restClient.patch()
                .uri("/api/internal/admin/settings/location/workplaces/{workplaceId}", workplaceId)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(new WorkplaceLocationRequest(
                    form.getName(),
                    form.getLatitude(),
                    form.getLongitude(),
                    form.getAllowedRadiusMeters(),
                    form.getNoticeMessage()
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    private String normalizeMobileSkinKey(String mobileSkinKey) {
        if (!StringUtils.hasText(mobileSkinKey)) {
            return DEFAULT_MOBILE_SKIN_KEY;
        }
        return switch (mobileSkinKey.trim().toLowerCase()) {
            case "ocean" -> "ocean";
            case "sunset" -> "sunset";
            default -> DEFAULT_MOBILE_SKIN_KEY;
        };
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
        return "백엔드 위치 설정 API 호출에 실패했습니다.";
    }

    public record LocationSettingsResponse(
        String companyName,
        Double latitude,
        Double longitude,
        Integer allowedRadiusMeters,
        String lateAfterTime,
        String noticeMessage,
        String mobileSkinKey,
        boolean enforceSingleDeviceLogin,
        boolean workplaceScopedAdmin,
        Long assignedWorkplaceId,
        List<WorkplaceLocationView> workplaces
    ) {
        public CompanyLocationView toCompanyLocationView() {
            return new CompanyLocationView(
                companyName,
                latitude == null ? 0D : latitude,
                longitude == null ? 0D : longitude,
                allowedRadiusMeters == null ? 0 : allowedRadiusMeters,
                lateAfterTime == null ? "09:00" : lateAfterTime,
                noticeMessage,
                StringUtils.hasText(mobileSkinKey) ? mobileSkinKey : DEFAULT_MOBILE_SKIN_KEY,
                enforceSingleDeviceLogin
            );
        }
    }

    private record CompanyLocationRequest(
        String companyName,
        Double latitude,
        Double longitude,
        Integer allowedRadiusMeters,
        String noticeMessage,
        String mobileSkinKey,
        boolean enforceSingleDeviceLogin
    ) {
    }

    private record WorkplaceLocationRequest(
        String name,
        Double latitude,
        Double longitude,
        Integer allowedRadiusMeters,
        String noticeMessage
    ) {
    }
}
