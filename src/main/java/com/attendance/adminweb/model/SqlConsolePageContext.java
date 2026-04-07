package com.attendance.adminweb.model;

import java.util.List;

public record SqlConsolePageContext(
        boolean workplaceScopedAdmin,
        String csrfParameterName,
        String csrfToken,
        List<SqlSnippet> sqlSnippets
) {
}
