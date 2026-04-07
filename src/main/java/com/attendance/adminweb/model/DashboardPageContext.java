package com.attendance.adminweb.model;

import java.util.List;

public record DashboardPageContext(
        String selectedFilter,
        Long selectedWorkplaceId,
        boolean workplaceScopedAdmin,
        boolean canAccessSqlConsole,
        String csrfParameterName,
        String csrfToken,
        List<WorkplaceOption> workplaceOptions
) {
}
