package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import com.attendance.adminweb.model.AttendanceState;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeeInviteResult;
import com.attendance.adminweb.model.EmployeePage;
import com.attendance.adminweb.model.EmployeeRow;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.model.InviteEmployeeForm;
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
public class BackendAdminEmployeeApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String ADMIN_CODE_HEADER = "X-Admin-Employee-Code";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminEmployeeApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public EmployeePage getEmployees(String adminEmployeeCode, boolean showDeleted, Long workplaceId, int page, int pageSize) {
        try {
            InternalEmployeePageResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/internal/admin/employees")
                    .queryParam("showDeleted", showDeleted)
                    .queryParam("page", page)
                    .queryParam("pageSize", pageSize)
                    .queryParamIfPresent("workplaceId", workplaceId == null ? java.util.Optional.empty() : java.util.Optional.of(workplaceId))
                    .build())
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(InternalEmployeePageResponse.class);

            if (response == null) {
                throw new IllegalStateException("직원 목록 응답이 비어 있습니다.");
            }

            List<EmployeeRow> rows = response.employees().stream()
                .map(employee -> new EmployeeRow(
                    employee.id(),
                    employee.employeeCode(),
                    employee.name(),
                    employee.workplaceName(),
                    employee.role(),
                    employee.workStartTime(),
                    employee.workEndTime(),
                    parseAttendanceState(employee.attendanceState()),
                    employee.checkInTime(),
                    employee.checkOutTime(),
                    employee.deviceRegistered(),
                    employee.active(),
                    employee.deleted()
                ))
                .toList();

