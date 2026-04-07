package com.attendance.adminweb.model;

public record CompanyLocationView(
        String companyName,
        double latitude,
        double longitude,
        int allowedRadiusMeters,
        String lateAfterTime,
        String noticeMessage,
        String mobileSkinKey,
        boolean enforceSingleDeviceLogin
) {
}
