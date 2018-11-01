package com.romif.securityalarm.androidclient.feature;


import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.romif.securityalarm.androidclient.feature.dto.UnitDto;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ContentActivity extends AppCompatActivity {

    private static final String TAG = "ContentActivity";
    private static final int REQUEST_ENABLE_BT = 111;
    private MapFragment mapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.AppTheme);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        Serializable serializable = getIntent().getExtras() != null ? getIntent().getExtras().getSerializable("com.romif.securityalarm.androidclient.Units") : null;
        if (serializable != null) {
            SharedPreferences.Editor sharedPref = android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit();
            List<UnitDto> unitsList = (List<UnitDto>) serializable;
            sharedPref.putStringSet(SettingsConstants.UNIT_NAMES, new HashSet<>(unitsList.stream().map(UnitDto::getName).collect(Collectors.toSet())));
            sharedPref.putStringSet(SettingsConstants.UNIT_IDS, new HashSet<>(unitsList.stream().map(unitDto -> unitDto.getId().toString()).collect(Collectors.toSet())));
            sharedPref.apply();
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate our menu from the resources by using the menu inflater.
        getMenuInflater().inflate(R.menu.main, menu);

        // It is also possible add items here. Use a generated id from
        // resources (ids.xml) to ensure that all menu ids are distinct.
        //MenuItem locationItem = menu.add(0, R.id.menu_location, 0, R.string.menu_location);
        //locationItem.setIcon(R.drawable.ic_action_location);

        // Need to use MenuItemCompat methods to call any action item related methods
        //MenuItemCompat.setShowAsAction(locationItem, MenuItem.SHOW_AS_ACTION_IF_ROOM);

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Serializable serializable = getIntent().getExtras() != null ? getIntent().getExtras().getSerializable("com.romif.securityalarm.androidclient.Units") : null;
        if (serializable == null || ((List<UnitDto>) serializable).stream().anyMatch(unitDto -> new Date(unitDto.getTime().getTime() + 2 * DateUtils.MINUTE_IN_MILLIS).before(new Date()))) {
            refreshState();
        } else {
            setMap();
        }


    }

    private void refreshState() {
        SharedPreferences sharedPref = android.preference.PreferenceManager.getDefaultSharedPreferences(this);
        String wialonHost = sharedPref.getString(SettingsConstants.WIALON_HOST_PREFERENCE, getString(R.string.wialon_host));
        if (Objects.equals(wialonHost, "")) {
            Toast.makeText(this, "Orange host is not set", Toast.LENGTH_SHORT).show();
            return;
        }
        mapFragment.getMapAsync(GoogleMap::clear);
        findViewById(R.id.mapProgressBar).setVisibility(View.VISIBLE);
        final View refreshButton = findViewById(R.id.menu_refresh);
        if (refreshButton != null) {
            refreshButton.setEnabled(false);
        }
        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                findViewById(R.id.mapProgressBar).setVisibility(View.GONE);
                if (refreshButton != null) {
                    refreshButton.setEnabled(true);
                }
                if (msg.what > 0) {
                    setMap();
                }
            }
        };
        SecurityService.getCredential(this)
                .thenCompose(credential -> WialonService.login(wialonHost, credential.getId(), credential.getPassword()))
                .thenCompose(result -> WialonService.getUnits())
                .thenAccept(units -> {
                    Log.d(TAG, "login and units are retrieved");
                    getIntent().putExtra("com.romif.securityalarm.androidclient.Units", new ArrayList<>(units));
                    SharedPreferences.Editor sharedPrefEditor = android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit();
                    sharedPrefEditor.putStringSet(SettingsConstants.UNIT_NAMES, new HashSet<>(units.stream().map(UnitDto::getName).collect(Collectors.toSet())));
                    sharedPrefEditor.putStringSet(SettingsConstants.UNIT_IDS, new HashSet<>(units.stream().map(unitDto -> unitDto.getId().toString()).collect(Collectors.toSet())));
                    sharedPrefEditor.apply();
                    handler.sendEmptyMessage(1);
                })
                .thenCompose(result -> WialonService.logout())
                .handle((s, throwable) -> {
                    handler.sendEmptyMessage(-1);
                    return s;
                });
    }

    @Override
    protected void onStart() {
        super.onStart();

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {

        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {

            if (resultCode != RESULT_OK) {
                Toast.makeText(this, "Bluetooth is not available. App will not work properly. Please turn it on", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setMap() {
        Serializable serializable = getIntent().getExtras() != null ? getIntent().getExtras().getSerializable("com.romif.securityalarm.androidclient.Units") : null;
        mapFragment.getMapAsync(googleMap -> {

            if (serializable != null) {
                ((List<UnitDto>) serializable).forEach(unitDto -> {
                    googleMap.addMarker(new MarkerOptions()
                            .position(new LatLng(unitDto.getLatitude(), unitDto.getLongitude()))
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.car_icon))
                            .snippet(unitDto.getName())
                            .title(unitDto.getName()));
                    googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(unitDto.getLatitude(), unitDto.getLongitude())));
                });

            }
        });
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.menu_refresh) {
            refreshState();
            return true;
        } else if (i == R.id.menu_settings) {// Here we would open up our settings activity
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        } else if (i == R.id.menu_logout) {
            SecurityService.logout(this);
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
