package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import com.attendance.adminweb.model.AttendanceRow;
import com.attendance.adminweb.model.AttendanceState;
import com.attendance.adminweb.model.DashboardData;
import com.attendance.adminweb.model.DashboardSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class BackendAdminDashboardApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String ADMIN_CODE_HEADER = "X-Admin-Employee-Code";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminDashboardApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public DashboardData getDashboard(String adminEmployeeCode, String filter, Long workplaceId) {
        try {
            InternalDashboardResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/internal/admin/dashboard")
                    .queryParam("filter", filter)
                    .queryParamIfPresent("workplaceId", workplaceId == null ? Optional.empty() : Optional.of(workplaceId))
                    .build())
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(InternalDashboardResponse.class);

            if (response == null || response.summary() == null) {
                throw new IllegalStateException("대시보드 응답이 비어 있습니다.");
            }

            DashboardSummary summary = new DashboardSummary(
                response.summary().totalEmployees(),
                response.summary().presentCount(),
                response.summary().lateCount(),
                response.summary().absentCount(),
                response.summary().checkedOutCount()
            );

            List<AttendanceRow> attendances = response.attendances().stream()
                .map(row -> new AttendanceRow(
                    row.employeeCode(),
                    row.employeeName(),
                    row.workplaceName(),
                    row.role(),
                    AttendanceState.valueOf(row.state()),
                    row.checkInTime(),
                    row.checkOutTime(),
                    row.note()
                ))
                .toList();

            return new DashboardData(summary, attendances);
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
        return "백엔드 대시보드 API 호출에 실패했습니다.";
    }

    private record InternalDashboardResponse(
        InternalDashboardSummaryResponse summary,
        List<InternalAttendanceRowResponse> attendances
    ) {
    }

    private record InternalDashboardSummaryResponse(
        int totalEmployees,
        int presentCount,
        int lateCount,
        int absentCount,
        int checkedOutCount
    ) {
    }

    private record InternalAttendanceRowResponse(
        String employeeCode,
        String employeeName,
        String workplaceName,
        String role,
        String state,
        String checkInTime,
        String checkOutTime,
        String note
    ) {
    }
}
