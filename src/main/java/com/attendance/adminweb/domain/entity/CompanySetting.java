package com.attendance.adminweb.domain.entity;

import com.attendance.adminweb.domain.entity.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;

@Entity
@Table(name = "company_settings")
public class CompanySetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(nullable = false)
    private Integer allowedRadiusMeters;

    @Column(name = "late_after_time", nullable = false)
    private LocalTime lateAfterTime;

    @Column(name = "notice_message", length = 1000)
    private String noticeMessage;

    @Column(name = "enforce_single_device_login", nullable = false, columnDefinition = "boolean default true")
    private boolean enforceSingleDeviceLogin = true;

    protected CompanySetting() {
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public Integer getAllowedRadiusMeters() {
        return allowedRadiusMeters;
    }

    public LocalTime getLateAfterTime() {
        return lateAfterTime;
    }

    public String getNoticeMessage() {
        return noticeMessage;
    }

    public boolean isEnforceSingleDeviceLogin() {
        return enforceSingleDeviceLogin;
    }

    public void updateAllowedRadiusMeters(Integer allowedRadiusMeters) {
        this.allowedRadiusMeters = allowedRadiusMeters;
    }

    public void updateNoticeMessage(String noticeMessage) {
        this.noticeMessage = noticeMessage;
    }

    public void updateEnforceSingleDeviceLogin(boolean enforceSingleDeviceLogin) {
        this.enforceSingleDeviceLogin = enforceSingleDeviceLogin;
    }
}
