package com.attendance.adminweb.model;

import java.util.List;

public record LocationSettingsPageContext(
        Long selectedWorkplaceId,
        boolean workplaceScopedAdmin,
        boolean canAccessSqlConsole,
        String csrfParameterName,
        String csrfToken,
        CompanyLocationView companyLocation,
        WorkplaceLocationView selectedWorkplace,
        List<WorkplaceLocationView> workplaces
) {
}
