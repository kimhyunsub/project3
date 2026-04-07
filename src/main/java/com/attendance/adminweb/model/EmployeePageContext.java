package com.attendance.adminweb.model;

import java.util.List;

public record EmployeePageContext(
        int currentPage,
        boolean showDeleted,
        Long selectedWorkplaceId,
        Long editId,
        boolean createMode,
        boolean inviteMode,
        boolean workplaceScopedAdmin,
        boolean canAccessSqlConsole,
        boolean canManageAdminRoles,
        String csrfParameterName,
        String csrfToken,
        List<WorkplaceOption> workplaceOptions
) {
}
