package com.attendance.adminweb.model;

import java.util.List;

public record EmployeePage(
        List<EmployeeRow> employees,
        int currentPage,
        int totalPages,
        long totalCount,
        int pageSize,
        boolean hasPrevious,
        boolean hasNext
) {
}
