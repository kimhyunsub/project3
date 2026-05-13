package com.attendance.adminweb.model;

import jakarta.validation.constraints.NotBlank;

public class WorkRequestCreateForm {

    @NotBlank(message = "사번을 입력해 주세요.")
    private String employeeCode;

    @NotBlank(message = "신청 유형을 선택해 주세요.")
    private String requestType;

    @NotBlank(message = "날짜를 입력해 주세요.")
    private String requestDate;

    private String halfDayType;
    private Integer earlyLeaveMinutes;
    private String reason;

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getRequestDate() {
        return requestDate;
    }

    public void setRequestDate(String requestDate) {
        this.requestDate = requestDate;
    }

    public String getHalfDayType() {
        return halfDayType;
    }

    public void setHalfDayType(String halfDayType) {
        this.halfDayType = halfDayType;
    }

    public Integer getEarlyLeaveMinutes() {
        return earlyLeaveMinutes;
    }

    public void setEarlyLeaveMinutes(Integer earlyLeaveMinutes) {
        this.earlyLeaveMinutes = earlyLeaveMinutes;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
