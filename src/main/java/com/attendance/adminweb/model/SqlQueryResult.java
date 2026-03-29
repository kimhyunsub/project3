package com.attendance.adminweb.model;

import java.util.List;

public record SqlQueryResult(
        List<String> columns,
        List<List<String>> rows,
        int rowLimit,
        boolean truncated
) {
}
