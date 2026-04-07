package com.attendance.adminweb.model;

import java.util.List;

public record MonthlyAttendanceData(
    MonthlyAttendanceSummary summary,
    List<MonthlyAttendanceEmployeeRow> employees,
    List<MonthlyAttendanceRecordRow> records,
    MonthlyAttendanceEmployeeDetailRow selectedEmployeeDetail
) {
}
