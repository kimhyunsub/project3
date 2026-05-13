package com.attendance.adminweb.model;

public record WorkRequestRow(
    Long id,
    String employeeCode,
    String employeeName,
    String workplaceName,
    String requestType,
    String requestTypeLabel,
    String status,
    String statusLabel,
    String requestDate,
    String halfDayType,
    String halfDayTypeLabel,
    Integer earlyLeaveMinutes,
    String reason,
    String reviewedByEmployeeCode,
    String reviewedByName,
    String reviewedAt,
    String reviewNote,
    String createdAt
) {
}
