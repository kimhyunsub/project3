package com.attendance.adminweb.model;

import com.attendance.adminweb.domain.entity.Employee;
import com.attendance.adminweb.domain.entity.EmployeeRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmployeeForm {

    private Long id;

    @NotBlank(message = "사번을 입력해 주세요.")
    @Size(max = 50, message = "사번은 50자 이하여야 합니다.")
    private String employeeCode;

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
    private String name;

    @NotBlank(message = "권한을 선택해 주세요.")
    private String role;

    private String password;

    private String workStartTime;

    private String workEndTime;

    private Long workplaceId;

    public static EmployeeForm from(Employee employee) {
        EmployeeForm form = new EmployeeForm();
        form.setId(employee.getId());
        form.setEmployeeCode(employee.getEmployeeCode());
        form.setName(employee.getName());
        form.setRole(employee.getRole().name());
        form.setPassword("");
        form.setWorkStartTime(employee.getWorkStartTime() == null ? "" : employee.getWorkStartTime().toString());
        form.setWorkEndTime(employee.getWorkEndTime() == null ? "" : employee.getWorkEndTime().toString());
        form.setWorkplaceId(employee.getWorkplace() == null ? null : employee.getWorkplace().getId());
        return form;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public EmployeeRole getEmployeeRole() {
        return EmployeeRole.valueOf(role);
    }
}
