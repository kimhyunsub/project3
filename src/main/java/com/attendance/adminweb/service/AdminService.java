package com.attendance.adminweb.service;

import com.attendance.adminweb.domain.entity.AttendanceRecord;
import com.attendance.adminweb.domain.entity.AttendanceStatus;
import com.attendance.adminweb.domain.entity.Company;
import com.attendance.adminweb.domain.entity.CompanySetting;
import com.attendance.adminweb.domain.entity.Employee;
import com.attendance.adminweb.domain.entity.EmployeeRole;
import com.attendance.adminweb.domain.repository.AttendanceRecordRepository;
import com.attendance.adminweb.domain.repository.CompanyRepository;
import com.attendance.adminweb.domain.repository.CompanySettingRepository;
import com.attendance.adminweb.domain.repository.EmployeeRepository;
import com.attendance.adminweb.model.AttendanceRow;
import com.attendance.adminweb.model.AttendanceState;
import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.CompanyLocationView;
import com.attendance.adminweb.model.DashboardSummary;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeeRow;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.model.MonthlyAttendanceEmployeeRow;
import com.attendance.adminweb.model.MonthlyAttendanceRecordRow;
import com.attendance.adminweb.model.MonthlyAttendanceSummary;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

@Service
@Transactional(readOnly = true)
public class AdminService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 M월");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final DataFormatter DATA_FORMATTER = new DataFormatter();

    private final EmployeeRepository employeeRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanyRepository companyRepository;
    private final CompanySettingRepository companySettingRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminService(EmployeeRepository employeeRepository,
                        AttendanceRecordRepository attendanceRecordRepository,
                        CompanyRepository companyRepository,
                        CompanySettingRepository companySettingRepository,
                        PasswordEncoder passwordEncoder) {
        this.employeeRepository = employeeRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.companyRepository = companyRepository;
        this.companySettingRepository = companySettingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public DashboardSummary getTodaySummary(String employeeCode) {
        List<Employee> employees = getCompanyEmployees(employeeCode);
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

    public List<AttendanceRow> getTodayAttendances(String employeeCode, String filter) {
        List<Employee> employees = getCompanyEmployees(employeeCode);
        Map<Long, AttendanceRecord> recordsByEmployeeId = getTodayRecordsByEmployee(employees);

        return employees.stream()
                .map(employee -> {
                    AttendanceRecord record = recordsByEmployeeId.get(employee.getId());
                    AttendanceState state = toState(record);
                    return new AttendanceRow(
                            employee.getEmployeeCode(),
                            employee.getName(),
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

    public MonthlyAttendanceSummary getMonthlyAttendanceSummary(String employeeCode, YearMonth yearMonth) {
        List<Employee> employees = getCompanyEmployees(employeeCode);
        List<AttendanceRecord> records = getMonthlyRecords(employeeCode, yearMonth);
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

    public List<MonthlyAttendanceEmployeeRow> getMonthlyAttendanceEmployees(String employeeCode, YearMonth yearMonth) {
        List<Employee> employees = getCompanyEmployees(employeeCode);
        Map<Long, List<AttendanceRecord>> recordsByEmployeeId = getMonthlyRecords(employeeCode, yearMonth).stream()
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

    public List<MonthlyAttendanceRecordRow> getMonthlyAttendanceRecords(String employeeCode, YearMonth yearMonth) {
        return getMonthlyRecords(employeeCode, yearMonth).stream()
                .sorted(Comparator.comparing(AttendanceRecord::getAttendanceDate).reversed()
                        .thenComparing(AttendanceRecord::getCheckInTime, Comparator.reverseOrder()))
                .map(record -> new MonthlyAttendanceRecordRow(
                        record.getAttendanceDate().format(DATE_FORMATTER),
                        record.getEmployee().getEmployeeCode(),
                        record.getEmployee().getName(),
                        record.getEmployee().getRole().name(),
                        toState(record),
                        formatCheckIn(record),
                        formatCheckOut(record),
                        buildNote(record)
                ))
                .toList();
    }

    public List<EmployeeRow> getEmployees(String employeeCode) {
        List<Employee> employees = getCompanyEmployees(employeeCode);
        Map<Long, AttendanceRecord> recordsByEmployeeId = getTodayRecordsByEmployee(employees);

        return employees.stream()
                .map(employee -> {
                    AttendanceRecord record = recordsByEmployeeId.get(employee.getId());
                    return new EmployeeRow(
                            employee.getId(),
                            employee.getEmployeeCode(),
                            employee.getName(),
                            employee.getRole().name(),
                            employee.getCompany().getName(),
                            formatScheduleTime(employee.getWorkStartTime()),
                            formatScheduleTime(employee.getWorkEndTime()),
                            toState(record),
                            formatCheckIn(record),
                            employee.getRegisteredDeviceId() != null && !employee.getRegisteredDeviceId().isBlank(),
                            formatRegisteredDeviceName(employee),
                            formatDeviceRegisteredAt(employee.getDeviceRegisteredAt())
                    );
                })
                .toList();
    }

    public EmployeeForm getEmployeeFormForCreate() {
        EmployeeForm form = new EmployeeForm();
        form.setRole(EmployeeRole.EMPLOYEE.name());
        form.setPassword("");
        return form;
    }

    public EmployeeForm getEmployeeFormForEdit(String employeeCode, Long employeeId) {
        Employee employee = getEditableEmployee(employeeCode, employeeId);
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
            String[] headers = {"사번", "이름", "권한", "비밀번호", "출근 기준 시간", "퇴근 기준 시간"};
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
            sampleRow1.createCell(4).setCellValue("09:00");
            sampleRow1.createCell(5).setCellValue("18:00");

            Row sampleRow2 = sheet.createRow(2);
            sampleRow2.createCell(0).setCellValue("EMP005");
            sampleRow2.createCell(1).setCellValue("박소연");
            sampleRow2.createCell(2).setCellValue("ADMIN");
            sampleRow2.createCell(3).setCellValue("securepass1");
            sampleRow2.createCell(4).setCellValue("10:00");
            sampleRow2.createCell(5).setCellValue("19:00");

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
                setting.getLateAfterTime().format(TIME_FORMATTER)
        );
    }

    public CompanyLocationForm getCompanyLocationForm(String employeeCode) {
        CompanyLocationView location = getCompanyLocation(employeeCode);
        CompanyLocationForm form = new CompanyLocationForm();
        form.setCompanyName(location.companyName());
        form.setLatitude(location.latitude());
        form.setLongitude(location.longitude());
        form.setAllowedRadiusMeters(location.allowedRadiusMeters());
        form.setLateAfterTime(location.lateAfterTime());
        return form;
    }

    @Transactional
    public void updateCompanyLocation(String employeeCode, CompanyLocationForm form) {
        Employee admin = getEmployeeByCode(employeeCode);
        Company company = companyRepository.findById(admin.getCompany().getId())
                .orElseThrow(() -> new EntityNotFoundException("회사를 찾을 수 없습니다."));
        CompanySetting setting = companySettingRepository.findByCompany(company)
                .orElseThrow(() -> new EntityNotFoundException("회사 설정을 찾을 수 없습니다."));

        company.updateName(form.getCompanyName().trim());
        company.updateLocation(form.getLatitude(), form.getLongitude());
        setting.updateAllowedRadiusMeters(form.getAllowedRadiusMeters());
    }

    @Transactional
    public void createEmployee(String adminEmployeeCode, EmployeeForm form) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        validateDuplicateEmployeeCode(form.getEmployeeCode(), null);

        Employee employee = new Employee(
                form.getEmployeeCode().trim(),
                form.getName().trim(),
                passwordEncoder.encode(form.getPassword()),
                form.getEmployeeRole(),
                admin.getCompany(),
                parseOptionalTime(form.getWorkStartTime(), "출근 기준 시간"),
                parseOptionalTime(form.getWorkEndTime(), "퇴근 기준 시간")
        );
        employeeRepository.save(employee);
    }

    @Transactional
    public void updateEmployee(String adminEmployeeCode, Long employeeId, EmployeeForm form) {
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);
        validateDuplicateEmployeeCode(form.getEmployeeCode(), employeeId);

        employee.updateProfile(
                form.getEmployeeCode().trim(),
                form.getName().trim(),
                form.getEmployeeRole(),
                parseOptionalTime(form.getWorkStartTime(), "출근 기준 시간"),
                parseOptionalTime(form.getWorkEndTime(), "퇴근 기준 시간")
        );

        if (form.getPassword() != null && !form.getPassword().isBlank()) {
            employee.updatePassword(passwordEncoder.encode(form.getPassword()));
        }
    }

    @Transactional
    public void deleteEmployee(String adminEmployeeCode, Long employeeId) {
        Employee admin = getEmployeeByCode(adminEmployeeCode);
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);

        if (admin.getId().equals(employee.getId())) {
            throw new IllegalArgumentException("현재 로그인한 관리자 계정은 삭제할 수 없습니다.");
        }

        if (attendanceRecordRepository.existsByEmployeeId(employeeId)) {
            throw new IllegalArgumentException("출근 기록이 있는 직원은 삭제할 수 없습니다.");
        }

        employeeRepository.delete(employee);
    }

    @Transactional
    public void resetEmployeeDevice(String adminEmployeeCode, Long employeeId) {
        Employee employee = getEditableEmployee(adminEmployeeCode, employeeId);
        employee.resetRegisteredDevice();
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
                String workStartTime = readCell(row, 4);
                String workEndTime = readCell(row, 5);

                try {
                    validateUploadRow(rowIndex + 1, employeeCode, name, roleValue, password, workStartTime, workEndTime, existingCodes);
                    Employee employee = new Employee(
                            employeeCode,
                            name,
                            passwordEncoder.encode(password),
                            EmployeeRole.valueOf(roleValue),
                            admin.getCompany(),
                            parseOptionalTime(workStartTime, rowIndex + 1 + "행 출근 기준 시간"),
                            parseOptionalTime(workEndTime, rowIndex + 1 + "행 퇴근 기준 시간")
                    );
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
        return employeeRepository.findAllByCompanyIdOrderByNameAsc(admin.getCompany().getId());
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

        return employee;
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
        if (!roleValue.equals("ADMIN") && !roleValue.equals("EMPLOYEE")) {
            throw new IllegalArgumentException(rowNumber + "행: 권한은 ADMIN 또는 EMPLOYEE만 사용할 수 있습니다.");
        }
        if (password.isBlank()) {
            throw new IllegalArgumentException(rowNumber + "행: 비밀번호를 입력해 주세요.");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException(rowNumber + "행: 비밀번호는 8자 이상이어야 합니다.");
        }
        parseOptionalTime(workStartTime, rowNumber + "행 출근 기준 시간");
        parseOptionalTime(workEndTime, rowNumber + "행 퇴근 기준 시간");
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
        for (int index = 0; index < 6; index++) {
            if (!readCell(row, index).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Row row, int cellIndex) {
        Cell cell = row == null ? null : row.getCell(cellIndex);
        return cell == null ? "" : DATA_FORMATTER.formatCellValue(cell).trim();
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
        );
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
