package com.attendance.adminweb.model;

public record EmployeeRow(
        Long id,
        String employeeCode,
        String name,
        String workplaceName,
        String role,
        String workStartTime,
        String workEndTime,
        AttendanceState attendanceState,
        String checkInTime,
        String checkOutTime,
        boolean deviceRegistered,
        boolean active,
        boolean deleted
) {
}
