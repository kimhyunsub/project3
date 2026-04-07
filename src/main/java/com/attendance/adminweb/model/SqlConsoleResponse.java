package com.attendance.adminweb.model;

import java.util.List;

public record SqlConsoleResponse(
    String queryText,
    SqlQueryResult queryResult,
    String errorMessage,
    List<SqlSnippet> sqlSnippets
) {
}
