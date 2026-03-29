package com.attendance.adminweb.model;

public record SqlSnippet(
        String key,
        String label,
        String description,
        String query
) {
}
