package com.attendance.adminweb.service;

import com.attendance.adminweb.domain.entity.AttendanceRecord;
import com.attendance.adminweb.domain.entity.AttendanceStatus;
import com.attendance.adminweb.domain.entity.Company;
import com.attendance.adminweb.domain.entity.CompanySetting;
import com.attendance.adminweb.domain.entity.Employee;
import com.attendance.adminweb.domain.entity.EmployeeRole;
import com.attendance.adminweb.domain.entity.Workplace;
import com.attendance.adminweb.domain.repository.AttendanceRecordRepository;
import com.attendance.adminweb.domain.repository.CompanyRepository;
import com.attendance.adminweb.domain.repository.CompanySettingRepository;
import com.attendance.adminweb.domain.repository.EmployeeRepository;
import com.attendance.adminweb.domain.repository.WorkplaceRepository;
import com.attendance.adminweb.model.AttendanceRow;
import com.attendance.adminweb.model.AttendanceState;
import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.CompanyLocationView;
import com.attendance.adminweb.model.DashboardSummary;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeePage;
import com.attendance.adminweb.model.EmployeeRow;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeDetailRow;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeRow;
import com.attendance.adminweb.model.MonthlyAttendanceRecordRow;
import com.attendance.adminweb.model.MonthlyAttendanceSummary;
import com.attendance.adminweb.model.SqlQueryResult;
import com.attendance.adminweb.model.WorkplaceLocationForm;
import com.attendance.adminweb.model.WorkplaceLocationView;
import com.attendance.adminweb.model.WorkplaceOption;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import javax.sql.DataSource;

