package com.attendance.adminweb.model;

import java.util.List;

public record DashboardData(
    DashboardSummary summary,
    List<AttendanceRow> attendances
) {
}
