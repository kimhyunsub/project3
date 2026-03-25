package com.attendance.adminweb.model;

public record WorkplaceLocationView(
        Long id,
        String name,
        double latitude,
        double longitude,
        int allowedRadiusMeters
) {
}
