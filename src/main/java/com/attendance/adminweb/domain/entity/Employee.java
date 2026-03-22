package com.attendance.adminweb.domain.entity;

import com.attendance.adminweb.domain.entity.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "employees")
public class Employee extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String employeeCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "work_start_time")
    private LocalTime workStartTime;

    @Column(name = "work_end_time")
    private LocalTime workEndTime;

    @Column(name = "registered_device_id", length = 120)
    private String registeredDeviceId;

    @Column(name = "registered_device_name", length = 200)
    private String registeredDeviceName;

    @Column(name = "device_registered_at")
    private LocalDateTime deviceRegisteredAt;

    protected Employee() {
    }

    public Employee(String employeeCode, String name, String password, EmployeeRole role, Company company) {
        this(employeeCode, name, password, role, company, null, null);
    }

    public Employee(String employeeCode,
                    String name,
                    String password,
                    EmployeeRole role,
                    Company company,
                    LocalTime workStartTime,
                    LocalTime workEndTime) {
        this.employeeCode = employeeCode;
        this.name = name;
        this.password = password;
        this.role = role;
        this.company = company;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
    }

    public Long getId() {
        return id;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public String getName() {
        return name;
    }

    public String getPassword() {
        return password;
    }

    public EmployeeRole getRole() {
        return role;
    }

    public Company getCompany() {
        return company;
    }

    public LocalTime getWorkStartTime() {
        return workStartTime;
    }

    public LocalTime getWorkEndTime() {
        return workEndTime;
    }

    public String getRegisteredDeviceId() {
        return registeredDeviceId;
    }

    public String getRegisteredDeviceName() {
        return registeredDeviceName;
    }

    public LocalDateTime getDeviceRegisteredAt() {
        return deviceRegisteredAt;
    }

    public void updateProfile(String employeeCode, String name, EmployeeRole role, LocalTime workStartTime, LocalTime workEndTime) {
        this.employeeCode = employeeCode;
        this.name = name;
        this.role = role;
        this.workStartTime = workStartTime;
        this.workEndTime = workEndTime;
    }

    public void updatePassword(String password) {
        this.password = password;
    }

    public void resetRegisteredDevice() {
        this.registeredDeviceId = null;
        this.registeredDeviceName = null;
        this.deviceRegisteredAt = null;
    }
}
