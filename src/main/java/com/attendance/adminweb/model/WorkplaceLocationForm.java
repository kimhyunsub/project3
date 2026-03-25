package com.attendance.adminweb.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class WorkplaceLocationForm {

    @NotBlank(message = "사업장명을 입력해 주세요.")
    @Size(max = 100, message = "사업장명은 100자 이하여야 합니다.")
    private String name;

    @NotNull(message = "위도를 입력해 주세요.")
    @DecimalMin(value = "-90.0", message = "위도 범위를 확인해 주세요.")
    @DecimalMax(value = "90.0", message = "위도 범위를 확인해 주세요.")
    private Double latitude;

    @NotNull(message = "경도를 입력해 주세요.")
    @DecimalMin(value = "-180.0", message = "경도 범위를 확인해 주세요.")
    @DecimalMax(value = "180.0", message = "경도 범위를 확인해 주세요.")
    private Double longitude;

    @NotNull(message = "허용 반경을 입력해 주세요.")
    @Min(value = 10, message = "허용 반경은 10m 이상이어야 합니다.")
    private Integer allowedRadiusMeters;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getAllowedRadiusMeters() {
        return allowedRadiusMeters;
    }

    public void setAllowedRadiusMeters(Integer allowedRadiusMeters) {
        this.allowedRadiusMeters = allowedRadiusMeters;
    }
}
