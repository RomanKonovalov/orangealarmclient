package com.romif.securityalarm.androidclient.feature.dto;

import java.io.Serializable;

public class GeozoneDto implements Serializable {

    private Long id;
    private String name;
    private double latitude;
    private double longitude;
    private boolean enabled;
}
