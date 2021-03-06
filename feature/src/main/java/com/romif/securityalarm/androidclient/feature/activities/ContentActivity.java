package com.romif.securityalarm.androidclient.feature.activities;


import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.github.zagum.switchicon.SwitchIconView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.romif.securityalarm.androidclient.feature.AlarmState;
import com.romif.securityalarm.androidclient.feature.R;
import com.romif.securityalarm.androidclient.feature.SettingsConstants;
import com.romif.securityalarm.androidclient.feature.dto.UnitDto;
import com.romif.securityalarm.androidclient.feature.service.NotificationService;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java9.util.concurrent.CompletableFuture;
import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

public class ContentActivity extends AppCompatActivity {

    private static final String TAG = "ContentActivity";
    private MapFragment mapFragment;
    private SharedPreferences sharedPref;
    private SwitchIconView alarmToggle;
    private Handler handler;
    private MenuItem refreshButton;
    private View progressBar;
    private DrawerLayout mDrawerLayout;
    private FirebaseAnalytics mFirebaseAnalytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content);

        sharedPref = android.preference.PreferenceManager.getDefaultSharedPreferences(this);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar actionbar = getSupportActionBar();
        actionbar.setDisplayHomeAsUpEnabled(true);
        actionbar.setHomeAsUpIndicator(R.drawable.ic_menu);

        mDrawerLayout = findViewById(R.id.drawer_layout);

        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(
                menuItem -> {
                    // set item as selected to persist highlight
                    menuItem.setChecked(false);
                    // close drawer when item is tapped
                    mDrawerLayout.closeDrawers();

                    int i = menuItem.getItemId();

                    if (i == R.id.menu_settings) {// Here we would open up our settings activity
                        log(Log.INFO, "menu_settings", "begin");
                        Intent intent = new Intent(this, SettingsActivity.class);
                        startActivity(intent);
                        return false;
                    } else if (i == R.id.menu_logout) {
                        log(Log.INFO, "menu_logout", "begin");
                        SecurityService.logout(this);
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        return true;
                    }

                    return true;
                });

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        SharedPreferences.Editor sharedPrefEditor = android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit();
        sharedPrefEditor.putStringSet(SettingsConstants.UNIT_NAMES, new HashSet<>(StreamSupport.stream(getUnits()).map(UnitDto::getName).collect(Collectors.toSet())));
        sharedPrefEditor.putStringSet(SettingsConstants.UNIT_IDS, new HashSet<>(StreamSupport.stream(getUnits()).map(unitDto -> unitDto.getId().toString()).collect(Collectors.toSet())));
        sharedPrefEditor.apply();

        progressBar = findViewById(R.id.mapProgressBar);

        handler = new Handler() {
            public void handleMessage(Message msg) {
                progressBar.setVisibility(View.GONE);
                refreshButton.setEnabled(true);
                alarmToggle.setEnabled(true);
                if (msg.what > 0) {
                    log(Log.INFO, "handler", "success");
                    setMap();
                    alarmToggle.setIconEnabled(getUnit() != null && getUnit().isAlarmEnabled(), true);
                    Toast.makeText(ContentActivity.this, getUnit() != null && getUnit().isAlarmEnabled() ? R.string.alarm_acivated : R.string.alarm_deactivated, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ContentActivity.this, R.string.error_refresh_state, Toast.LENGTH_SHORT).show();
                }
            }
        };

        boolean geozoneEscapeNotification = sharedPref.getBoolean(SettingsConstants.GEOZONE_ESCAPE_NOTIFICATION_PREFERENCE, true);
        if (geozoneEscapeNotification && !NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {
            Toast.makeText(this, R.string.error_notification_permissions, Toast.LENGTH_LONG).show();
        }

        boolean btAutoSwitching = sharedPref.getBoolean(SettingsConstants.BT_AUTO_SWITCHING_PREFERENCE, true);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAutoSwitching && !bluetoothAdapter.isEnabled()) {
            Toast.makeText(this, R.string.error_bluetooth_off, Toast.LENGTH_LONG).show();
        }

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction trans = fragmentManager.beginTransaction();
        trans.replace(R.id.adContainer, new AdMobFragment());
        trans.commit();

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate our menu from the resources by using the menu inflater.
        getMenuInflater().inflate(R.menu.main, menu);

        refreshButton = menu.findItem(R.id.menu_refresh);

        MenuItem alarmMenuItem = menu.findItem(R.id.menu_alarm_toggle);

        ConstraintLayout rootView = (ConstraintLayout) alarmMenuItem.getActionView();

        alarmToggle = rootView.findViewById(R.id.switchIconView);

        alarmToggle.setIconEnabled(getUnit() != null && getUnit().isAlarmEnabled());
        alarmToggle.setOnClickListener(v -> {
            log(Log.INFO, "alarm_toggle", "begin");
            boolean isChecked = alarmToggle.isIconEnabled();
            progressBar.setVisibility(View.VISIBLE);
            refreshButton.setEnabled(false);
            alarmToggle.setEnabled(false);

            String wialonHost = sharedPref.getString(SettingsConstants.WIALON_HOST_PREFERENCE, getString(R.string.wialon_host));
            long unitId = Long.parseLong(sharedPref.getString(SettingsConstants.UNIT_PREFERENCE, "0"));
            String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, getString(R.string.notification_name));
            String email = sharedPref.getString(SettingsConstants.EMAIL_PREFERENCE, "");
            String geozoneName = sharedPref.getString(SettingsConstants.GEOZONE_NAME_PREFERENCE, getString(R.string.geozone_name));
            boolean useSmartLock = sharedPref.getBoolean(SettingsConstants.USE_SMART_LOCK_PREFERENCE, true);
            CompletableFuture<String> tokenFuture = useSmartLock ? SecurityService.getCredential(this)
                    .thenCompose(credential -> WialonService.getToken(wialonHost, credential.getId(), credential.getPassword(), true)) :
                    CompletableFuture.completedFuture(sharedPref.getString(SettingsConstants.TOKEN, ""));
            CompletableFuture<String> future = tokenFuture
                    .thenCompose(token -> WialonService.login(wialonHost, token));
            CompletableFuture<Boolean> futureUpdateNotification;
            if (!isChecked) {
                futureUpdateNotification = future
                        .thenCompose(result -> WialonService.getGeozone(false))
                        .thenCompose(geozone -> {
                            String zoneId = StreamSupport.stream(geozone.getZl().entrySet())
                                    .filter(e -> geozoneName.equals(e.getValue().getN()))
                                    .findFirst()
                                    .map(Map.Entry::getKey)
                                    .orElse(null);

                            int geozoneRadius = Integer.parseInt(sharedPref.getString(SettingsConstants.GEOZONE_RADIUS_PREFERENCE, String.valueOf(getResources().getInteger(R.integer.geozone_radius))));
                            int geozoneColor = -0xFF000000 + sharedPref.getInt(SettingsConstants.GEOZONE_COLOR_PREFERENCE, getColor(R.color.geozone_color));
                            if (zoneId == null) {
                                return WialonService.getLocation(unitId).thenCompose(position -> WialonService.createGeozone(geozone, position, geozoneName, geozoneRadius, geozoneColor));
                            } else {
                                return WialonService.getLocation(unitId).thenCompose(position -> WialonService.updateGeozone(geozone, position, geozoneName, geozoneRadius, geozoneColor));
                            }
                        })
                        .thenCompose(result -> WialonService.getNotification())
                        .thenCompose(notification -> {
                            String notificationId = StreamSupport.stream(notification.getUnf().entrySet())
                                    .filter(e -> notificationName.equals(e.getValue().getN()))
                                    .findFirst()
                                    .map(Map.Entry::getKey)
                                    .orElse(null);
                            if (notificationId == null) {
                                String notificationEmailSubject = sharedPref.getString(SettingsConstants.NOTIFICATION_EMAIL_SUBJECT_PREFERENCE, getString(R.string.notification_email_subject));
                                String notificationPatternText = sharedPref.getString(SettingsConstants.NOTIFICATION_PATTERN_PREFERENCE, getString(R.string.notification_pattern));
                                return WialonService.getGeozone(true).thenCompose(resource -> WialonService.createNotification(resource, unitId, email, geozoneName, notificationName, notificationEmailSubject, notificationPatternText));
                            } else {
                                return WialonService.updateNotification(notification, false, notificationName);
                            }
                        });
            } else {
                futureUpdateNotification = future
                        .thenCompose(result -> WialonService.getNotification())
                        .thenCompose(notification -> WialonService.updateNotification(notification, true, notificationName));
            }
            futureUpdateNotification
                    .thenCompose(result -> WialonService.getUnitDtos(notificationName, unitId, geozoneName))
                    .thenAccept(units -> {
                        Log.d(TAG, "Units are retrieved");
                        log(Log.INFO, "alarm_toggle", "units are retrieved");
                        putUnits(units);
                    })
                    .handle((o, throwable) -> {
                        WialonService.logout();
                        if (throwable != null) {
                            Log.e(TAG, "Unable to change state", throwable);
                            log("alarm_toggle", throwable);
                            handler.sendEmptyMessage(-1);
                        } else {
                            handler.sendEmptyMessage(1);
                        }
                        return o;
                    });
        });

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getUnit() != null && new Date(getUnit().getTime().getTime() + 2 * DateUtils.MINUTE_IN_MILLIS).before(new Date())) {
            refreshState();
        } else {
            setMap();
        }
    }

    private void refreshState() {

        log(Log.INFO, "refresh_state", "begin");

        String wialonHost = sharedPref.getString(SettingsConstants.WIALON_HOST_PREFERENCE, getString(R.string.wialon_host));
        if (Objects.equals(wialonHost, "")) {
            Toast.makeText(this, R.string.error_host_not_set, Toast.LENGTH_SHORT).show();
            log(Log.ERROR, "refresh_state", "error_host_not_set");
            return;
        }
        mapFragment.getMapAsync(GoogleMap::clear);
        progressBar.setVisibility(View.VISIBLE);
        refreshButton.setEnabled(false);
        alarmToggle.setEnabled(false);

        String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, getString(R.string.notification_name));
        long unitId = Long.parseLong(sharedPref.getString(SettingsConstants.UNIT_PREFERENCE, "0"));
        boolean useSmartLock = sharedPref.getBoolean(SettingsConstants.USE_SMART_LOCK_PREFERENCE, true);
        String geozoneName = sharedPref.getString(SettingsConstants.GEOZONE_NAME_PREFERENCE, getString(R.string.geozone_name));
        CompletableFuture<String> tokenFuture = useSmartLock ? SecurityService.getCredential(this)
                .thenCompose(credential -> WialonService.getToken(wialonHost, credential.getId(), credential.getPassword(), true)) :
                CompletableFuture.completedFuture(sharedPref.getString(SettingsConstants.TOKEN, ""));
        tokenFuture
                .thenCompose(token -> WialonService.login(wialonHost, token))
                .thenCompose(result -> WialonService.getUnitDtos(notificationName, unitId, geozoneName))
                .thenAccept(units -> {
                    Log.d(TAG, "units are retrieved");
                    log(Log.INFO, "refresh_state", "units are retrieved");
                    putUnits(units);
                    SharedPreferences.Editor sharedPrefEditor = android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit();
                    sharedPrefEditor.putStringSet(SettingsConstants.UNIT_NAMES, new HashSet<>(StreamSupport.stream(units).map(UnitDto::getName).collect(Collectors.toSet())));
                    sharedPrefEditor.putStringSet(SettingsConstants.UNIT_IDS, new HashSet<>(StreamSupport.stream(units).map(unitDto -> unitDto.getId().toString()).collect(Collectors.toSet())));
                    sharedPrefEditor.apply();
                    handler.sendEmptyMessage(1);
                })
                .thenCompose(result -> WialonService.logout())
                .handle((s, throwable) -> {
                    if (throwable != null) {
                        Log.e(TAG, "Unable to refresh state", throwable);
                        log("refresh_state", throwable);
                        handler.sendEmptyMessage(-1);
                    }
                    return s;
                });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void setMap() {
        mapFragment.getMapAsync(googleMap -> {
            googleMap.clear();
            UnitDto unitDto = getUnit();
            if (unitDto != null) {
                googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(unitDto.getUnitLatitude(), unitDto.getUnitLongitude()))
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.ic_car_location))
                        .snippet(unitDto.getName())
                        .title(unitDto.getName()));
                if (unitDto.isAlarmEnabled()) {
                    int geozoneRadius = Integer.parseInt(sharedPref.getString(SettingsConstants.GEOZONE_RADIUS_PREFERENCE, String.valueOf(getResources().getInteger(R.integer.geozone_radius))));
                    int geozoneColor = sharedPref.getInt(SettingsConstants.GEOZONE_COLOR_PREFERENCE, getColor(R.color.geozone_color));
                    googleMap.addCircle(new CircleOptions()
                            .center(new LatLng(unitDto.getGeozoneLatitude(), unitDto.getGeozoneLongitude()))
                            .radius(geozoneRadius)
                            .strokeColor(geozoneColor)
                            .fillColor(-0x80000000 + geozoneColor));
                }
                googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(unitDto.getUnitLatitude(), unitDto.getUnitLongitude())));
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.menu_refresh) {
            refreshState();
            NotificationService.notify(getApplicationContext(), AlarmState.ZONE_ESCAPE, 1);
            return true;
        } else if (i == android.R.id.home) {// Here we would open up our settings activity
            mDrawerLayout.openDrawer(GravityCompat.START);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void putUnits(ArrayList<UnitDto> units) {
        getIntent().putExtra("com.romif.securityalarm.androidclient.Units", new ArrayList<>(units));
    }

    private List<UnitDto> getUnits() {
        return getIntent().getExtras() != null ? (List<UnitDto>) getIntent().getExtras().getSerializable("com.romif.securityalarm.androidclient.Units") : Collections.emptyList();
    }

    @Nullable
    private UnitDto getUnit() {
        long unitId = Long.parseLong(sharedPref.getString(SettingsConstants.UNIT_PREFERENCE, "0"));
        return StreamSupport.stream(getUnits())
                .filter(u -> u.getId() == unitId)
                .findFirst()
                .orElse(null);
    }

    private void log(int priority, String event, String status) {
        Bundle params = new Bundle();
        params.putString("activity", TAG);
        params.putString("status", status);
        mFirebaseAnalytics.logEvent(event, params);
        Crashlytics.log(priority, TAG, event + ": status " + status);
    }

    private void log(String event, Throwable throwable) {
        Bundle params = new Bundle();
        params.putString("error", throwable.getLocalizedMessage());
        mFirebaseAnalytics.logEvent(event, params);
        Crashlytics.logException(throwable);
    }

}
