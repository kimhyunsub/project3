package com.attendance.adminweb.controller;

import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.EmployeePage;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.model.WorkplaceLocationForm;
import com.attendance.adminweb.service.AdminService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.DateTimeException;
import java.time.YearMonth;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

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
                            Model model,
                            Principal principal) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        String normalizedFilter = adminService.normalizeDashboardFilter(filter);
        model.addAttribute("selectedFilter", normalizedFilter);
        model.addAttribute("selectedFilterLabel", adminService.getDashboardFilterLabel(normalizedFilter));
        model.addAttribute("selectedWorkplaceId", resolvedWorkplaceId);
        model.addAttribute("workplaceScopedAdmin", adminService.isWorkplaceScopedAdmin(principal.getName()));
        model.addAttribute("workplaceOptions", adminService.getWorkplaceOptions(principal.getName()));
        model.addAttribute("summary", adminService.getTodaySummary(principal.getName(), resolvedWorkplaceId));
        model.addAttribute("recentAttendances", adminService.getTodayAttendances(principal.getName(), normalizedFilter, resolvedWorkplaceId));
        return "dashboard";
    }

    @GetMapping("/attendance/monthly")
    public String monthlyAttendance(@RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month,
                                    @RequestParam(required = false) String employeeCode,
                                    @RequestParam(required = false) Long workplaceId,
                                    Model model,
                                    Principal principal) {
        YearMonth selectedMonth = resolveYearMonth(year, month);
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("selectedEmployeeCode", employeeCode);
        model.addAttribute("selectedWorkplaceId", resolvedWorkplaceId);
        model.addAttribute("workplaceScopedAdmin", adminService.isWorkplaceScopedAdmin(principal.getName()));
        model.addAttribute("workplaceOptions", adminService.getWorkplaceOptions(principal.getName()));
        model.addAttribute("monthlySummary", adminService.getMonthlyAttendanceSummary(principal.getName(), selectedMonth, resolvedWorkplaceId));
        model.addAttribute("monthlyEmployees", adminService.getMonthlyAttendanceEmployees(principal.getName(), selectedMonth, resolvedWorkplaceId));
        model.addAttribute("monthlyAttendances", adminService.getMonthlyAttendanceRecords(principal.getName(), selectedMonth, resolvedWorkplaceId));
        model.addAttribute("selectedEmployeeDetail",
                adminService.getMonthlyAttendanceEmployeeDetail(principal.getName(), selectedMonth, employeeCode, resolvedWorkplaceId));
        return "monthly-attendance";
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

    @GetMapping("/employees")
    public String employees(@RequestParam(required = false) Long editId,
                            @RequestParam(defaultValue = "false") boolean createMode,
                            @RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "false") boolean showDeleted,
                            @RequestParam(required = false) Long workplaceId,
                            Model model,
                            Principal principal) {
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        EmployeePage employeePage = adminService.getEmployees(principal.getName(), showDeleted, resolvedWorkplaceId, page, EMPLOYEE_PAGE_SIZE);
        model.addAttribute("employees", employeePage.employees());
        model.addAttribute("currentPage", employeePage.currentPage());
        model.addAttribute("totalPages", employeePage.totalPages());
        model.addAttribute("totalCount", employeePage.totalCount());
        model.addAttribute("pageSize", employeePage.pageSize());
        model.addAttribute("hasPreviousPage", employeePage.hasPrevious());
        model.addAttribute("hasNextPage", employeePage.hasNext());
        model.addAttribute("editId", editId);
        model.addAttribute("showDeleted", showDeleted);
        model.addAttribute("selectedWorkplaceId", resolvedWorkplaceId);
        model.addAttribute("workplaceScopedAdmin", adminService.isWorkplaceScopedAdmin(principal.getName()));
        model.addAttribute("canManageAdminRoles", adminService.canManageAdminRoles(principal.getName()));
        model.addAttribute("workplaceOptions", adminService.getWorkplaceOptions(principal.getName()));
        boolean employeeModalOpen = editId != null || createMode || Boolean.TRUE.equals(model.asMap().get("createMode"));
        if (!model.containsAttribute("employeeForm")) {
            model.addAttribute("employeeForm", editId == null
                    ? adminService.getEmployeeFormForCreate()
                    : adminService.getEmployeeFormForEdit(principal.getName(), editId));
        }
        model.addAttribute("createMode", createMode);
        model.addAttribute("editing", editId != null);
        model.addAttribute("employeeModalOpen", employeeModalOpen);
        return "employees";
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
    public String companyLocation(@RequestParam(required = false) Long workplaceId, Model model, Principal principal) {
        boolean workplaceScopedAdmin = adminService.isWorkplaceScopedAdmin(principal.getName());
        Long resolvedWorkplaceId = adminService.resolveRequestedWorkplaceId(principal.getName(), workplaceId);
        if (!model.containsAttribute("locationForm")) {
            if (!workplaceScopedAdmin) {
                model.addAttribute("locationForm", adminService.getCompanyLocationForm(principal.getName()));
            }
        }
        if (!model.containsAttribute("workplaceForm")) {
            model.addAttribute("workplaceForm", resolvedWorkplaceId == null
                    ? new WorkplaceLocationForm()
                    : adminService.getWorkplaceLocationForm(principal.getName(), resolvedWorkplaceId));
        }
        if (!model.containsAttribute("newWorkplaceForm")) {
            model.addAttribute("newWorkplaceForm", new WorkplaceLocationForm());
        }
        if (!workplaceScopedAdmin) {
            model.addAttribute("location", adminService.getCompanyLocation(principal.getName()));
        }
        model.addAttribute("workplaces", adminService.getWorkplaces(principal.getName()));
        model.addAttribute("selectedWorkplaceId", resolvedWorkplaceId);
        model.addAttribute("workplaceScopedAdmin", workplaceScopedAdmin);
        return "location-settings";
    }

    @PostMapping("/settings/location")
    public String updateCompanyLocation(@Valid @ModelAttribute("locationForm") CompanyLocationForm form,
                                        BindingResult bindingResult,
                                        RedirectAttributes redirectAttributes,
                                        Principal principal) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.locationForm", bindingResult);
            redirectAttributes.addFlashAttribute("locationForm", form);
            return "redirect:/settings/location";
        }

        adminService.updateCompanyLocation(principal.getName(), form);
        redirectAttributes.addFlashAttribute("message", "회사 위치가 저장되었습니다.");
        return "redirect:/settings/location";
    }

    @PostMapping("/settings/location/workplaces")
    public String createWorkplace(@Valid @ModelAttribute("newWorkplaceForm") WorkplaceLocationForm form,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Principal principal) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.newWorkplaceForm", bindingResult);
            redirectAttributes.addFlashAttribute("newWorkplaceForm", form);
            return "redirect:/settings/location";
        }

        try {
            adminService.createWorkplace(principal.getName(), form);
            redirectAttributes.addFlashAttribute("message", "사업장이 추가되었습니다.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("newWorkplaceForm", form);
        }
        return "redirect:/settings/location";
    }

    @PostMapping("/settings/location/workplaces/{workplaceId}")
    public String updateWorkplace(@PathVariable Long workplaceId,
                                  @Valid @ModelAttribute("workplaceForm") WorkplaceLocationForm form,
                                  BindingResult bindingResult,
                                  RedirectAttributes redirectAttributes,
                                  Principal principal) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.workplaceForm", bindingResult);
            redirectAttributes.addFlashAttribute("workplaceForm", form);
            return "redirect:/settings/location?workplaceId=" + workplaceId;
        }

        try {
            adminService.updateWorkplace(principal.getName(), workplaceId, form);
            redirectAttributes.addFlashAttribute("message", "사업장 위치가 저장되었습니다.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("workplaceForm", form);
        }
        return "redirect:/settings/location?workplaceId=" + workplaceId;
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
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("createMode", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, null, true);
        }

        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
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
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", true);
            return buildEmployeesRedirect(page, showDeleted, workplaceId, employeeId, false);
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
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
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
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, false, workplaceId, null, false);
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
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, true, workplaceId, null, false);
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
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return buildEmployeesRedirect(page, showDeleted, workplaceId, null, false);
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
        if (form.getPassword() == null || form.getPassword().isBlank()) {
            bindingResult.rejectValue("password", "required", "비밀번호를 입력해 주세요.");
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