            return new EmployeePage(
                rows,
                response.currentPage(),
                response.totalPages(),
                response.totalCount(),
                response.pageSize(),
                response.hasPrevious(),
                response.hasNext()
            );
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public EmployeeForm getEmployeeForm(String adminEmployeeCode, Long employeeId) {
        try {
            InternalEmployeeFormResponse response = restClient.get()
                .uri("/api/internal/admin/employees/{employeeId}", employeeId)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(InternalEmployeeFormResponse.class);

            if (response == null) {
                throw new IllegalStateException("직원 상세 응답이 비어 있습니다.");
            }

            EmployeeForm form = new EmployeeForm();
            form.setId(response.id());
            form.setEmployeeCode(response.employeeCode());
            form.setName(response.name());
            form.setRole(response.role());
            form.setPassword("");
            form.setWorkStartTime(emptyIfHyphen(response.workStartTime()));
            form.setWorkEndTime(emptyIfHyphen(response.workEndTime()));
            form.setWorkplaceId(response.workplaceId());
            return form;
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void createEmployee(String adminEmployeeCode, EmployeeForm form) {
        try {
            restClient.post()
                .uri("/api/internal/admin/employees")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(toRequest(form))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void updateEmployee(String adminEmployeeCode, Long employeeId, EmployeeForm form) {
        try {
            restClient.patch()
                .uri("/api/internal/admin/employees/{employeeId}", employeeId)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(toRequest(form))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public EmployeeInviteResult createInviteForExistingEmployee(String adminEmployeeCode, Long employeeId) {
        try {
            InternalEmployeeInviteResponse response = restClient.post()
                .uri("/api/internal/admin/employees/{employeeId}/invite-link", employeeId)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(InternalEmployeeInviteResponse.class);

            if (response == null) {
                throw new IllegalStateException("직원 초대 응답이 비어 있습니다.");
            }

            return new EmployeeInviteResult(
                response.employeeCode(),
                response.employeeName(),
                response.role(),
                response.workplaceName(),
                response.inviteUrl(),
                response.expiresAt(),
                response.message()
            );
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public EmployeeInviteResult createEmployeeInvite(String adminEmployeeCode, InviteEmployeeForm form) {
        try {
            InternalEmployeeInviteResponse response = restClient.post()
                .uri("/api/internal/admin/employees/invite")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .body(new InternalEmployeeInviteCreateRequest(
                    form.getEmployeeCode(),
                    form.getName(),
                    form.getRole(),
                    form.getWorkplaceId()
                ))
                .retrieve()
                .body(InternalEmployeeInviteResponse.class);

            if (response == null) {
                throw new IllegalStateException("직원 초대 응답이 비어 있습니다.");
            }

            return new EmployeeInviteResult(
                response.employeeCode(),
                response.employeeName(),
                response.role(),
                response.workplaceName(),
                response.inviteUrl(),
                response.expiresAt(),
                response.message()
            );
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    public void updateEmployeeUsage(String adminEmployeeCode, Long employeeId, boolean active) {
        postMessageOnly("/api/internal/admin/employees/{employeeId}/usage?active={active}", adminEmployeeCode, employeeId, active);
    }

    public void resetEmployeeDevice(String adminEmployeeCode, Long employeeId) {
        postMessageOnly("/api/internal/admin/employees/{employeeId}/device-reset", adminEmployeeCode, employeeId);
    }

    public void deleteEmployee(String adminEmployeeCode, Long employeeId) {
        postMessageOnly("/api/internal/admin/employees/{employeeId}/delete", adminEmployeeCode, employeeId);
    }

    public void restoreEmployee(String adminEmployeeCode, Long employeeId) {
        postMessageOnly("/api/internal/admin/employees/{employeeId}/restore", adminEmployeeCode, employeeId);
    }

    public EmployeeUploadResult uploadEmployees(String adminEmployeeCode, MultipartFile file) {
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
            body.add("employeeFile", fileResource);

            InternalEmployeeUploadResponse response = restClient.post()
                .uri("/api/internal/admin/employees/upload")
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(InternalEmployeeUploadResponse.class);

            if (response == null) {
                throw new IllegalStateException("직원 업로드 응답이 비어 있습니다.");
            }

            return new EmployeeUploadResult(response.successCount(), response.failureMessages());
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    private void postMessageOnly(String uri, String adminEmployeeCode, Object... uriVariables) {
        try {
            restClient.post()
                .uri(uri, uriVariables)
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    private InternalEmployeeUpsertRequest toRequest(EmployeeForm form) {
        InternalEmployeeUpsertRequest request = new InternalEmployeeUpsertRequest();
        request.setEmployeeCode(form.getEmployeeCode());
        request.setName(form.getName());
        request.setRole(form.getRole());
        request.setPassword(form.getPassword());
        request.setWorkStartTime(form.getWorkStartTime());
        request.setWorkEndTime(form.getWorkEndTime());
        request.setWorkplaceId(form.getWorkplaceId());
        return request;
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
        return "백엔드 직원 API 호출에 실패했습니다.";
    }

    private String emptyIfHyphen(String value) {
        return "-".equals(value) ? "" : value;
    }

    private AttendanceState parseAttendanceState(String value) {
        if (!StringUtils.hasText(value)) {
            return AttendanceState.ABSENT;
        }

        try {
            return AttendanceState.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return AttendanceState.ABSENT;
        }
    }

    private record InternalEmployeePageResponse(
        List<InternalEmployeeRowResponse> employees,
        int currentPage,
        int totalPages,
        long totalCount,
        int pageSize,
        boolean hasPrevious,
        boolean hasNext
    ) {
    }

    private record InternalEmployeeRowResponse(
        Long id,
        String employeeCode,
        String name,
        String workplaceName,
        String role,
        String workStartTime,
        String workEndTime,
        String attendanceState,
        String checkInTime,
        String checkOutTime,
        boolean deviceRegistered,
        boolean active,
        boolean deleted
    ) {
    }

    private record InternalEmployeeFormResponse(
        Long id,
        String employeeCode,
        String name,
        String role,
        String workStartTime,
        String workEndTime,
        Long workplaceId
    ) {
    }

    private record InternalEmployeeInviteResponse(
        String employeeCode,
        String employeeName,
        String role,
        String workplaceName,
        String inviteUrl,
        String expiresAt,
        String message
    ) {
    }

    private record InternalEmployeeInviteCreateRequest(
        String employeeCode,
        String name,
        String role,
        Long workplaceId
    ) {
    }

    private record InternalEmployeeUploadResponse(
        int successCount,
        int failureCount,
        List<String> failureMessages
    ) {
    }

    private static class InternalEmployeeUpsertRequest {
        private String employeeCode;
        private String name;
        private String role;
        private String password;
        private String workStartTime;
        private String workEndTime;
        private Long workplaceId;

        public String getEmployeeCode() {
            return employeeCode;
        }

        public void setEmployeeCode(String employeeCode) {
            this.employeeCode = employeeCode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getWorkStartTime() {
            return workStartTime;
        }

        public void setWorkStartTime(String workStartTime) {
            this.workStartTime = workStartTime;
        }

        public String getWorkEndTime() {
            return workEndTime;
        }

        public void setWorkEndTime(String workEndTime) {
            this.workEndTime = workEndTime;
        }

        public Long getWorkplaceId() {
            return workplaceId;
        }

        public void setWorkplaceId(Long workplaceId) {
            this.workplaceId = workplaceId;
        }
    }
}
