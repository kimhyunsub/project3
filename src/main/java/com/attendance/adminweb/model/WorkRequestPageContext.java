package com.attendance.adminweb.model;

public record WorkRequestPageContext(
    boolean workplaceScopedAdmin,
    boolean canAccessSqlConsole,
    boolean approvalRequired,
    String csrfParameterName,
    String csrfToken
) {
}
