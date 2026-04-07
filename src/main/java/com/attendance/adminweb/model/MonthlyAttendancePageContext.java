package com.attendance.adminweb.model;

import java.util.List;

public record MonthlyAttendancePageContext(
        int year,
        int month,
        String selectedEmployeeCode,
        Long selectedWorkplaceId,
        boolean workplaceScopedAdmin,
        boolean canAccessSqlConsole,
        String csrfParameterName,
        String csrfToken,
        List<WorkplaceOption> workplaceOptions
) {
}
