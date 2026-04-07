package com.attendance.adminweb.client;

import com.attendance.adminweb.config.BackendAdminApiProperties;
import com.attendance.adminweb.model.AttendanceState;
import com.attendance.adminweb.model.MonthlyAttendanceData;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeDetailRow;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeRow;
import com.attendance.adminweb.model.MonthlyAttendanceRecordRow;
import com.attendance.adminweb.model.MonthlyAttendanceSummary;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class BackendAdminMonthlyAttendanceApiClient {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String ADMIN_CODE_HEADER = "X-Admin-Employee-Code";

    private final RestClient restClient;
    private final BackendAdminApiProperties properties;

    public BackendAdminMonthlyAttendanceApiClient(BackendAdminApiProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
            .baseUrl(properties.getBaseUrl())
            .build();
    }

    public MonthlyAttendanceData getMonthlyAttendance(
        String adminEmployeeCode,
        int year,
        int month,
        String selectedEmployeeCode,
        Long workplaceId
    ) {
        try {
            InternalMonthlyAttendanceResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/internal/admin/attendance/monthly")
                    .queryParam("year", year)
                    .queryParam("month", month)
                    .queryParamIfPresent(
                        "employeeCode",
                        StringUtils.hasText(selectedEmployeeCode) ? Optional.of(selectedEmployeeCode.trim()) : Optional.empty()
                    )
                    .queryParamIfPresent("workplaceId", workplaceId == null ? Optional.empty() : Optional.of(workplaceId))
                    .build())
                .header(API_KEY_HEADER, properties.getInternalKey())
                .header(ADMIN_CODE_HEADER, adminEmployeeCode)
                .retrieve()
                .body(InternalMonthlyAttendanceResponse.class);

            if (response == null || response.summary() == null) {
                throw new IllegalStateException("월별 출근 응답이 비어 있습니다.");
            }

            return new MonthlyAttendanceData(
                new MonthlyAttendanceSummary(
                    response.summary().monthLabel(),
                    response.summary().totalEmployees(),
                    response.summary().attendedEmployees(),
                    response.summary().attendanceCount(),
                    response.summary().lateCount(),
                    response.summary().checkedOutCount()
                ),
                response.employees().stream()
                    .map(row -> new MonthlyAttendanceEmployeeRow(
                        row.employeeCode(),
                        row.employeeName(),
                        row.workplaceName(),
                        row.role(),
                        row.attendanceDays(),
                        row.lateDays(),
                        row.checkedOutDays(),
                        row.lastAttendanceDate(),
                        AttendanceState.valueOf(row.lastState())
                    ))
                    .toList(),
                response.records().stream()
                    .map(row -> new MonthlyAttendanceRecordRow(
                        row.attendanceDate(),
                        row.employeeCode(),
                        row.employeeName(),
                        row.workplaceName(),
                        row.role(),
                        AttendanceState.valueOf(row.state()),
                        row.checkInTime(),
                        row.checkOutTime(),
                        row.note()
                    ))
                    .toList(),
                toDetail(response.selectedEmployeeDetail())
            );
        } catch (HttpStatusCodeException exception) {
            throw new IllegalArgumentException(extractMessage(exception));
        }
    }

    private MonthlyAttendanceEmployeeDetailRow toDetail(InternalMonthlyAttendanceEmployeeDetailResponse detail) {
        if (detail == null) {
            return null;
        }

        return new MonthlyAttendanceEmployeeDetailRow(
            detail.employeeCode(),
            detail.employeeName(),
            detail.workplaceName(),
            detail.role(),
            detail.attendanceDays(),
            detail.lateDays(),
            detail.checkedOutDays(),
            detail.records().stream()
                .map(row -> new MonthlyAttendanceRecordRow(
                    row.attendanceDate(),
                    row.employeeCode(),
                    row.employeeName(),
                    row.workplaceName(),
                    row.role(),
                    AttendanceState.valueOf(row.state()),
                    row.checkInTime(),
                    row.checkOutTime(),
                    row.note()
                ))
                .toList()
        );
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
        return "백엔드 월별 출근 API 호출에 실패했습니다.";
    }

    private record InternalMonthlyAttendanceResponse(
        InternalMonthlyAttendanceSummaryResponse summary,
        List<InternalMonthlyAttendanceEmployeeRowResponse> employees,
        List<InternalMonthlyAttendanceRecordRowResponse> records,
        InternalMonthlyAttendanceEmployeeDetailResponse selectedEmployeeDetail
    ) {
    }

    private record InternalMonthlyAttendanceSummaryResponse(
        String monthLabel,
        int totalEmployees,
        int attendedEmployees,
        int attendanceCount,
        int lateCount,
        int checkedOutCount
    ) {
    }

    private record InternalMonthlyAttendanceEmployeeRowResponse(
        String employeeCode,
        String employeeName,
        String workplaceName,
        String role,
        int attendanceDays,
        int lateDays,
        int checkedOutDays,
        String lastAttendanceDate,
        String lastState
    ) {
    }

    private record InternalMonthlyAttendanceRecordRowResponse(
        String attendanceDate,
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

    private record InternalMonthlyAttendanceEmployeeDetailResponse(
        String employeeCode,
        String employeeName,
        String workplaceName,
        String role,
        int attendanceDays,
        int lateDays,
        int checkedOutDays,
        List<InternalMonthlyAttendanceRecordRowResponse> records
    ) {
    }
}
