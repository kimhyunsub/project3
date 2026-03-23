package com.attendance.adminweb.controller;

import com.attendance.adminweb.model.CompanyLocationForm;
import com.attendance.adminweb.model.EmployeeForm;
import com.attendance.adminweb.model.EmployeeUploadResult;
import com.attendance.adminweb.service.AdminService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
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

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String filter, Model model, Principal principal) {
        String normalizedFilter = adminService.normalizeDashboardFilter(filter);
        model.addAttribute("selectedFilter", normalizedFilter);
        model.addAttribute("selectedFilterLabel", adminService.getDashboardFilterLabel(normalizedFilter));
        model.addAttribute("summary", adminService.getTodaySummary(principal.getName()));
        model.addAttribute("recentAttendances", adminService.getTodayAttendances(principal.getName(), normalizedFilter));
        return "dashboard";
    }

    @GetMapping("/attendance/monthly")
    public String monthlyAttendance(@RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month,
                                    Model model,
                                    Principal principal) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth selectedMonth = YearMonth.of(
                year == null ? currentMonth.getYear() : year,
                month == null ? currentMonth.getMonthValue() : month
        );

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("monthlySummary", adminService.getMonthlyAttendanceSummary(principal.getName(), selectedMonth));
        model.addAttribute("monthlyEmployees", adminService.getMonthlyAttendanceEmployees(principal.getName(), selectedMonth));
        model.addAttribute("monthlyAttendances", adminService.getMonthlyAttendanceRecords(principal.getName(), selectedMonth));
        return "monthly-attendance";
    }

    @GetMapping("/employees")
    public String employees(@RequestParam(required = false) Long editId, Model model, Principal principal) {
        model.addAttribute("employees", adminService.getEmployees(principal.getName()));
        if (!model.containsAttribute("employeeForm")) {
            model.addAttribute("employeeForm", editId == null
                    ? adminService.getEmployeeFormForCreate()
                    : adminService.getEmployeeFormForEdit(principal.getName(), editId));
        }
        model.addAttribute("editing", editId != null);
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
    public String companyLocation(Model model, Principal principal) {
        if (!model.containsAttribute("locationForm")) {
            model.addAttribute("locationForm", adminService.getCompanyLocationForm(principal.getName()));
        }
        model.addAttribute("location", adminService.getCompanyLocation(principal.getName()));
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

    @PostMapping("/employees")
    public String createEmployee(@Valid @ModelAttribute("employeeForm") EmployeeForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        validateCreateEmployeeForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.employeeForm", bindingResult);
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", false);
            return "redirect:/employees";
        }

        try {
            adminService.createEmployee(principal.getName(), form);
            redirectAttributes.addFlashAttribute("message", "직원이 등록되었습니다.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("employeeForm", form);
        }

        return "redirect:/employees";
    }

    @PostMapping("/employees/upload")
    public String uploadEmployees(@RequestParam("employeeFile") MultipartFile employeeFile,
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

        return "redirect:/employees";
    }

    @PostMapping("/employees/{employeeId}/update")
    public String updateEmployee(@PathVariable Long employeeId,
                                 @Valid @ModelAttribute("employeeForm") EmployeeForm form,
                                 BindingResult bindingResult,
                                 RedirectAttributes redirectAttributes,
                                 Principal principal) {
        validateUpdateEmployeeForm(form, bindingResult);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.employeeForm", bindingResult);
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", true);
            return "redirect:/employees?editId=" + employeeId;
        }

        try {
            adminService.updateEmployee(principal.getName(), employeeId, form);
            redirectAttributes.addFlashAttribute("message", "직원 정보가 수정되었습니다.");
            return "redirect:/employees";
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
            redirectAttributes.addFlashAttribute("employeeForm", form);
            redirectAttributes.addFlashAttribute("editing", true);
            return "redirect:/employees?editId=" + employeeId;
        }
    }

    @PostMapping("/employees/{employeeId}/usage")
    public String updateEmployeeUsage(@PathVariable Long employeeId,
                                      @RequestParam boolean active,
                                      RedirectAttributes redirectAttributes,
                                      Principal principal) {
        try {
            adminService.updateEmployeeUsage(principal.getName(), employeeId, active);
            redirectAttributes.addFlashAttribute("message", active ? "직원이 다시 사용 상태로 변경되었습니다." : "직원이 사용 중지되었습니다.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return "redirect:/employees";
    }

    @PostMapping("/employees/{employeeId}/device-reset")
    public String resetEmployeeDevice(@PathVariable Long employeeId,
                                      RedirectAttributes redirectAttributes,
                                      Principal principal) {
        try {
            adminService.resetEmployeeDevice(principal.getName(), employeeId);
            redirectAttributes.addFlashAttribute("message", "등록된 단말이 초기화되었습니다.");
        } catch (IllegalArgumentException | DataIntegrityViolationException exception) {
            redirectAttributes.addFlashAttribute("employeeErrorMessage", exception.getMessage());
        }
        return "redirect:/employees";
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