@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();
    private static final int SQL_PREVIEW_ROW_LIMIT = 200;
    private static final int SQL_EXPORT_ROW_LIMIT = 5000;
    private static final int EXCEL_CELL_TEXT_LIMIT = 32767;

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingRepository companySettingRepository;
    private final WorkplaceRepository workplaceRepository;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    public AdminService(EmployeeRepository employeeRepository,
                        AttendanceRecordRepository attendanceRecordRepository,
                        CompanyRepository companyRepository,
                        CompanySettingRepository companySettingRepository,
                        WorkplaceRepository workplaceRepository,
                        PasswordEncoder passwordEncoder,
                        DataSource dataSource) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.companyRepository = companyRepository;
        this.companySettingRepository = companySettingRepository;
        this.workplaceRepository = workplaceRepository;
        this.passwordEncoder = passwordEncoder;
        this.dataSource = dataSource;
    }

    public DashboardSummary getTodaySummary(String employeeCode, Long workplaceId) {
        List<Employee> employees = getCompanyEmployees(employeeCode, workplaceId);
        Map<Long, AttendanceRecord> recordsByEmployeeId = getTodayRecordsByEmployee(employees);

        int total = employees.size();
        int present = 0;
        int late = 0;
        int absent = 0;
        int checkedOut = 0;

        for (Employee employee : employees) {
            AttendanceRecord record = recordsByEmployeeId.get(employee.getId());
            if (record == null) {
                absent++;
                continue;
            }

            if (record.isLate()) {
                late++;
            } else if (record.getStatus() == AttendanceStatus.CHECKED_OUT) {
                checkedOut++;
            } else {
                present++;
            }
        }

        return new DashboardSummary(total, present, late, absent, checkedOut);
    }

    public List<AttendanceRow> getTodayAttendances(String employeeCode, String filter, Long workplaceId) {
        List<Employee> employees = getCompanyEmployees(employeeCode, workplaceId);
        Map<Long, AttendanceRecord> recordsByEmployeeId = getTodayRecordsByEmployee(employees);

        return employees.stream()
                .map(employee -> {
                    AttendanceRecord record = recordsByEmployeeId.get(employee.getId());
                    AttendanceState state = toState(record);
                    return new AttendanceRow(
                            employee.getEmployeeCode(),
                            employee.getName(),
                            getWorkplaceName(employee),
                            employee.getRole().name(),
                            state,
                            formatCheckIn(record),
                            formatCheckOut(record),
                            buildNote(record)
                    );
                })
                .filter(row -> matchesFilter(row, filter))
                .toList();
    }

    public MonthlyAttendanceSummary getMonthlyAttendanceSummary(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        List<Employee> employees = getCompanyEmployees(employeeCode, workplaceId);
        Set<Long> employeeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
        List<AttendanceRecord> records = getMonthlyRecords(employeeCode, yearMonth).stream()
                .filter(record -> employeeIds.contains(record.getEmployee().getId()))
                .toList();
        Set<Long> attendedEmployeeIds = records.stream()
                .map(record -> record.getEmployee().getId())
                .collect(Collectors.toSet());

        int lateCount = (int) records.stream()
                .filter(AttendanceRecord::isLate)
                .count();
        int checkedOutCount = (int) records.stream()
                .filter(record -> record.getStatus() == AttendanceStatus.CHECKED_OUT)
                .count();

        return new MonthlyAttendanceSummary(
                yearMonth.format(MONTH_FORMATTER),
                employees.size(),
                attendedEmployeeIds.size(),
                records.size(),
                lateCount,
                checkedOutCount
        );
    }

    public List<MonthlyAttendanceEmployeeRow> getMonthlyAttendanceEmployees(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        List<Employee> employees = getCompanyEmployees(employeeCode, workplaceId);
        Set<Long> employeeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
        Map<Long, List<AttendanceRecord>> recordsByEmployeeId = getMonthlyRecords(employeeCode, yearMonth).stream()
                .filter(record -> employeeIds.contains(record.getEmployee().getId()))
                .collect(Collectors.groupingBy(record -> record.getEmployee().getId()));

        return employees.stream()
                .map(employee -> {
                    List<AttendanceRecord> records = recordsByEmployeeId.getOrDefault(employee.getId(), List.of());
                    AttendanceRecord lastRecord = records.stream()
                            .max(Comparator.comparing(AttendanceRecord::getAttendanceDate)
                                    .thenComparing(AttendanceRecord::getCheckInTime))
                            .orElse(null);

                    int lateDays = (int) records.stream()
                            .filter(AttendanceRecord::isLate)
                            .count();
                    int checkedOutDays = (int) records.stream()
                            .filter(record -> record.getStatus() == AttendanceStatus.CHECKED_OUT)
                            .count();

                    return new MonthlyAttendanceEmployeeRow(
                            employee.getEmployeeCode(),
                            employee.getName(),
                            getWorkplaceName(employee),
                            employee.getRole().name(),
                            records.size(),
                            lateDays,
                            checkedOutDays,
                            lastRecord == null ? "-" : lastRecord.getAttendanceDate().format(DATE_FORMATTER),
                            lastRecord == null ? AttendanceState.ABSENT : toState(lastRecord)
                    );
                })
                .toList();
    }

    public List<MonthlyAttendanceRecordRow> getMonthlyAttendanceRecords(String employeeCode, YearMonth yearMonth, Long workplaceId) {
        Set<Long> employeeIds = getCompanyEmployees(employeeCode, workplaceId).stream()
                .map(Employee::getId)
                .collect(Collectors.toSet());

        return getMonthlyRecords(employeeCode, yearMonth).stream()
                .filter(record -> employeeIds.contains(record.getEmployee().getId()))
                .sorted(Comparator.comparing(AttendanceRecord::getAttendanceDate).reversed()
                        .thenComparing(AttendanceRecord::getCheckInTime, Comparator.reverseOrder()))
                .map(record -> new MonthlyAttendanceRecordRow(
                        record.getAttendanceDate().format(DATE_FORMATTER),
                        record.getEmployee().getEmployeeCode(),
                        record.getEmployee().getName(),
                        getWorkplaceName(record.getEmployee()),
                        record.getEmployee().getRole().name(),
                        toState(record),
                        formatCheckIn(record),
                        formatCheckOut(record),
                        buildNote(record)
                ))
                .toList();
    }

    public MonthlyAttendanceEmployeeDetailRow getMonthlyAttendanceEmployeeDetail(String employeeCode,
                                                                                 YearMonth yearMonth,
                                                                                 String selectedEmployeeCode,
                                                                                 Long workplaceId) {
        if (selectedEmployeeCode == null || selectedEmployeeCode.isBlank()) {
            return null;
        }

        List<Employee> employees = getCompanyEmployees(employeeCode, workplaceId);
        Set<Long> employeeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
        Map<Long, List<AttendanceRecord>> recordsByEmployeeId = getMonthlyRecords(employeeCode, yearMonth).stream()
                .filter(record -> employeeIds.contains(record.getEmployee().getId()))
                .collect(Collectors.groupingBy(record -> record.getEmployee().getId()));

        return employees.stream()
                .filter(employee -> employee.getEmployeeCode().equalsIgnoreCase(selectedEmployeeCode.trim()))
                .map(employee -> {
                    List<AttendanceRecord> employeeRecords = recordsByEmployeeId.getOrDefault(employee.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(AttendanceRecord::getAttendanceDate).reversed()
                                    .thenComparing(AttendanceRecord::getCheckInTime, Comparator.reverseOrder()))
                            .toList();

                    int lateDays = (int) employeeRecords.stream()
                            .filter(AttendanceRecord::isLate)
                            .count();
                    int checkedOutDays = (int) employeeRecords.stream()
                            .filter(record -> record.getStatus() == AttendanceStatus.CHECKED_OUT)
                            .count();

                    List<MonthlyAttendanceRecordRow> records = employeeRecords.stream()
                            .map(record -> new MonthlyAttendanceRecordRow(
                                    record.getAttendanceDate().format(DATE_FORMATTER),
                                    record.getEmployee().getEmployeeCode(),
                                    record.getEmployee().getName(),
                                    getWorkplaceName(record.getEmployee()),
                                    record.getEmployee().getRole().name(),
                                    toState(record),
                                    formatCheckIn(record),
                                    formatCheckOut(record),
                                    buildNote(record)
                            ))
                            .toList();

                    return new MonthlyAttendanceEmployeeDetailRow(
                            employee.getEmployeeCode(),
                            employee.getName(),
                            getWorkplaceName(employee),
                            employee.getRole().name(),
                            employeeRecords.size(),
                            lateDays,
                            checkedOutDays,
                            records
                    );
                })
                .findFirst()
                .orElse(null);
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
        List<Employee> employees = getEmployeeList(employeeCode, showDeleted, workplaceId);
        Map<Long, AttendanceRecord> recordsByEmployeeId = getTodayRecordsByEmployee(employees);

        List<EmployeeRow> employeeRows = employees.stream()
                .map(employee -> {
                    AttendanceRecord record = recordsByEmployeeId.get(employee.getId());
                    return new EmployeeRow(
                            employee.getId(),
                            employee.getEmployeeCode(),
                            employee.getName(),
                            employee.getWorkplace() == null ? "본사" : employee.getWorkplace().getName(),
                            employee.getRole().name(),
                            formatScheduleTime(employee.getWorkStartTime()),
                            formatScheduleTime(employee.getWorkEndTime()),
                            toState(record),
                            formatCheckIn(record),
                            formatCheckOut(record),
                            employee.getRegisteredDeviceId() != null && !employee.getRegisteredDeviceId().isBlank(),
                            employee.isActive(),
                            employee.isDeleted()
                    );
                })
                .toList();

        int normalizedPageSize = Math.max(pageSize, 1);
        int totalCount = employeeRows.size();
        int totalPages = totalCount == 0 ? 1 : (int) Math.ceil((double) totalCount / normalizedPageSize);
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * normalizedPageSize, totalCount);
        int toIndex = Math.min(fromIndex + normalizedPageSize, totalCount);

        return new EmployeePage(
                employeeRows.subList(fromIndex, toIndex),
                currentPage,
                totalPages,
                totalCount,
                normalizedPageSize,
                currentPage > 1,
                currentPage < totalPages
        );
    }

    public EmployeeForm getEmployeeFormForCreate() {
        EmployeeForm form = new EmployeeForm();
        form.setRole(EmployeeRole.EMPLOYEE.name());
        form.setPassword("");
        form.setWorkplaceId(null);
        return form;
    }

    public EmployeeForm getEmployeeFormForEdit(String employeeCode, Long employeeId) {
        Employee employee = getEditableEmployee(employeeCode, employeeId);
        if (employee.isDeleted()) {
            throw new IllegalArgumentException("삭제된 직원은 수정할 수 없습니다. 먼저 복구해 주세요.");
        }
        return EmployeeForm.from(employee);
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
        Employee admin = getEmployeeByCode(employeeCode);
        Company company = admin.getCompany();
        CompanySetting setting = companySettingRepository.findByCompany(company)
                .orElseThrow(() -> new EntityNotFoundException("회사 설정을 찾을 수 없습니다."));

        return new CompanyLocationView(
                company.getName(),
                company.getLatitude(),
                company.getLongitude(),
                setting.getAllowedRadiusMeters(),
                setting.getLateAfterTime().format(TIME_FORMATTER),
                setting.getNoticeMessage(),
                setting.isEnforceSingleDeviceLogin()
        );
    }

    public List<WorkplaceLocationView> getWorkplaces(String employeeCode) {
        Employee admin = getEmployeeByCode(employeeCode);
        return getAccessibleWorkplaces(admin).stream()
                .map(workplace -> new WorkplaceLocationView(
                        workplace.getId(),
                        workplace.getName(),
                        workplace.getLatitude(),
                        workplace.getLongitude(),
                        workplace.getAllowedRadiusMeters(),
                        workplace.getNoticeMessage()
                ))
                .toList();
    }

    public List<WorkplaceOption> getWorkplaceOptions(String employeeCode) {
        Employee admin = getEmployeeByCode(employeeCode);
        return getAccessibleWorkplaces(admin).stream()
                .map(workplace -> new WorkplaceOption(workplace.getId(), workplace.getName()))
                .toList();
    }

    public WorkplaceLocationForm getWorkplaceLocationForm(String employeeCode, Long workplaceId) {
        Workplace workplace = getWorkplace(employeeCode, workplaceId);
        WorkplaceLocationForm form = new WorkplaceLocationForm();
        form.setName(workplace.getName());
        form.setLatitude(workplace.getLatitude());
        form.setLongitude(workplace.getLongitude());
        form.setAllowedRadiusMeters(workplace.getAllowedRadiusMeters());
        form.setNoticeMessage(workplace.getNoticeMessage());
        return form;
    }

    public boolean canAccessSqlConsole(String employeeCode) {
        Employee employee = getEmployeeByCode(employeeCode);
        return employee.getRole() == EmployeeRole.ADMIN || employee.getRole() == EmployeeRole.WORKPLACE_ADMIN;
    }

    public SqlQueryResult executeReadOnlySql(String employeeCode, String queryText) {
        Employee admin = requireSqlConsoleAdmin(employeeCode);
        return runSqlQuery(buildExecutableSql(admin, validateSqlQuery(admin, queryText)), admin, SQL_PREVIEW_ROW_LIMIT);
    }

    public byte[] exportSqlQueryExcel(String employeeCode, String queryText) {
        Employee admin = requireSqlConsoleAdmin(employeeCode);
        SqlQueryResult queryResult = runSqlQuery(buildExecutableSql(admin, validateSqlQuery(admin, queryText)), admin, SQL_EXPORT_ROW_LIMIT);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setFillForegroundColor((short) 22);
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet sheet = workbook.createSheet("SQL 결과");
            Row headerRow = sheet.createRow(0);
            for (int index = 0; index < queryResult.columns().size(); index++) {
                Cell cell = headerRow.createCell(index);
                cell.setCellValue(queryResult.columns().get(index));
                cell.setCellStyle(headerStyle);
            }

            for (int rowIndex = 0; rowIndex < queryResult.rows().size(); rowIndex++) {
                List<String> rowValues = queryResult.rows().get(rowIndex);
                Row row = sheet.createRow(rowIndex + 1);
                for (int columnIndex = 0; columnIndex < rowValues.size(); columnIndex++) {
                    row.createCell(columnIndex).setCellValue(trimExcelCellValue(rowValues.get(columnIndex)));
                }
            }

            for (int index = 0; index < queryResult.columns().size(); index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.max(sheet.getColumnWidth(index), 3600));
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("SQL 결과 엑셀을 생성할 수 없습니다.", exception);
        }
    }

    public CompanyLocationForm getCompanyLocationForm(String employeeCode) {
        ensureCompanyScopeAllowed(getEmployeeByCode(employeeCode));
        CompanyLocationView location = getCompanyLocation(employeeCode);
        CompanyLocationForm form = new CompanyLocationForm();
        form.setCompanyName(location.companyName());
        form.setLatitude(location.latitude());
        form.setLongitude(location.longitude());
        form.setAllowedRadiusMeters(location.allowedRadiusMeters());
        form.setLateAfterTime(location.lateAfterTime());
        form.setNoticeMessage(location.noticeMessage());
        form.setEnforceSingleDeviceLogin(location.enforceSingleDeviceLogin());
        return form;
    }

    @Transactional
    public void updateCompanyLocation(String employeeCode, CompanyLocationForm form) {
        Employee admin = getEmployeeByCode(employeeCode);
        ensureCompanyScopeAllowed(admin);
        Company company = companyRepository.findById(admin.getCompany().getId())
                .orElseThrow(() -> new EntityNotFoundException("회사를 찾을 수 없습니다."));
        CompanySetting setting = companySettingRepository.findByCompany(company)
                .orElseThrow(() -> new EntityNotFoundException("회사 설정을 찾을 수 없습니다."));

        company.updateName(form.getCompanyName().trim());
        company.updateLocation(form.getLatitude(), form.getLongitude());
        setting.updateAllowedRadiusMeters(form.getAllowedRadiusMeters());
        setting.updateNoticeMessage(normalizeNoticeMessage(form.getNoticeMessage()));
        setting.updateEnforceSingleDeviceLogin(form.isEnforceSingleDeviceLogin());
    }

    @Transactional
    public void createWorkplace(String employeeCode, WorkplaceLocationForm form) {
        Employee admin = getEmployeeByCode(employeeCode);
        ensureCompanyScopeAllowed(admin);
        workplaceRepository.save(new Workplace(
                admin.getCompany(),
                form.getName().trim(),
                form.getLatitude(),
                form.getLongitude(),
                form.getAllowedRadiusMeters(),
                normalizeNoticeMessage(form.getNoticeMessage())
        ));
    }

    @Transactional
    public void updateWorkplace(String employeeCode, Long workplaceId, WorkplaceLocationForm form) {
        Workplace workplace = getWorkplace(employeeCode, workplaceId);
        workplace.update(
                form.getName().trim(),
                form.getLatitude(),
                form.getLongitude(),
                form.getAllowedRadiusMeters(),
                normalizeNoticeMessage(form.getNoticeMessage())
        );
    }

    @Transactional
    public void createEmployee(String adminEmployeeCode, EmployeeForm form) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        validateRoleAssignment(admin, form.getEmployeeRole(), form.getWorkplaceId());
        validateDuplicateEmployeeCode(form.getEmployeeCode(), null);
        Workplace workplace = resolveManagedWorkplace(admin, form.getWorkplaceId());

        Employee employee = new Employee(
                form.getEmployeeCode().trim(),
                form.getName().trim(),
                passwordEncoder.encode(form.getPassword()),
                form.getEmployeeRole(),
                admin.getCompany(),
                workplace,
                parseOptionalTime(form.getWorkStartTime(), "출근 기준 시간"),
                parseOptionalTime(form.getWorkEndTime(), "퇴근 기준 시간")
        );
        applyPasswordChangePolicy(employee, form.getEmployeeRole());
        employeeRepository.save(employee);
    }

    @Transactional
    public void updateEmployee(String adminEmployeeCode, Long employeeId, EmployeeForm form) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);
        if (employee.isDeleted()) {
            throw new IllegalArgumentException("삭제된 직원은 수정할 수 없습니다. 먼저 복구해 주세요.");
        }
        validateRoleAssignment(admin, form.getEmployeeRole(), form.getWorkplaceId());
        validateDuplicateEmployeeCode(form.getEmployeeCode(), employeeId);
        Workplace workplace = resolveManagedWorkplace(admin, form.getWorkplaceId());

        employee.updateProfile(
                form.getEmployeeCode().trim(),
                form.getName().trim(),
                form.getEmployeeRole(),
                workplace,
                parseOptionalTime(form.getWorkStartTime(), "출근 기준 시간"),
                parseOptionalTime(form.getWorkEndTime(), "퇴근 기준 시간")
        );

        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            employee.updatePassword(passwordEncoder.encode(form.getPassword()));
            applyPasswordChangePolicy(employee, form.getEmployeeRole());
        }
    }

    @Transactional
    public void updateEmployeeUsage(String adminEmployeeCode, Long employeeId, boolean active) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);

        if (employee.isDeleted()) {
            throw new IllegalArgumentException("삭제된 직원은 사용 여부를 변경할 수 없습니다. 먼저 복구해 주세요.");
        }

        if (admin.getId().equals(employee.getId())) {
            throw new IllegalArgumentException("현재 로그인한 관리자 계정은 사용 중지할 수 없습니다.");
        }

        if (employee.isActive() == active) {
            throw new IllegalArgumentException(active ? "이미 사용 중인 직원입니다." : "이미 사용 중지된 직원입니다.");
        }

        employee.updateActive(active);
    }

    @Transactional
    public void resetEmployeeDevice(String adminEmployeeCode, Long employeeId) {
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);
        if (employee.isDeleted()) {
            throw new IllegalArgumentException("삭제된 직원의 단말은 초기화할 수 없습니다.");
        }
        employee.resetRegisteredDevice();
    }

    @Transactional
    public void deleteEmployee(String adminEmployeeCode, Long employeeId) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);

        if (admin.getId().equals(employee.getId())) {
            throw new IllegalArgumentException("현재 로그인한 관리자 계정은 삭제할 수 없습니다.");
        }

        if (employee.isDeleted()) {
            throw new IllegalArgumentException("이미 삭제된 직원입니다.");
        }

        employee.softDelete();
    }

    @Transactional
    public void restoreEmployee(String adminEmployeeCode, Long employeeId) {
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);

        if (!employee.isDeleted()) {
            throw new IllegalArgumentException("삭제된 직원만 복구할 수 있습니다.");
        }

        employee.restore();
    }

    @Transactional
    public EmployeeUploadResult uploadEmployees(String adminEmployeeCode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 파일을 선택해 주세요.");
        }

        Employee admin = getEmployeeByCode(adminEmployeeCode);
        Set<String> existingCodes = employeeRepository.findAllByCompanyIdOrderByNameAsc(admin.getCompany().getId()).stream()
                .map(Employee::getEmployeeCode)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, Workplace> workplacesByName = workplaceRepository.findAllByCompanyIdOrderByNameAsc(admin.getCompany().getId()).stream()
                .collect(Collectors.toMap(
                        workplace -> workplace.getName().trim().toLowerCase(),
                        Function.identity(),
                        (first, second) -> first
                ));
        List<String> failureMessages = new ArrayList<>();
        int successCount = 0;

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("엑셀 시트가 비어 있습니다.");
            }

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isEmptyRow(row)) {
                    continue;
                }

                String employeeCode = readCell(row, 0);
                String name = readCell(row, 1);
                String roleValue = readCell(row, 2).toUpperCase();
                String password = readCell(row, 3);
                String workplaceName = readCell(row, 4);
                String workStartTime = readCell(row, 5);
                String workEndTime = readCell(row, 6);

                try {
                    validateUploadRow(rowIndex + 1, employeeCode, name, roleValue, password, workplaceName, workStartTime, workEndTime, existingCodes);
                    validateUploadRole(admin, rowIndex + 1, roleValue);
                    Workplace workplace = resolveUploadWorkplace(admin, rowIndex + 1, workplaceName, workplacesByName);
                    Employee employee = new Employee(
                            employeeCode,
                            name,
                            passwordEncoder.encode(password),
                            EmployeeRole.valueOf(roleValue),
                            admin.getCompany(),
                            workplace,
                            parseOptionalTime(workStartTime, rowIndex + 1 + "행 출근 기준 시간"),
                            parseOptionalTime(workEndTime, rowIndex + 1 + "행 퇴근 기준 시간")
                    );
                    applyPasswordChangePolicy(employee, employee.getRole());
                    employeeRepository.saveAndFlush(employee);
                    existingCodes.add(employeeCode);
                    successCount++;
                } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
                    failureMessages.add(exception.getMessage());
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("엑셀 파일을 읽는 중 오류가 발생했습니다.");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("지원하지 않는 엑셀 파일 형식입니다. .xlsx 파일을 사용해 주세요.");
        }

        return new EmployeeUploadResult(successCount, failureMessages);
    }

    private List<Employee> getCompanyEmployees(String employeeCode) {
        Employee admin = getEmployeeByCode(employeeCode);
        return employeeRepository.findAllByCompanyIdAndActiveTrueAndDeletedFalseOrderByNameAsc(admin.getCompany().getId());
    }

    private List<Employee> getCompanyEmployees(String employeeCode, Long workplaceId) {
        Employee admin = getEmployeeByCode(employeeCode);
        return filterByWorkplace(getCompanyEmployees(employeeCode), resolveRequestedWorkplaceId(admin, workplaceId));
    }

    private List<Employee> getEmployeeList(String employeeCode, boolean showDeleted) {
        Employee admin = getEmployeeByCode(employeeCode);
        if (showDeleted) {
            return employeeRepository.findAllByCompanyIdAndDeletedTrueOrderByNameAsc(admin.getCompany().getId());
        }
        return employeeRepository.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(admin.getCompany().getId());
    }

    private List<Employee> getEmployeeList(String employeeCode, boolean showDeleted, Long workplaceId) {
        Employee admin = getEmployeeByCode(employeeCode);
        return filterByWorkplace(getEmployeeList(employeeCode, showDeleted), resolveRequestedWorkplaceId(admin, workplaceId));
    }

    private List<Employee> filterByWorkplace(List<Employee> employees, Long workplaceId) {
        if (workplaceId == null) {
            return employees;
        }
        if (workplaceId == 0L) {
            return employees.stream()
                    .filter(employee -> employee.getWorkplace() == null)
                    .toList();
        }
        return employees.stream()
                .filter(employee -> employee.getWorkplace() != null && workplaceId.equals(employee.getWorkplace().getId()))
                .toList();
    }

    private Employee getEmployeeByCode(String employeeCode) {
        return employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다."));
    }

    private Employee getEditableEmployee(String adminEmployeeCode, Long employeeId) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EntityNotFoundException("직원을 찾을 수 없습니다."));

        if (!employee.getCompany().getId().equals(admin.getCompany().getId())) {
            throw new IllegalArgumentException("같은 회사 소속 직원만 관리할 수 있습니다.");
        }

        if (isWorkplaceScopedAdmin(admin)) {
            Long assignedWorkplaceId = getAssignedWorkplaceId(admin);
            if (employee.getWorkplace() == null || !assignedWorkplaceId.equals(employee.getWorkplace().getId())) {
                throw new IllegalArgumentException("사업장 관리자 권한으로는 본인 사업장 직원만 관리할 수 있습니다.");
            }
        }

        return employee;
    }

    private Workplace getWorkplace(String employeeCode, Long workplaceId) {
        Employee admin = getEmployeeByCode(employeeCode);
        if (isWorkplaceScopedAdmin(admin) && !getAssignedWorkplaceId(admin).equals(workplaceId)) {
            throw new IllegalArgumentException("본인 사업장만 관리할 수 있습니다.");
        }
        return workplaceRepository.findByIdAndCompanyId(workplaceId, admin.getCompany().getId())
                .orElseThrow(() -> new EntityNotFoundException("사업장을 찾을 수 없습니다."));
    }

    private Workplace resolveWorkplace(Employee employee, Long workplaceId) {
        if (workplaceId == null) {
            return null;
        }

        return workplaceRepository.findByIdAndCompanyId(workplaceId, employee.getCompany().getId())
                .orElseThrow(() -> new IllegalArgumentException("같은 회사 소속 사업장만 선택할 수 있습니다."));
    }

    private Workplace resolveManagedWorkplace(Employee admin, Long workplaceId) {
        if (isWorkplaceScopedAdmin(admin)) {
            return resolveWorkplace(admin, getAssignedWorkplaceId(admin));
        }
        return resolveWorkplace(admin, workplaceId);
    }

    private String getWorkplaceName(Employee employee) {
        return employee.getWorkplace() == null ? "본사" : employee.getWorkplace().getName();
    }

    public boolean isWorkplaceScopedAdmin(String employeeCode) {
        return isWorkplaceScopedAdmin(getEmployeeByCode(employeeCode));
    }

    public Long resolveRequestedWorkplaceId(String employeeCode, Long requestedWorkplaceId) {
        return resolveRequestedWorkplaceId(getEmployeeByCode(employeeCode), requestedWorkplaceId);
    }

    public boolean canManageAdminRoles(String employeeCode) {
        return !isWorkplaceScopedAdmin(employeeCode);
    }

    private void validateDuplicateEmployeeCode(String employeeCode, Long employeeId) {
        String normalizedCode = employeeCode.trim();
        boolean duplicated = employeeId == null
                ? employeeRepository.existsByEmployeeCode(normalizedCode)
                : employeeRepository.existsByEmployeeCodeAndIdNot(normalizedCode, employeeId);

        if (duplicated) {
            throw new IllegalArgumentException("이미 사용 중인 사번입니다.");
        }
    }

    private void validateUploadRow(int rowNumber,
                                   String employeeCode,
                                   String name,
                                   String roleValue,
                                   String password,
                                   String workplaceName,
                                   String workStartTime,
                                   String workEndTime,
                                   Set<String> existingCodes) {
        if (employeeCode.isBlank()) {
            throw new IllegalArgumentException(rowNumber + "행: 사번을 입력해 주세요.");
        }
        if (employeeCode.length() > 50) {
            throw new IllegalArgumentException(rowNumber + "행: 사번은 50자 이하여야 합니다.");
        }
        if (existingCodes.contains(employeeCode) || employeeRepository.existsByEmployeeCode(employeeCode)) {
            throw new IllegalArgumentException(rowNumber + "행: 이미 사용 중인 사번입니다. (" + employeeCode + ")");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException(rowNumber + "행: 이름을 입력해 주세요.");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException(rowNumber + "행: 이름은 100자 이하여야 합니다.");
        }
        if (roleValue.isBlank()) {
            throw new IllegalArgumentException(rowNumber + "행: 권한을 입력해 주세요.");
        }
        if (!roleValue.equals("ADMIN") && !roleValue.equals("WORKPLACE_ADMIN") && !roleValue.equals("EMPLOYEE")) {
            throw new IllegalArgumentException(rowNumber + "행: 권한은 ADMIN, WORKPLACE_ADMIN 또는 EMPLOYEE만 사용할 수 있습니다.");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException(rowNumber + "행: 비밀번호를 입력해 주세요.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException(rowNumber + "행: 비밀번호는 8자 이상이어야 합니다.");
        }
        if (workplaceName.length() > 100) {
            throw new IllegalArgumentException(rowNumber + "행: 사업장명은 100자 이하여야 합니다.");
        }
        parseOptionalTime(workStartTime, rowNumber + "행 출근 기준 시간");
        parseOptionalTime(workEndTime, rowNumber + "행 퇴근 기준 시간");
    }

    private Workplace resolveUploadWorkplace(Employee admin,
                                             int rowNumber,
                                             String workplaceName,
                                             Map<String, Workplace> workplacesByName) {
        if (isWorkplaceScopedAdmin(admin)) {
            if (workplaceName != null && !workplaceName.isBlank()
                    && !admin.getWorkplace().getName().equals(workplaceName.trim())) {
                throw new IllegalArgumentException(rowNumber + "행: 사업장 관리자 권한으로는 본인 사업장 직원만 등록할 수 있습니다.");
            }
            return admin.getWorkplace();
        }
        if (workplaceName == null || workplaceName.isBlank()) {
            return null;
        }

        Workplace workplace = workplacesByName.get(workplaceName.trim().toLowerCase());
        if (workplace == null) {
            throw new IllegalArgumentException(rowNumber + "행: 등록되지 않은 사업장입니다. (" + workplaceName + ")");
        }
        return workplace;
    }

    private Long resolveRequestedWorkplaceId(Employee admin, Long requestedWorkplaceId) {
        if (!isWorkplaceScopedAdmin(admin)) {
            return requestedWorkplaceId;
        }
        return getAssignedWorkplaceId(admin);
    }

    private boolean isWorkplaceScopedAdmin(Employee employee) {
        return employee.getRole() == EmployeeRole.WORKPLACE_ADMIN;
    }

    private Employee requireSqlConsoleAdmin(String employeeCode) {
        Employee admin = getEmployeeByCode(employeeCode);
        if (admin.getRole() != EmployeeRole.ADMIN && admin.getRole() != EmployeeRole.WORKPLACE_ADMIN) {
            throw new IllegalArgumentException("SQL 리포트는 관리자 권한으로만 사용할 수 있습니다.");
        }
        return admin;
    }

    private Long getAssignedWorkplaceId(Employee employee) {
        if (employee.getWorkplace() == null) {
            throw new IllegalStateException("사업장 관리자 계정에는 사업장 지정이 필요합니다.");
        }
        return employee.getWorkplace().getId();
    }

    private void ensureCompanyScopeAllowed(Employee admin) {
        if (isWorkplaceScopedAdmin(admin)) {
            throw new IllegalArgumentException("사업장 관리자 권한으로는 회사 전체 설정을 변경할 수 없습니다.");
        }
    }

    private void validateRoleAssignment(Employee admin, EmployeeRole targetRole, Long workplaceId) {
        if (targetRole == EmployeeRole.WORKPLACE_ADMIN && workplaceId == null) {
            throw new IllegalArgumentException("사업장 관리자 계정은 사업장을 지정해야 합니다.");
        }
        if (!isWorkplaceScopedAdmin(admin)) {
            return;
        }
        if (targetRole != EmployeeRole.EMPLOYEE) {
            throw new IllegalArgumentException("사업장 관리자 권한으로는 일반 직원 계정만 등록하거나 수정할 수 있습니다.");
        }
        if (workplaceId != null && !getAssignedWorkplaceId(admin).equals(workplaceId)) {
            throw new IllegalArgumentException("사업장 관리자 권한으로는 본인 사업장 직원만 등록하거나 수정할 수 있습니다.");
        }
    }

    private void validateUploadRole(Employee admin, int rowNumber, String roleValue) {
        if (isWorkplaceScopedAdmin(admin) && !roleValue.equals("EMPLOYEE")) {
            throw new IllegalArgumentException(rowNumber + "행: 사업장 관리자 권한으로는 일반 직원만 일괄 등록할 수 있습니다.");
        }
    }

    private List<Workplace> getAccessibleWorkplaces(Employee admin) {
        if (isWorkplaceScopedAdmin(admin)) {
            return List.of(getWorkplace(admin.getEmployeeCode(), getAssignedWorkplaceId(admin)));
        }
        return workplaceRepository.findAllByCompanyIdOrderByNameAsc(admin.getCompany().getId());
    }

    private String formatRegisteredDeviceName(Employee employee) {
        if (employee.getRegisteredDeviceId() == null || employee.getRegisteredDeviceId().isBlank()) {
            return "-";
        }
        if (employee.getRegisteredDeviceName() == null || employee.getRegisteredDeviceName().isBlank()) {
            return "등록된 단말";
        }
        return employee.getRegisteredDeviceName();
    }

    private String formatDeviceRegisteredAt(LocalDateTime registeredAt) {
        if (registeredAt == null) {
            return "-";
        }
        return registeredAt.format(DATE_TIME_FORMATTER);
    }

    private boolean isEmptyRow(Row row) {
        if (row == null) {
            return true;
        }
        for (int index = 0; index < 7; index++) {
            if (!readCell(row, index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private void applyPasswordChangePolicy(Employee employee, EmployeeRole role) {
        if (role == EmployeeRole.EMPLOYEE) {
            employee.markPasswordChangeRequired();
            return;
        }
        employee.markPasswordChanged();
    }

    private String normalizeNoticeMessage(String noticeMessage) {
        if (noticeMessage == null) {
            return null;
        }
        String normalized = noticeMessage.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String readCell(Row row, int cellIndex) {
        Cell cell = row == null ? null : row.getCell(cellIndex);
        return cell == null ? "" : DATA_FORMATTER.formatCellValue(cell).trim();
    }

    private String validateSqlQuery(Employee admin, String queryText) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("실행할 SQL을 입력해 주세요.");
        }

        String normalized = queryText.trim();
        String lowerCaseQuery = normalized.toLowerCase();

        if (normalized.length() > 20000) {
            throw new IllegalArgumentException("SQL은 20,000자 이하로 입력해 주세요.");
        }
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("세미콜론 없이 단일 조회 쿼리만 실행할 수 있습니다.");
        }
        if (lowerCaseQuery.contains("--") || lowerCaseQuery.contains("/*") || lowerCaseQuery.contains("*/")) {
            throw new IllegalArgumentException("주석이 포함된 SQL은 실행할 수 없습니다.");
        }
        if (!(lowerCaseQuery.startsWith("select") || lowerCaseQuery.startsWith("with"))) {
            throw new IllegalArgumentException("SELECT 또는 WITH로 시작하는 조회 쿼리만 실행할 수 있습니다.");
        }

        String[] blockedKeywords = {
                "insert", "update", "delete", "merge", "drop", "alter", "truncate",
                "create", "grant", "revoke", "comment", "call", "execute", "exec",
                "vacuum", "analyze", "refresh", "copy", "set"
        };

        for (String blockedKeyword : blockedKeywords) {
            if (lowerCaseQuery.matches("(?s).*\\b" + blockedKeyword + "\\b.*")) {
                throw new IllegalArgumentException("조회 전용 SQL만 실행할 수 있습니다. 금지된 키워드: " + blockedKeyword.toUpperCase());
            }
        }

        if (isWorkplaceScopedAdmin(admin)) {
            if (!lowerCaseQuery.matches("(?s).*\\bscoped_(employees|attendance_records|workplace)\\b.*")) {
                throw new IllegalArgumentException("사업장 관리자는 scoped_employees, scoped_attendance_records, scoped_workplace 중 하나를 사용해 조회해 주세요.");
            }

            String[] blockedTableNames = {"employees", "attendance_records", "workplaces", "companies", "company_settings"};
            for (String blockedTableName : blockedTableNames) {
                if (lowerCaseQuery.matches("(?s).*\\b" + blockedTableName + "\\b.*")
                        && !lowerCaseQuery.matches("(?s).*\\bscoped_" + blockedTableName + "\\b.*")) {
                    throw new IllegalArgumentException("사업장 관리자는 원본 테이블 대신 scoped_* 뷰만 조회할 수 있습니다.");
                }
            }
        }

        return normalized;
    }

    private String buildExecutableSql(Employee admin, String queryText) {
        if (!isWorkplaceScopedAdmin(admin)) {
            return queryText;
        }

        return """
                with scoped_workplace as (
                    select *
                    from workplaces
                    where id = ?
                ),
                scoped_employees as (
                    select *
                    from employees
                    where workplace_id = ?
                      and deleted = false
                ),
                scoped_attendance_records as (
                    select ar.*
                    from attendance_records ar
                    join scoped_employees se on se.id = ar.employee_id
                ),
                user_query as (
                %s
                )
                select *
                from user_query
                """.formatted(queryText);
    }

    private SqlQueryResult runSqlQuery(String queryText, Employee admin, int rowLimit) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(queryText)) {
                if (isWorkplaceScopedAdmin(admin)) {
                    Long workplaceId = getAssignedWorkplaceId(admin);
                    statement.setLong(1, workplaceId);
                    statement.setLong(2, workplaceId);
                }
                statement.setMaxRows(rowLimit + 1);
                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    for (int index = 1; index <= columnCount; index++) {
                        columns.add(metaData.getColumnLabel(index));
                    }

                    List<List<String>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (resultSet.next()) {
                        if (rows.size() == rowLimit) {
                            truncated = true;
                            break;
                        }

                        List<String> row = new ArrayList<>(columnCount);
                        for (int index = 1; index <= columnCount; index++) {
                            Object value = resultSet.getObject(index);
                            row.add(value == null ? "" : String.valueOf(value));
                        }
                        rows.add(row);
                    }

                    return new SqlQueryResult(columns, rows, rowLimit, truncated);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalArgumentException("SQL 실행에 실패했습니다. 구문과 테이블/컬럼명을 확인해 주세요.", exception);
        }
    }

    private String trimExcelCellValue(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= EXCEL_CELL_TEXT_LIMIT
                ? value
                : value.substring(0, EXCEL_CELL_TEXT_LIMIT);
    }

    private Map<Long, AttendanceRecord> getTodayRecordsByEmployee(List<Employee> employees) {
        if (employees.isEmpty()) {
            return Map.of();
        }

        Long companyId = employees.get(0).getCompany().getId();
        return attendanceRecordRepository.findAllByEmployeeCompanyIdAndAttendanceDate(companyId, LocalDate.now())
                .stream()
                .collect(Collectors.toMap(record -> record.getEmployee().getId(), Function.identity()));
    }

    private List<AttendanceRecord> getMonthlyRecords(String employeeCode, YearMonth yearMonth) {
        Employee admin = getEmployeeByCode(employeeCode);
        return attendanceRecordRepository.findAllByEmployeeCompanyIdAndAttendanceDateBetween(
                admin.getCompany().getId(),
                yearMonth.atDay(1),
                yearMonth.atEndOfMonth()
        ).stream()
                .filter(record -> !record.getEmployee().isDeleted())
                .toList();
    }

    private AttendanceState toState(AttendanceRecord record) {
        if (record == null) {
            return AttendanceState.ABSENT;
        }
        if (record.isLate()) {
            return AttendanceState.LATE;
        }
        if (record.getStatus() == AttendanceStatus.CHECKED_OUT) {
            return AttendanceState.CHECKED_OUT;
        }
        return AttendanceState.WORKING;
    }

    private String formatCheckIn(AttendanceRecord record) {
        return record == null ? "-" : record.getCheckInTime().format(TIME_FORMATTER);
    }

    private String formatCheckOut(AttendanceRecord record) {
        return record == null || record.getCheckOutTime() == null
                ? "-"
                : record.getCheckOutTime().format(TIME_FORMATTER);
    }

    private String buildNote(AttendanceRecord record) {
        if (record == null) {
            return "오늘 출근 기록 없음";
        }
        if (record.isLate()) {
            return "지각 출근";
        }
        if (record.getStatus() == AttendanceStatus.CHECKED_OUT) {
            return "퇴근 완료";
        }
        return "근무 중";
    }

    private LocalTime parseOptionalTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return LocalTime.parse(value.trim());
        } catch (Exception exception) {
            throw new IllegalArgumentException(fieldName + "은 HH:mm 형식으로 입력해 주세요.");
        }
    }

    private String formatScheduleTime(LocalTime time) {
        return time == null ? "-" : time.format(TIME_FORMATTER);
    }

    private boolean matchesFilter(AttendanceRow row, String filter) {
        return switch (normalizeDashboardFilter(filter)) {
            case "PRESENT" -> row.state() == AttendanceState.WORKING;
            case "LATE" -> row.state() == AttendanceState.LATE;
            case "ABSENT" -> row.state() == AttendanceState.ABSENT;
            case "CHECKED_OUT" -> row.state() == AttendanceState.CHECKED_OUT;
            default -> true;
        };
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

    public String getDashboardFilterLabel(String filter) {
        return switch (normalizeDashboardFilter(filter)) {
            case "PRESENT" -> "정상 출근";
            case "LATE" -> "지각";
            case "ABSENT" -> "미출근";
            case "CHECKED_OUT" -> "퇴근 완료";
            default -> "전체 직원";
        };
    }
}
