package com.attendance.adminweb.controller;

import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.DashboardData;
import com.attendance.adminweb.model.DashboardPageContext;
import com.attendance.adminweb.model.EmployeeActionResponse;
import com.attendance.adminweb.model.EmployeePageContext;
import com.attendance.adminweb.model.EmployeePage;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.model.InviteEmployeeForm;
import com.attendance.adminweb.model.LocationSettingsPageContext;
import com.attendance.adminweb.model.MonthlyAttendanceData;
import com.attendance.adminweb.model.MonthlyAttendancePageContext;
import com.attendance.adminweb.model.SqlConsoleResponse;
import com.attendance.adminweb.model.SqlConsolePageContext;
import com.attendance.adminweb.model.WorkplaceLocationForm;
import com.attendance.adminweb.service.AdminService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.web.csrf.CsrfToken;

@Controller
public class AdminController {
    private static final int EMPLOYEE_PAGE_SIZE = 12;

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String filter,
                            @RequestParam(required = false) Long workplaceId,
                            Principal principal) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        String normalizedFilter = adminService.normalizeDashboardFilter(filter);
        StringBuilder redirect = new StringBuilder("redirect:/app/dashboard.html?filter=").append(normalizedFilter);
        if (resolvedWorkplaceId != null) {
            redirect.append("&workplaceId=").append(resolvedWorkplaceId);
        }
        return redirect.toString();
    }

    @GetMapping("/dashboard/data")
    @ResponseBody
    public DashboardData dashboardData(@RequestParam(defaultValue = "ALL") String filter,
                                       @RequestParam(required = false) Long workplaceId,
                                       Principal principal) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        return adminService.getTodayDashboard(principal.getName(), filter, resolvedWorkplaceId);
    }

    @GetMapping("/dashboard/page-context")
    @ResponseBody
    public DashboardPageContext dashboardPageContext(@RequestParam(defaultValue = "ALL") String filter,
                                                     @RequestParam(required = false) Long workplaceId,
                                                     Principal principal,
                                                     CsrfToken csrfToken) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        String normalizedFilter = adminService.normalizeDashboardFilter(filter);
        return new DashboardPageContext(
                normalizedFilter,
                resolvedWorkplaceId,
                adminService.isWorkplaceScopedAdmin(principal.getName()),
                adminService.canAccessSqlConsole(principal.getName()),
                csrfToken.getParameterName(),
                csrfToken.getToken(),
                adminService.getWorkplaceOptions(principal.getName())
        );
    }

    @GetMapping("/attendance/monthly")
    public String monthlyAttendance(@RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month,
                                    @RequestParam(required = false) String employeeCode,
                                    @RequestParam(required = false) Long workplaceId,
                                    Principal principal) {
        YearMonth selectedMonth = resolveYearMonth(year, month);
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        StringBuilder redirect = new StringBuilder("redirect:/app/monthly-attendance.html?year=")
                .append(selectedMonth.getYear())
                .append("&month=")
                .append(selectedMonth.getMonthValue());
        if (employeeCode != null && !employeeCode.isBlank()) {
            redirect.append("&employeeCode=").append(employeeCode);
        }
        if (resolvedWorkplaceId != null) {
            redirect.append("&workplaceId=").append(resolvedWorkplaceId);
        }
        return redirect.toString();
    }

    @GetMapping("/attendance/monthly/data")
    @ResponseBody
    public MonthlyAttendanceData monthlyAttendanceData(@RequestParam Integer year,
                                                       @RequestParam Integer month,
                                                       @RequestParam(required = false) String employeeCode,
                                                       @RequestParam(required = false) Long workplaceId,
                                                       Principal principal) {
        YearMonth selectedMonth = resolveYearMonth(year, month);
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        return adminService.getMonthlyAttendanceData(principal.getName(), selectedMonth, employeeCode, resolvedWorkplaceId);
    }

    @GetMapping("/attendance/monthly/page-context")
    @ResponseBody
    public MonthlyAttendancePageContext monthlyAttendancePageContext(@RequestParam Integer year,
                                                                     @RequestParam Integer month,
                                                                     @RequestParam(required = false) String employeeCode,
                                                                     @RequestParam(required = false) Long workplaceId,
                                                                     Principal principal,
                                                                     CsrfToken csrfToken) {
        YearMonth selectedMonth = resolveYearMonth(year, month);
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        return new MonthlyAttendancePageContext(
                selectedMonth.getYear(),
                selectedMonth.getMonthValue(),
                employeeCode,
                resolvedWorkplaceId,
                adminService.isWorkplaceScopedAdmin(principal.getName()),
                adminService.canAccessSqlConsole(principal.getName()),
                csrfToken.getParameterName(),
                csrfToken.getToken(),
                adminService.getWorkplaceOptions(principal.getName())
        );
    }

    @GetMapping("/attendance/monthly/excel")
    public ResponseEntity<ByteArrayResource> downloadMonthlyAttendanceExcel(@RequestParam Integer year,
                                                                            @RequestParam Integer month,
                                                                            @RequestParam(required = false) Long workplaceId,
                                                                            Principal principal) {
        YearMonth selectedMonth = resolveYearMonth(year, month);
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        byte[] fileBytes = adminService.exportMonthlyAttendanceExcel(principal.getName(), selectedMonth, resolvedWorkplaceId);
        String fileName = "monthly-attendance-" + selectedMonth + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(fileBytes.length)
                .body(new ByteArrayResource(fileBytes));
    }

    @GetMapping("/sql-console")
    public String sqlConsole(Principal principal) {
        ensureSqlConsoleAccess(principal);
        return "redirect:/app/sql-console.html";
    }

    @GetMapping("/sql-console/page-context")
    @ResponseBody
    public SqlConsolePageContext sqlConsolePageContext(Principal principal, CsrfToken csrfToken) {
        ensureSqlConsoleAccess(principal);
        return new SqlConsolePageContext(
                adminService.isWorkplaceScopedAdmin(principal.getName()),
                csrfToken.getParameterName(),
                csrfToken.getToken(),
                adminService.getSqlConsoleSnippets(principal.getName())
        );
    }

    @PostMapping("/sql-console/query-data")
    @ResponseBody
    public SqlConsoleResponse executeSqlQueryData(@RequestParam String queryText, Principal principal) {
        ensureSqlConsoleAccess(principal);
        try {
            return new SqlConsoleResponse(
                queryText,
                adminService.executeReadOnlySql(principal.getName(), queryText),
                null,
                adminService.getSqlConsoleSnippets(principal.getName())
            );
        } catch (IllegalArgumentException exception) {
            return new SqlConsoleResponse(
                queryText,
                null,
                exception.getMessage(),
                adminService.getSqlConsoleSnippets(principal.getName())
            );
        }
    }

    @PostMapping("/sql-console/excel")
    public ResponseEntity<ByteArrayResource> downloadSqlQueryExcel(@RequestParam String queryText,
                                                                   Principal principal) {
        ensureSqlConsoleAccess(principal);
        byte[] fileBytes = adminService.exportSqlQueryExcel(principal.getName(), queryText);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("sql-report.xlsx", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(fileBytes.length)
                .body(new ByteArrayResource(fileBytes));
    }

    private YearMonth resolveYearMonth(Integer year, Integer month) {
        YearMonth currentMonth = YearMonth.now();
        int resolvedYear = year == null ? currentMonth.getYear() : year;
        int resolvedMonth = month == null ? currentMonth.getMonthValue() : month;

        try {
            return YearMonth.of(resolvedYear, resolvedMonth);
        } catch (DateTimeException exception) {
            return currentMonth;
        }
    }

    private void ensureSqlConsoleAccess(Principal principal) {
        if (!adminService.canAccessSqlConsole(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "회사 관리자만 SQL 리포트에 접근할 수 있습니다.");
        }
    }

    @GetMapping("/employees")
    public String employees(@RequestParam(required = false) Long editId,
                            @RequestParam(defaultValue = "false") boolean createMode,
                            @RequestParam(defaultValue = "false") boolean inviteMode,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "false") boolean showDeleted,
                            @RequestParam(required = false) Long workplaceId,
                            Principal principal) {
        StringBuilder redirect = new StringBuilder("redirect:/app/employees.html?page=").append(page);
        if (showDeleted) {
            redirect.append("&showDeleted=true");
        }
        if (workplaceId != null) {
            redirect.append("&workplaceId=").append(workplaceId);
        }
        if (editId != null) {
            redirect.append("&editId=").append(editId);
        }
        if (createMode) {
            redirect.append("&createMode=true");
        }
        if (inviteMode) {
            redirect.append("&inviteMode=true");
        }
        return redirect.toString();
    }

    @GetMapping("/employees/page-context")
    @ResponseBody
    public EmployeePageContext employeePageContext(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "false") boolean showDeleted,
                                                   @RequestParam(required = false) Long workplaceId,
                                                   @RequestParam(required = false) Long editId,
                                                   @RequestParam(defaultValue = "false") boolean createMode,
                                                   @RequestParam(defaultValue = "false") boolean inviteMode,
                                                   Principal principal,
                                                   CsrfToken csrfToken) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        return new EmployeePageContext(
                page,
                showDeleted,
                resolvedWorkplaceId,
                editId,
                createMode,
                inviteMode,
                adminService.isWorkplaceScopedAdmin(principal.getName()),
                adminService.canAccessSqlConsole(principal.getName()),
                adminService.canManageAdminRoles(principal.getName()),
                csrfToken.getParameterName(),
                csrfToken.getToken(),
                adminService.getWorkplaceOptions(principal.getName())
        );
    }

    @GetMapping("/employees/create-form-data")
    @ResponseBody
    public EmployeeForm employeeCreateFormData() {
        return adminService.getEmployeeFormForCreate();
    }

    @GetMapping("/employees/{employeeId}/form-data")
    @ResponseBody
    public EmployeeForm employeeFormData(@PathVariable Long employeeId, Principal principal) {
        return adminService.getEmployeeFormForEdit(principal.getName(), employeeId);
    }

    @GetMapping("/employees/list-data")
    @ResponseBody
    public EmployeePage employeeListData(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "false") boolean showDeleted,
                                         @RequestParam(required = false) Long workplaceId,
                                         Principal principal) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        return adminService.getEmployees(principal.getName(), showDeleted, resolvedWorkplaceId, page, EMPLOYEE_PAGE_SIZE);
    }

    @GetMapping("/employees/template")
    public ResponseEntity<ByteArrayResource> downloadEmployeeTemplate() {
        byte[] fileBytes = adminService.createEmployeeUploadTemplate();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("employee-upload-template.xlsx", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(fileBytes.length)
                .body(new ByteArrayResource(fileBytes));
    }

    @GetMapping("/settings/location")
    public String companyLocation(@RequestParam(required = false) Long workplaceId, Principal principal) {
        boolean workplaceScopedAdmin = adminService.isWorkplaceScopedAdmin(principal.getName());
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        StringBuilder redirect = new StringBuilder("redirect:/app/settings.html");
        if (resolvedWorkplaceId != null) {
            redirect.append("?workplaceId=").append(resolvedWorkplaceId);
        } else if (workplaceScopedAdmin) {
            redirect.append("?workplaceId=").append(adminService.getAssignedWorkplaceId(principal.getName()));
        }
        return redirect.toString();
    }

    @GetMapping("/settings/location/page-context")
    @ResponseBody
    public LocationSettingsPageContext locationSettingsPageContext(@RequestParam(required = false) Long workplaceId,
                                                                   Principal principal,
                                                                   CsrfToken csrfToken) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        boolean workplaceScopedAdmin = adminService.isWorkplaceScopedAdmin(principal.getName());
        return new LocationSettingsPageContext(
                resolvedWorkplaceId,
                workplaceScopedAdmin,
                adminService.canAccessSqlConsole(principal.getName()),
                csrfToken.getParameterName(),
                csrfToken.getToken(),
                workplaceScopedAdmin ? null : adminService.getCompanyLocation(principal.getName()),
                resolvedWorkplaceId == null ? null : adminService.getSelectedWorkplace(principal.getName(), resolvedWorkplaceId),
                adminService.getWorkplaces(principal.getName())
        );
    }

    @PostMapping("/settings/location/update-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> updateCompanyLocationData(@Valid @ModelAttribute CompanyLocationForm form,
                                                                            BindingResult bindingResult,
                                                                            Principal principal) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(
                    false,
                    bindingResult.getAllErrors().get(0).getDefaultMessage(),
                    null
            ));
        }

        try {
            adminService.updateCompanyLocation(principal.getName(), form);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "회사 설정이 저장되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/settings/location/workplaces/create-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> createWorkplaceData(@Valid @ModelAttribute WorkplaceLocationForm form,
                                                                      BindingResult bindingResult,
                                                                      Principal principal) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(
                    false,
                    bindingResult.getAllErrors().get(0).getDefaultMessage(),
                    null
            ));
        }

        try {
            adminService.createWorkplace(principal.getName(), form);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "사업장이 추가되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/settings/location/workplaces/{workplaceId}/update-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> updateWorkplaceData(@PathVariable Long workplaceId,
                                                                      @Valid @ModelAttribute WorkplaceLocationForm form,
                                                                      BindingResult bindingResult,
                                                                      Principal principal) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(
                    false,
                    bindingResult.getAllErrors().get(0).getDefaultMessage(),
                    null
            ));
        }

        try {
            adminService.updateWorkplace(principal.getName(), workplaceId, form);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "사업장 설정이 저장되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees")
    public String createEmployee(@Valid @ModelAttribute("employeeForm") EmployeeForm form,
                                 BindingResult bindingResult,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "false") boolean showDeleted,
                                 @RequestParam(name = "listWorkplaceId", required = false) Long workplaceId,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        validateCreateEmployeeForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.employeeForm", bindingResult);
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", false);
            redirectAttributes.addFlashAttribute("createMode", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, true);
        }

        try {
            adminService.createEmployee(principal.getName(), form);
            redirectAttributes.addFlashAttribute("message", "직원이 등록되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("createMode", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, true);
        }

        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
    }

    @PostMapping("/employees/create-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> createEmployeeData(@Valid @ModelAttribute EmployeeForm form,
                                                                     BindingResult bindingResult,
                                                                     Principal principal) {
        validateCreateEmployeeForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(
                    false,
                    bindingResult.getAllErrors().get(0).getDefaultMessage(),
                    null
            ));
        }

        try {
            adminService.createEmployee(principal.getName(), form);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "직원이 등록되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees/upload")
    public String uploadEmployees(@RequestParam("employeeFile") MultipartFile employeeFile,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "false") boolean showDeleted,
                                  @RequestParam(name = "listWorkplaceId", required = false) Long workplaceId,
                                  RedirectAttributes redirectAttributes,
                                  Principal principal) {
        try {
            EmployeeUploadResult result = adminService.uploadEmployees(principal.getName(), employeeFile);
            if (result.successCount() > 0) {
                redirectAttributes.addFlashAttribute(
                        "message",
                        "엑셀 업로드가 완료되었습니다. " + result.successCount() + "건 등록, " + result.failureCount() + "건 실패"
                );
            } else if (result.hasFailures()) {
                redirectAttributes.addFlashAttribute("employeeErrorMessage", "업로드된 직원이 없습니다. 실패 사유를 확인해 주세요.");
            } else {
                redirectAttributes.addFlashAttribute("employeeErrorMessage", "등록할 직원 데이터가 없습니다.");
            }

            if (result.hasFailures()) {
                redirectAttributes.addFlashAttribute("uploadFailureMessages", result.failureMessages());
            }
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }

        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
    }

    @PostMapping("/employees/{employeeId}/update")
    public String updateEmployee(@PathVariable Long employeeId,
                                 @Valid @ModelAttribute("employeeForm") EmployeeForm form,
                                 BindingResult bindingResult,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "false") boolean showDeleted,
                                 @RequestParam(name = "listWorkplaceId", required = false) Long workplaceId,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        validateUpdateEmployeeForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.employeeForm", bindingResult);
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, employeeId, false);
        }

        try {
            adminService.updateEmployee(principal.getName(), employeeId, form);
            redirectAttributes.addFlashAttribute("message", "직원 정보가 수정되었습니다.");
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, employeeId, false);
        }
    }

    @PostMapping("/employees/{employeeId}/update-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> updateEmployeeData(@PathVariable Long employeeId,
                                                                     @Valid @ModelAttribute EmployeeForm form,
                                                                     BindingResult bindingResult,
                                                                     Principal principal) {
        validateUpdateEmployeeForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(
                    false,
                    bindingResult.getAllErrors().get(0).getDefaultMessage(),
                    null
            ));
        }

        try {
            adminService.updateEmployee(principal.getName(), employeeId, form);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "직원 정보가 수정되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees/invite")
    public String createEmployeeInvite(@Valid @ModelAttribute("inviteEmployeeForm") InviteEmployeeForm form,
                                       BindingResult bindingResult,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "false") boolean showDeleted,
                                       @RequestParam(name = "listWorkplaceId", required = false) Long workplaceId,
                                       RedirectAttributes redirectAttributes,
                                       Principal principal) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.inviteEmployeeForm", bindingResult);
            redirectAttributes.addFlashAttribute("inviteEmployeeForm", form);
            redirectAttributes.addFlashAttribute("inviteMode", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
        }

        try {
            redirectAttributes.addFlashAttribute("inviteResult", adminService.createEmployeeInvite(principal.getName(), form));
            redirectAttributes.addFlashAttribute("message", "직원 초대 링크가 생성되었습니다.");
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("inviteEmployeeForm", form);
            redirectAttributes.addFlashAttribute("inviteMode", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
        }
    }

    @PostMapping("/employees/{employeeId}/invite-link")
    public String createInviteLinkForExistingEmployee(@PathVariable Long employeeId,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "false") boolean showDeleted,
                                                      @RequestParam(required = false) Long workplaceId,
                                                      RedirectAttributes redirectAttributes,
                                                      Principal principal) {
        try {
            redirectAttributes.addFlashAttribute("inviteResult", adminService.createInviteForExistingEmployee(principal.getName(), employeeId));
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
        }
    }

    @PostMapping("/employees/{employeeId}/invite-link-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> createInviteLinkForExistingEmployeeData(@PathVariable Long employeeId,
                                                                                          Principal principal) {
        try {
            return ResponseEntity.ok(new EmployeeActionResponse(
                    true,
                    "직원 초대 링크가 생성되었습니다.",
                    adminService.createInviteForExistingEmployee(principal.getName(), employeeId)
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees/{employeeId}/usage")
    public String updateEmployeeUsage(@PathVariable Long employeeId,
                                      @RequestParam boolean active,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "false") boolean showDeleted,
                                      @RequestParam(required = false) Long workplaceId,
                                      RedirectAttributes redirectAttributes,
                                      Principal principal) {
        try {
            adminService.updateEmployeeUsage(principal.getName(), employeeId, active);
            redirectAttributes.addFlashAttribute("message", active ? "직원이 다시 사용 상태로 변경되었습니다." : "직원이 사용 중지되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
    }

    @PostMapping("/employees/{employeeId}/usage-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> updateEmployeeUsageData(@PathVariable Long employeeId,
                                                                          @RequestParam boolean active,
                                                                          Principal principal) {
        try {
            adminService.updateEmployeeUsage(principal.getName(), employeeId, active);
            return ResponseEntity.ok(new EmployeeActionResponse(
                    true,
                    active ? "직원이 다시 사용 상태로 변경되었습니다." : "직원이 사용 중지되었습니다.",
                    null
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees/{employeeId}/delete")
    public String deleteEmployee(@PathVariable Long employeeId,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(required = false) Long workplaceId,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        try {
            adminService.deleteEmployee(principal.getName(), employeeId);
            redirectAttributes.addFlashAttribute("message", "직원이 삭제 목록으로 이동되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, false, workplaceId, null, false);
    }

    @PostMapping("/employees/{employeeId}/delete-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> deleteEmployeeData(@PathVariable Long employeeId,
                                                                     Principal principal) {
        try {
            adminService.deleteEmployee(principal.getName(), employeeId);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "직원이 삭제 목록으로 이동되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees/{employeeId}/restore")
    public String restoreEmployee(@PathVariable Long employeeId,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(required = false) Long workplaceId,
                                  RedirectAttributes redirectAttributes,
                                  Principal principal) {
        try {
            adminService.restoreEmployee(principal.getName(), employeeId);
            redirectAttributes.addFlashAttribute("message", "직원이 복구되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, true, workplaceId, null, false);
    }

    @PostMapping("/employees/{employeeId}/restore-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> restoreEmployeeData(@PathVariable Long employeeId,
                                                                      Principal principal) {
        try {
            adminService.restoreEmployee(principal.getName(), employeeId);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "직원이 복구되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    @PostMapping("/employees/{employeeId}/device-reset")
    public String resetEmployeeDevice(@PathVariable Long employeeId,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "false") boolean showDeleted,
                                      @RequestParam(required = false) Long workplaceId,
                                      RedirectAttributes redirectAttributes,
                                      Principal principal) {
        try {
            adminService.resetEmployeeDevice(principal.getName(), employeeId);
            redirectAttributes.addFlashAttribute("message", "등록된 단말이 초기화되었습니다.");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
    }

    @PostMapping("/employees/{employeeId}/device-reset-data")
    @ResponseBody
    public ResponseEntity<EmployeeActionResponse> resetEmployeeDeviceData(@PathVariable Long employeeId,
                                                                          Principal principal) {
        try {
            adminService.resetEmployeeDevice(principal.getName(), employeeId);
            return ResponseEntity.ok(new EmployeeActionResponse(true, "등록된 단말이 초기화되었습니다.", null));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.badRequest().body(new EmployeeActionResponse(false, exception.getMessage(), null));
        }
    }

    private String buildEmployeesRedirect(int page, boolean showDeleted, Long workplaceId, Long editId, boolean createMode) {
        StringBuilder redirect = new StringBuilder("redirect:/employees?page=").append(page);
        if (showDeleted) {
            redirect.append("&showDeleted=true");
        }
        if (workplaceId != null) {
            redirect.append("&workplaceId=").append(workplaceId);
        }
        if (editId != null) {
            redirect.append("&editId=").append(editId);
        }
        if (createMode) {
            redirect.append("&createMode=true");
        }
        return redirect.toString();
    }

    private void validateCreateEmployeeForm(EmployeeForm form, BindingResult bindingResult) {
        if ("EMPLOYEE".equals(form.getNormalizedRole())) {
            return;
        }
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            bindingResult.rejectValue("password", "required", "관리자 계정은 비밀번호를 입력해 주세요.");
            return;
        }
        if (form.getPassword().length() < 8) {
            bindingResult.rejectValue("password", "length", "비밀번호는 8자 이상이어야 합니다.");
        }
    }

    private void validateUpdateEmployeeForm(EmployeeForm form, BindingResult bindingResult) {
        if (form.getPassword() != null && !form.getPassword().isBlank() && form.getPassword().length() < 8) {
            bindingResult.rejectValue("password", "length", "비밀번호는 8자 이상이어야 합니다.");
        }
    }
}
