package com.attendance.adminweb.model;

public record EmployeeActionResponse(
        boolean success,
        String message,
        EmployeeInviteResult inviteResult
) {
}
