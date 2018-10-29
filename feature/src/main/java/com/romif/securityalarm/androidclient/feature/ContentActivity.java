package com.romif.securityalarm.androidclient.feature;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.romif.securityalarm.androidclient.feature.dto.UnitDto;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ContentActivity extends AppCompatActivity {

    private static final String TAG = "ContentActivity";
    private static final int REQUEST_ENABLE_BT = 111;
    private TextView mStatusTextView;
    private Properties properties = new Properties();
    private MapFragment mapFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);

        try {
            properties.load(getBaseContext().getAssets().open("application.properties"));
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        mStatusTextView = findViewById(R.id.status);

        Button logoutButton = findViewById(R.id.logoutButton);
        logoutButton.setOnClickListener(v -> {
            SecurityService.logout(this);
            Intent intent = new Intent(v.getContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        Button changeCredsButton = findViewById(R.id.changeCredsButton);
        changeCredsButton.setOnClickListener(v -> Crashlytics.logException(new RuntimeException()));

        setLabels();

        setEmails();

        setUnits();
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
                    setUnits();
                }
            }
        };
        SecurityService.getCredential(this)
                .thenCompose(credential -> WialonService.login((String) properties.get("wialon.host"), credential.getId(), credential.getPassword()))
                .thenCompose(result -> WialonService.getUnits())
                .thenAccept(units -> {
                    Log.d(TAG, "login and units are retrieved");
                    getIntent().putExtra("com.romif.securityalarm.androidclient.Units", new ArrayList<>(units));
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
            setDevices();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            setDevices();
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

    private void setDevices() {
        BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();
        List<BluetoothDevice> bluetoothDevices = defaultAdapter.getBondedDevices().stream().sorted(Comparator.comparing(BluetoothDevice::getName)).collect(Collectors.toList());
        AppCompatSpinner devicesSpinner = findViewById(R.id.devices);
        ArrayAdapter<BluetoothDevice> devicesAdapter = new ArrayAdapter<BluetoothDevice>(this, R.layout.layout_spinner_item, bluetoothDevices) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                BluetoothDevice bluetoothDevice = getItem(position);
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                view.setText(bluetoothDevice.getName());
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                BluetoothDevice bluetoothDevice = getItem(position);
                TextView dropDownView = (TextView) super.getDropDownView(position, convertView, parent);
                dropDownView.setText(bluetoothDevice.getName());
                return dropDownView;
            }
        };
        devicesSpinner.setAdapter(devicesAdapter);
        SharedPreferences sharedPref = this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
        String address = sharedPref.getString("address", "");
        int position = IntStream.range(0, bluetoothDevices.size()).filter(i -> bluetoothDevices.get(i).getAddress().equalsIgnoreCase(address)).findFirst().orElse(0);
        devicesSpinner.setSelection(position);
        devicesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                BluetoothDevice bluetoothDevice = (BluetoothDevice) parent.getItemAtPosition(position);
                SharedPreferences sharedPref = ContentActivity.this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("address", bluetoothDevice.getAddress());
                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                SharedPreferences sharedPref = ContentActivity.this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("address", "");
                editor.commit();
            }
        });
    }

    private void setEmails() {
        Pattern gmailPattern = Patterns.EMAIL_ADDRESS; // API level 8+
        Account[] accounts = AccountManager.get(this).getAccounts();
        List<String> emails = Arrays.stream(accounts).map(account -> account.name).filter(name -> gmailPattern.matcher(name).matches()).collect(Collectors.toList());
        AppCompatSpinner emailsSpinner = findViewById(R.id.emails);
        ArrayAdapter<String> emailsAdapter = new ArrayAdapter<String>(this, R.layout.layout_spinner_item, emails);
        emailsSpinner.setAdapter(emailsAdapter);
        //unitsSpinner.setSelection(0);
        emailsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                String email = parent.getItemAtPosition(position).toString();
                SharedPreferences sharedPref = ContentActivity.this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("email", email);
                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                SharedPreferences sharedPref = ContentActivity.this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString("email", "");
                editor.commit();
            }
        });
    }

    private void setLabels() {
        String login = getIntent().getExtras() != null ? getIntent().getExtras().getString("login") : "";
        mStatusTextView.setText(getString(R.string.your_status, login));
    }

    private void setUnits() {
        Serializable serializable = getIntent().getExtras() != null ? getIntent().getExtras().getSerializable("com.romif.securityalarm.androidclient.Units") : null;
        if (serializable != null) {
            List<UnitDto> unitsList = (List<UnitDto>) serializable;
            AppCompatSpinner unitsSpinner = findViewById(R.id.units);
            ArrayAdapter<UnitDto> dataAdapter = new ArrayAdapter<>(this, R.layout.layout_spinner_item, unitsList);
            unitsSpinner.setAdapter(dataAdapter);
            //unitsSpinner.setSelection(0);
            unitsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long l) {
                    UnitDto unit = (UnitDto) parent.getItemAtPosition(position);
                    SharedPreferences sharedPref = ContentActivity.this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("unit", unit.getName());
                    editor.putLong("unitId", unit.getId());
                    editor.commit();
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                    SharedPreferences sharedPref = ContentActivity.this.getSharedPreferences("deviceInfo", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("unitName", "");
                    editor.putLong("unitId", 0);
                    editor.commit();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.menu_refresh) {
            refreshState();
            return true;
        } else if (i == R.id.menu_settings) {// Here we would open up our settings activity
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
