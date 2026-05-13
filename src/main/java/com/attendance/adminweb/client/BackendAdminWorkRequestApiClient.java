package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import com.attendance.adminweb.model.WorkRequestCreateForm;
import com.attendance.adminweb.model.WorkRequestRow;
import com.attendance.adminweb.model.WorkRequestUploadResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Component
public class BackendAdminWorkRequestApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String ADMIN_CODE_HEADER = "X-Admin-Employee-Code";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminWorkRequestApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public WorkRequestListResponse getWorkRequests(String adminEmployeeCode) {
        try {
            WorkRequestListResponse response = restClient.get()
                .uri("/api/internal/admin/work-requests")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(WorkRequestListResponse.class);

            if (response == null) {
                throw new IllegalStateException("근무 신청 응답이 비어 있습니다.");
            }
            return response;
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void approveWorkRequest(String adminEmployeeCode, Long requestId, String reviewNote) {
        submitReview(adminEmployeeCode, requestId, "/approve", reviewNote);
    }

    public void rejectWorkRequest(String adminEmployeeCode, Long requestId, String reviewNote) {
        submitReview(adminEmployeeCode, requestId, "/reject", reviewNote);
    }

    public void createWorkRequest(String adminEmployeeCode, WorkRequestCreateForm form) {
        try {
            restClient.post()
                .uri("/api/internal/admin/work-requests")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(new CreateRequest(
                    form.getEmployeeCode(),
                    form.getRequestType(),
                    form.getRequestDate(),
                    form.getHalfDayType(),
                    form.getEarlyLeaveMinutes(),
                    form.getReason()
                ))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public WorkRequestUploadResult uploadWorkRequests(String adminEmployeeCode, MultipartFile file) {
        try {
            byte[] bytes;
            try (InputStream inputStream = file.getInputStream()) {
                bytes = inputStream.readAllBytes();
            } catch (IOException exception) {
                throw new IllegalArgumentException("엑셀 파일을 읽는 중 오류가 발생했습니다.");
            }

            ByteArrayResource fileResource = new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("workRequestFile", fileResource);

            UploadResponse response = restClient.post()
                .uri("/api/internal/admin/work-requests/upload")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(UploadResponse.class);

            if (response == null) {
                throw new IllegalStateException("근무 신청 업로드 응답이 비어 있습니다.");
            }
            return new WorkRequestUploadResult(response.successCount(), response.failureMessages());
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    private void submitReview(String adminEmployeeCode, Long requestId, String pathSuffix, String reviewNote) {
        try {
            restClient.post()
                .uri("/api/internal/admin/work-requests/{requestId}" + pathSuffix, requestId)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(new ReviewRequest(reviewNote))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
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
        return "백엔드 근무 신청 API 호출에 실패했습니다.";
    }

    public record WorkRequestListResponse(
        boolean approvalRequired,
        boolean workplaceScopedAdmin,
        List<WorkRequestRow> requests
    ) {
    }

    private record ReviewRequest(String reviewNote) {
    }

    private record CreateRequest(
        String employeeCode,
        String requestType,
        String requestDate,
        String halfDayType,
        Integer earlyLeaveMinutes,
        String reason
    ) {
    }

    private record UploadResponse(
        int successCount,
        int failureCount,
        List<String> failureMessages
    ) {
    }
}
