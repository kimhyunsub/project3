package com.attendance.adminweb.service;

import com.attendance.adminweb.client.BackendAdminAuthApiClient;
import com.attendance.adminweb.client.BackendAdminDashboardApiClient;
import com.attendance.adminweb.client.BackendAdminEmployeeApiClient;
import com.attendance.adminweb.client.BackendAdminLocationApiClient;
import com.attendance.adminweb.client.BackendAdminMonthlyAttendanceApiClient;
import com.attendance.adminweb.client.BackendAdminSqlApiClient;
import com.attendance.adminweb.model.AttendanceRow;
import com.attendance.adminweb.model.AttendanceState;
import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.CompanyLocationView;
import com.attendance.adminweb.model.DashboardData;
import com.attendance.adminweb.model.DashboardSummary;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeeInviteResult;
import com.attendance.adminweb.model.EmployeePage;
import com.attendance.adminweb.model.EmployeeRow;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.model.InviteEmployeeForm;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeDetailRow;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeRow;
import com.attendance.adminweb.model.MonthlyAttendanceData;
import com.attendance.adminweb.model.MonthlyAttendanceRecordRow;
import com.attendance.adminweb.model.MonthlyAttendanceSummary;
import com.attendance.adminweb.model.SqlQueryResult;
import com.attendance.adminweb.model.SqlSnippet;
import com.attendance.adminweb.model.WorkplaceLocationForm;
import com.attendance.adminweb.model.WorkplaceLocationView;
import com.attendance.adminweb.model.WorkplaceOption;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminService {
    private final BackendAdminAuthApiClient backendAdminAuthApiClient;
    private final BackendAdminDashboardApiClient backendAdminDashboardApiClient;
    private final BackendAdminMonthlyAttendanceApiClient backendAdminMonthlyAttendanceApiClient;
    private final BackendAdminEmployeeApiClient backendAdminEmployeeApiClient;
    private final BackendAdminLocationApiClient backendAdminLocationApiClient;
    private final BackendAdminSqlApiClient backendAdminSqlApiClient;

    public AdminService(BackendAdminAuthApiClient backendAdminAuthApiClient,
                        BackendAdminDashboardApiClient backendAdminDashboardApiClient,
                        BackendAdminMonthlyAttendanceApiClient backendAdminMonthlyAttendanceApiClient,
                        BackendAdminEmployeeApiClient backendAdminEmployeeApiClient,
                        BackendAdminLocationApiClient backendAdminLocationApiClient,
                        BackendAdminSqlApiClient backendAdminSqlApiClient) {
        this.backendAdminAuthApiClient = backendAdminAuthApiClient;
        this.backendAdminDashboardApiClient = backendAdminDashboardApiClient;
        this.backendAdminMonthlyAttendanceApiClient = backendAdminMonthlyAttendanceApiClient;
        this.backendAdminEmployeeApiClient = backendAdminEmployeeApiClient;
        this.backendAdminLocationApiClient = backendAdminLocationApiClient;
        this.backendAdminSqlApiClient = backendAdminSqlApiClient;
    }

    public DashboardSummary getTodaySummary(String employeeCode, Long workplaceId) {
        return getTodayDashboard(employeeCode, "ALL", workplaceId).summary();
    }

    public List<AttendanceRow> getTodayAttendances(String employeeCode, String filter, Long workplaceId) {
        return getTodayDashboard(employeeCode, filter, workplaceId).attendances();
    }

    public DashboardData getTodayDashboard(String employeeCode, String filter, Long workplaceId) {
        return backendAdminDashboardApiClient.getDashboard(employeeCode, normalizeDashboardFilter(filter), workplaceId);
    }

    public MonthlyAttendanceSummary getMonthlyAttendanceSummary(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        return getMonthlyAttendanceData(employeeCode, yearMonth, null, workplaceId).summary();
    }

    public List<MonthlyAttendanceEmployeeRow> getMonthlyAttendanceEmployees(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        return getMonthlyAttendanceData(employeeCode, yearMonth, null, workplaceId).employees();
    }

    public List<MonthlyAttendanceRecordRow> getMonthlyAttendanceRecords(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        return getMonthlyAttendanceData(employeeCode, yearMonth, null, workplaceId).records();
    }

    public MonthlyAttendanceEmployeeDetailRow getMonthlyAttendanceEmployeeDetail(String employeeCode,
                                                                                 YearMonth yearMonth,
                                                                                 String selectedEmployeeCode,
                                                                                 Long workplaceId) {
        return getMonthlyAttendanceData(employeeCode, yearMonth, selectedEmployeeCode, workplaceId).selectedEmployeeDetail();
    }

    public MonthlyAttendanceData getMonthlyAttendanceData(
        String employeeCode,
        YearMonth yearMonth,
        String selectedEmployeeCode,
        Long workplaceId
    ) {
        return backendAdminMonthlyAttendanceApiClient.getMonthlyAttendance(
            employeeCode,
            yearMonth.getYear(),
            yearMonth.getMonthValue(),
            selectedEmployeeCode,
            workplaceId
        );
    }

    public byte[] exportMonthlyAttendanceExcel(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        List<MonthlyAttendanceEmployeeRow> employeeRows = getMonthlyAttendanceEmployees(employeeCode, yearMonth, workplaceId);
        List<MonthlyAttendanceRecordRow> recordRows = getMonthlyAttendanceRecords(employeeCode, yearMonth, workplaceId);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor((short) 22);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet summarySheet = workbook.createSheet("직원별 요약");
            Row summaryHeaderRow = summarySheet.createRow(0);
            String[] summaryHeaders = {"사번", "이름", "사업장", "권한", "출근일수", "지각일수", "퇴근완료", "최근 출근일", "최근 상태"};
            for (int index = 0; index < summaryHeaders.length; index++) {
                Cell cell = summaryHeaderRow.createCell(index);
                cell.setCellValue(summaryHeaders[index]);
                cell.setCellStyle(headerStyle);
            }

            for (int index = 0; index < employeeRows.size(); index++) {
                MonthlyAttendanceEmployeeRow row = employeeRows.get(index);
                Row sheetRow = summarySheet.createRow(index + 1);
                sheetRow.createCell(0).setCellValue(row.employeeCode());
                sheetRow.createCell(1).setCellValue(row.employeeName());
                sheetRow.createCell(2).setCellValue(row.workplaceName());
                sheetRow.createCell(3).setCellValue(row.role());
                sheetRow.createCell(4).setCellValue(row.attendanceDays());
                sheetRow.createCell(5).setCellValue(row.lateDays());
                sheetRow.createCell(6).setCellValue(row.checkedOutDays());
                sheetRow.createCell(7).setCellValue(row.lastAttendanceDate());
                sheetRow.createCell(8).setCellValue(row.lastState().getLabel());
            }

            Sheet detailSheet = workbook.createSheet("출근 상세");
            Row detailHeaderRow = detailSheet.createRow(0);
            String[] detailHeaders = {"날짜", "사번", "이름", "사업장", "권한", "상태", "출근 시간", "퇴근 시간", "메모"};
            for (int index = 0; index < detailHeaders.length; index++) {
                Cell cell = detailHeaderRow.createCell(index);
                cell.setCellValue(detailHeaders[index]);
                cell.setCellStyle(headerStyle);
            }

            for (int index = 0; index < recordRows.size(); index++) {
                MonthlyAttendanceRecordRow row = recordRows.get(index);
                Row sheetRow = detailSheet.createRow(index + 1);
                sheetRow.createCell(0).setCellValue(row.attendanceDate());
                sheetRow.createCell(1).setCellValue(row.employeeCode());
                sheetRow.createCell(2).setCellValue(row.employeeName());
                sheetRow.createCell(3).setCellValue(row.workplaceName());
                sheetRow.createCell(4).setCellValue(row.role());
                sheetRow.createCell(5).setCellValue(row.state().getLabel());
                sheetRow.createCell(6).setCellValue(row.checkInTime());
                sheetRow.createCell(7).setCellValue(row.checkOutTime());
                sheetRow.createCell(8).setCellValue(row.note());
            }

            for (int index = 0; index < summaryHeaders.length; index++) {
                summarySheet.autoSizeColumn(index);
                summarySheet.setColumnWidth(index, Math.max(summarySheet.getColumnWidth(index), 3600));
            }

            for (int index = 0; index < detailHeaders.length; index++) {
                detailSheet.autoSizeColumn(index);
                detailSheet.setColumnWidth(index, Math.max(detailSheet.getColumnWidth(index), 3600));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("월별 출근 엑셀을 생성할 수 없습니다.", exception);
        }
    }

    public EmployeePage getEmployees(String employeeCode, boolean showDeleted, Long workplaceId, int page, int pageSize) {
        return backendAdminEmployeeApiClient.getEmployees(employeeCode, showDeleted, workplaceId, page, pageSize);
    }

    public EmployeeForm getEmployeeFormForCreate() {
        EmployeeForm form = new EmployeeForm();
        form.setRole("EMPLOYEE");
        form.setPassword("");
        form.setWorkplaceId(null);
        return form;
    }

    public InviteEmployeeForm getInviteEmployeeFormForCreate() {
        InviteEmployeeForm form = new InviteEmployeeForm();
        form.setRole("EMPLOYEE");
        form.setWorkplaceId(null);
        return form;
    }

    public EmployeeForm getEmployeeFormForEdit(String employeeCode, Long employeeId) {
        return backendAdminEmployeeApiClient.getEmployeeForm(employeeCode, employeeId);
    }

    public byte[] createEmployeeUploadTemplate() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("employees");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor((short) 22);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"사번", "이름", "권한", "비밀번호", "사업장", "출근 기준 시간", "퇴근 기준 시간"};
            for (int index = 0; index < headers.length; index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(headers[index]);
                cell.setCellStyle(headerStyle);
            }

            Row sampleRow1 = sheet.createRow(1);
            sampleRow1.createCell(0).setCellValue("EMP004");
            sampleRow1.createCell(1).setCellValue("김서준");
            sampleRow1.createCell(2).setCellValue("EMPLOYEE");
            sampleRow1.createCell(3).setCellValue("password1234");
            sampleRow1.createCell(4).setCellValue("강남점");
            sampleRow1.createCell(5).setCellValue("09:00");
            sampleRow1.createCell(6).setCellValue("18:00");

            Row sampleRow2 = sheet.createRow(2);
            sampleRow2.createCell(0).setCellValue("EMP005");
            sampleRow2.createCell(1).setCellValue("박소연");
            sampleRow2.createCell(2).setCellValue("ADMIN");
            sampleRow2.createCell(3).setCellValue("securepass1");
            sampleRow2.createCell(4).setCellValue("");
            sampleRow2.createCell(5).setCellValue("10:00");
            sampleRow2.createCell(6).setCellValue("19:00");

            for (int index = 0; index < headers.length; index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.max(sheet.getColumnWidth(index), 4200));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("엑셀 샘플 파일을 생성할 수 없습니다.", exception);
        }
    }

    public CompanyLocationView getCompanyLocation(String employeeCode) {
        return backendAdminLocationApiClient.getLocationSettings(employeeCode).toCompanyLocationView();
    }

    public List<WorkplaceLocationView> getWorkplaces(String employeeCode) {
        return backendAdminLocationApiClient.getLocationSettings(employeeCode).workplaces();
    }

    public List<WorkplaceOption> getWorkplaceOptions(String employeeCode) {
        return getWorkplaces(employeeCode).stream()
                .map(workplace -> new WorkplaceOption(workplace.id(), workplace.name()))
                .toList();
    }

    public WorkplaceLocationForm getWorkplaceLocationForm(String employeeCode, Long workplaceId) {
        WorkplaceLocationView workplace = getWorkplaces(employeeCode).stream()
                .filter(item -> java.util.Objects.equals(item.id(), workplaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));
        WorkplaceLocationForm form = new WorkplaceLocationForm();
        form.setName(workplace.name());
        form.setLatitude(workplace.latitude());
        form.setLongitude(workplace.longitude());
        form.setAllowedRadiusMeters(workplace.allowedRadiusMeters());
        form.setNoticeMessage(workplace.noticeMessage());
        return form;
    }

    public WorkplaceLocationView getSelectedWorkplace(String employeeCode, Long workplaceId) {
        return getWorkplaces(employeeCode).stream()
                .filter(item -> java.util.Objects.equals(item.id(), workplaceId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("사업장을 찾을 수 없습니다."));
    }

    public boolean canAccessSqlConsole(String employeeCode) {
        AdminContext admin = getAdminContext(employeeCode);
        return admin.isAdminRole();
    }

    public List<SqlSnippet> getSqlConsoleSnippets(String employeeCode) {
        AdminContext admin = requireSqlConsoleAdmin(employeeCode);
        if (admin.workplaceScopedAdmin()) {
            return List.of(
                    new SqlSnippet(
                            "monthly-attendance",
                            "월별 출근 리스트",
                            "이번 달 기준 사업장 직원 출근 기록을 날짜 역순으로 조회합니다.",
                            """
                            select
                                sar.attendance_date,
                                se.employee_code,
                                se.name,
                                sar.check_in_time,
                                sar.check_out_time,
                                sar.late,
                                sar.status
                            from scoped_attendance_records sar
                            join scoped_employees se on se.id = sar.employee_id
                            where sar.attendance_date >= date_trunc('month', current_date)::date
                              and sar.attendance_date < (date_trunc('month', current_date) + interval '1 month')::date
                            order by sar.attendance_date desc, se.name asc
                            """
                    ),
                    new SqlSnippet(
                            "period-attendance",
                            "기간별 출근 리스트",
                            "기간 조건만 바꿔서 사업장 출근 기록을 조회할 수 있습니다.",
                            """
                            select
                                sar.attendance_date,
                                se.employee_code,
                                se.name,
                                sar.check_in_time,
                                sar.check_out_time,
                                sar.late,
                                sar.status
                            from scoped_attendance_records sar
                            join scoped_employees se on se.id = sar.employee_id
                            where sar.attendance_date between date '2026-03-01' and date '2026-03-31'
                            order by sar.attendance_date desc, se.name asc
                            """
                    ),
                    new SqlSnippet(
                            "employee-attendance",
                            "직원별 출근 리스트",
                            "사번 조건을 바꿔서 특정 직원의 출근 기록만 볼 수 있습니다.",
                            """
                            select
                                sar.attendance_date,
                                se.employee_code,
                                se.name,
                                sar.check_in_time,
                                sar.check_out_time,
                                sar.late,
                                sar.status
                            from scoped_attendance_records sar
                            join scoped_employees se on se.id = sar.employee_id
                            where se.employee_code = 'EMP001'
                            order by sar.attendance_date desc
                            """
                    )
            );
        }

        return List.of(
                new SqlSnippet(
                        "monthly-attendance",
                        "월별 출근 리스트",
                        "이번 달 전체 직원 출근 기록을 날짜 역순으로 조회합니다.",
                        """
                        select
                            ar.attendance_date,
                            e.employee_code,
                            e.name,
                            coalesce(w.name, '본사') as workplace_name,
                            ar.check_in_time,
                            ar.check_out_time,
                            ar.late,
                            ar.status
                        from attendance_records ar
                        join employees e on e.id = ar.employee_id
                        left join workplaces w on w.id = e.workplace_id
                        where ar.attendance_date >= date_trunc('month', current_date)::date
                          and ar.attendance_date < (date_trunc('month', current_date) + interval '1 month')::date
                        order by ar.attendance_date desc, e.name asc
                        """
                ),
                new SqlSnippet(
                        "period-attendance",
                        "기간별 출근 리스트",
                        "기간 조건만 바꿔서 출근 기록을 조회할 수 있습니다.",
                        """
                        select
                            ar.attendance_date,
                            e.employee_code,
                            e.name,
                            coalesce(w.name, '본사') as workplace_name,
                            ar.check_in_time,
                            ar.check_out_time,
                            ar.late,
                            ar.status
                        from attendance_records ar
                        join employees e on e.id = ar.employee_id
                        left join workplaces w on w.id = e.workplace_id
                        where ar.attendance_date between date '2026-03-01' and date '2026-03-31'
                        order by ar.attendance_date desc, e.name asc
                        """
                ),
                new SqlSnippet(
                        "employee-attendance",
                        "직원별 출근 리스트",
                        "사번 조건을 바꿔서 특정 직원의 출근 기록만 볼 수 있습니다.",
                        """
                        select
                            ar.attendance_date,
                            e.employee_code,
                            e.name,
                            coalesce(w.name, '본사') as workplace_name,
                            ar.check_in_time,
                            ar.check_out_time,
                            ar.late,
                            ar.status
                        from attendance_records ar
                        join employees e on e.id = ar.employee_id
                        left join workplaces w on w.id = e.workplace_id
                        where e.employee_code = 'EMP001'
                        order by ar.attendance_date desc
                        """
                )
        );
    }

    public SqlQueryResult executeReadOnlySql(String employeeCode, String queryText) {
        requireSqlConsoleAdmin(employeeCode);
        return backendAdminSqlApiClient.executeReadOnlySql(employeeCode, queryText);
    }

    public byte[] exportSqlQueryExcel(String employeeCode, String queryText) {
        requireSqlConsoleAdmin(employeeCode);
        return backendAdminSqlApiClient.exportSqlQueryExcel(employeeCode, queryText);
    }

    public CompanyLocationForm getCompanyLocationForm(String employeeCode) {
        ensureCompanyScopeAllowed(getAdminContext(employeeCode));
        CompanyLocationView location = getCompanyLocation(employeeCode);
        CompanyLocationForm form = new CompanyLocationForm();
        form.setCompanyName(location.companyName());
        form.setLatitude(location.latitude());
        form.setLongitude(location.longitude());
        form.setAllowedRadiusMeters(location.allowedRadiusMeters());
        form.setLateAfterTime(location.lateAfterTime());
        form.setNoticeMessage(location.noticeMessage());
        form.setMobileSkinKey(location.mobileSkinKey());
        form.setEnforceSingleDeviceLogin(location.enforceSingleDeviceLogin());
        return form;
    }

    public void updateCompanyLocation(String employeeCode, CompanyLocationForm form) {
        backendAdminLocationApiClient.updateCompanyLocation(employeeCode, form);
    }

    public void createWorkplace(String employeeCode, WorkplaceLocationForm form) {
        backendAdminLocationApiClient.createWorkplace(employeeCode, form);
    }

    public void updateWorkplace(String employeeCode, Long workplaceId, WorkplaceLocationForm form) {
        backendAdminLocationApiClient.updateWorkplace(employeeCode, workplaceId, form);
    }

    public void createEmployee(String adminEmployeeCode, EmployeeForm form) {
        backendAdminEmployeeApiClient.createEmployee(adminEmployeeCode, form);
    }

    public EmployeeInviteResult createEmployeeInvite(String adminEmployeeCode, InviteEmployeeForm form) {
        return backendAdminEmployeeApiClient.createEmployeeInvite(adminEmployeeCode, form);
    }

    public EmployeeInviteResult createInviteForExistingEmployee(String adminEmployeeCode, Long employeeId) {
        return backendAdminEmployeeApiClient.createInviteForExistingEmployee(adminEmployeeCode, employeeId);
    }

    public void updateEmployee(String adminEmployeeCode, Long employeeId, EmployeeForm form) {
        backendAdminEmployeeApiClient.updateEmployee(adminEmployeeCode, employeeId, form);
    }

    public void updateEmployeeUsage(String adminEmployeeCode, Long employeeId, boolean active) {
        backendAdminEmployeeApiClient.updateEmployeeUsage(adminEmployeeCode, employeeId, active);
    }

    public void resetEmployeeDevice(String adminEmployeeCode, Long employeeId) {
        backendAdminEmployeeApiClient.resetEmployeeDevice(adminEmployeeCode, employeeId);
    }

    public void deleteEmployee(String adminEmployeeCode, Long employeeId) {
        backendAdminEmployeeApiClient.deleteEmployee(adminEmployeeCode, employeeId);
    }

    public void restoreEmployee(String adminEmployeeCode, Long employeeId) {
        backendAdminEmployeeApiClient.restoreEmployee(adminEmployeeCode, employeeId);
    }

    public EmployeeUploadResult uploadEmployees(String adminEmployeeCode, MultipartFile file) {
        return backendAdminEmployeeApiClient.uploadEmployees(adminEmployeeCode, file);
    }

    public boolean isWorkplaceScopedAdmin(String employeeCode) {
        return getAdminContext(employeeCode).workplaceScopedAdmin();
    }

    public Long getAssignedWorkplaceId(String employeeCode) {
        AdminContext admin = getAdminContext(employeeCode);
        if (admin.assignedWorkplaceId() == null) {
            throw new IllegalStateException("사업장 관리자 계정에는 사업장 지정이 필요합니다.");
        }
        return admin.assignedWorkplaceId();
    }

    public Long resolveRequestedWorkplaceId(String employeeCode, Long requestedWorkplaceId) {
        AdminContext admin = getAdminContext(employeeCode);
        if (!admin.workplaceScopedAdmin()) {
            return requestedWorkplaceId;
        }
        return admin.assignedWorkplaceId();
    }

    public boolean canManageAdminRoles(String employeeCode) {
        return !isWorkplaceScopedAdmin(employeeCode);
    }
    public String normalizeDashboardFilter(String filter) {
        if (filter == null || filter.isBlank()) {
            return "ALL";
        }

        String normalized = filter.trim().toUpperCase();
        return switch (normalized) {
            case "ALL", "PRESENT", "LATE", "ABSENT", "CHECKED_OUT" -> normalized;
            default -> "ALL";
        };
    }

    private AdminContext getAdminContext(String employeeCode) {
        BackendAdminAuthApiClient.AdminUserDetailsResponse auth =
                backendAdminAuthApiClient.getAdminUserDetails(employeeCode);
        BackendAdminLocationApiClient.LocationSettingsResponse location =
                backendAdminLocationApiClient.getLocationSettings(employeeCode);
        return new AdminContext(
                auth.employeeCode(),
                auth.role(),
                auth.active(),
                location.workplaceScopedAdmin(),
                location.assignedWorkplaceId()
        );
    }

    private AdminContext requireSqlConsoleAdmin(String employeeCode) {
        AdminContext admin = getAdminContext(employeeCode);
        if (!admin.isAdminRole()) {
            throw new IllegalArgumentException("SQL 리포트는 관리자 권한으로만 사용할 수 있습니다.");
        }
        return admin;
    }

    private void ensureCompanyScopeAllowed(AdminContext admin) {
        if (admin.workplaceScopedAdmin()) {
            throw new IllegalArgumentException("사업장 관리자 권한으로는 회사 전체 설정을 변경할 수 없습니다.");
        }
    }

    private record AdminContext(
            String employeeCode,
            String role,
            boolean active,
            boolean workplaceScopedAdmin,
            Long assignedWorkplaceId
    ) {
        boolean isAdminRole() {
            return "ADMIN".equals(role) || "WORKPLACE_ADMIN".equals(role);
        }
    }
}
