package com.attendance.adminweb.model;

public record EmployeeInviteResult(
    String employeeCode,
    String employeeName,
    String role,
    String workplaceName,
    String inviteUrl,
    String expiresAt,
    String message
) {
}
