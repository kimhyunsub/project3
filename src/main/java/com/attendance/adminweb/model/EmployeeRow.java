package com.attendance.adminweb.model;

public record EmployeeRow(
        Long id,
        String employeeCode,
        String name,
        String role,
        String companyName,
        String workStartTime,
        String workEndTime,
        AttendanceState attendanceState,
        String checkInTime,
        boolean deviceRegistered,
        String registeredDeviceName,
        String deviceRegisteredAt
) {
}
