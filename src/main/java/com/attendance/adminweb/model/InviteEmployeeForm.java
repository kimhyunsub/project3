package com.attendance.adminweb.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InviteEmployeeForm {

    @NotBlank(message = "사번을 입력해 주세요.")
    @Size(max = 50, message = "사번은 50자 이하여야 합니다.")
    private String employeeCode;

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "권한을 선택해 주세요.")
    private String role = "EMPLOYEE";

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

    public Long getWorkplaceId() {
        return workplaceId;
    }

    public void setWorkplaceId(Long workplaceId) {
        this.workplaceId = workplaceId;
    }

    public String getNormalizedRole() {
        return role == null ? "" : role.trim().toUpperCase();
    }
}
