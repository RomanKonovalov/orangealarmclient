package com.romif.securityalarm.androidclient.feature.dto;

import com.wialon.item.Unit;

import java.io.Serializable;
import java.util.Date;

public class UnitDto implements Serializable {

    private Long id;
    private String name;
    private double latitude;
    private double longitude;
    private Date time;

    public UnitDto() {
    }

    public UnitDto(Unit unit) {
        id = unit.getId();
        name = unit.getName();
        latitude = unit.getPosition().getLatitude();
        longitude = unit.getPosition().getLongitude();
        this.time = new Date(unit.getPosition().getTime() * 1000);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    @Override
    public String toString() {
        return name;
    }
}
