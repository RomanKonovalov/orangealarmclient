package com.romif.securityalarm.androidclient.feature.dto;

import com.wialon.item.Unit;

import java.io.Serializable;
import java.util.Date;

public class UnitDto implements Serializable {

    private Long id;
    private String name;
    private double unitLatitude;
    private double unitLongitude;
    private double geozoneLatitude;
    private double geozoneLongitude;
    private Date time;
    private boolean alarmEnabled;

    public UnitDto() {
    }

    public UnitDto(Unit unit) {
        id = unit.getId();
        name = unit.getName();
        unitLatitude = unit.getPosition().getLatitude();
        unitLongitude = unit.getPosition().getLongitude();
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

    public double getUnitLatitude() {
        return unitLatitude;
    }

    public void setUnitLatitude(double unitLatitude) {
        this.unitLatitude = unitLatitude;
    }

    public double getUnitLongitude() {
        return unitLongitude;
    }

    public void setUnitLongitude(double unitLongitude) {
        this.unitLongitude = unitLongitude;
    }

    public Date getTime() {
        return time;
    }

    public void setTime(Date time) {
        this.time = time;
    }

    public boolean isAlarmEnabled() {
        return alarmEnabled;
    }

    public void setAlarmEnabled(boolean alarmEnabled) {
        this.alarmEnabled = alarmEnabled;
    }

    public double getGeozoneLatitude() {
        return geozoneLatitude;
    }

    public void setGeozoneLatitude(double geozoneLatitude) {
        this.geozoneLatitude = geozoneLatitude;
    }

    public double getGeozoneLongitude() {
        return geozoneLongitude;
    }

    public void setGeozoneLongitude(double geozoneLongitude) {
        this.geozoneLongitude = geozoneLongitude;
    }

    @Override
    public String toString() {
        return name;
    }
}
