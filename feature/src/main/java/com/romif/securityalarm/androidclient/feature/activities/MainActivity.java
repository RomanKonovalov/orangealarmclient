package com.romif.securityalarm.androidclient.feature.activities;

import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.romif.securityalarm.androidclient.feature.R;
import com.romif.securityalarm.androidclient.feature.SettingsConstants;
import com.romif.securityalarm.androidclient.feature.dto.UnitDto;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.util.ArrayList;

import java9.util.concurrent.CompletableFuture;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int RC_SAVE = 1;
    private static final int RC_READ = 3;
    private static final String IS_RESOLVING = "is_resolving";
    private static final String IS_REQUESTING = "is_requesting";
    private static final String SIGN_IN_TAG = "sign_in_fragment";
    // Add mGoogleApiClient and mIsResolving fields here.
    private boolean mIsResolving;
    private boolean mIsRequesting;
    private CredentialsClient mCredentialsClient;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCredentialsClient = Credentials.getClient(this);

        if (savedInstanceState != null) {
            mIsResolving = savedInstanceState.getBoolean(IS_RESOLVING);
            mIsRequesting = savedInstanceState.getBoolean(IS_REQUESTING);
        }

        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        /*if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {        //ask for permission
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        }*/

    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Save the user's current sign in state
        savedInstanceState.putBoolean(IS_RESOLVING, mIsResolving);
        savedInstanceState.putBoolean(IS_REQUESTING, mIsRequesting);

        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    /**
     * If the currently displayed Fragment is the SignIn Fragment then enable or disable the sign in form.
     *
     * @param enable Enable form when true, disable form when false.
     */
    private void setSignInEnabled(boolean enable) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(SIGN_IN_TAG);
        if (fragment != null && fragment.isVisible()) {
            ((SignInFragment) fragment).setSignEnabled(enable);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        if (mIsRequesting || mIsResolving) {
            return;
        }

        setSignInEnabled(false);
        mIsRequesting = true;

        SecurityService.getCredential(this)
                .thenCompose(this::processRetrievedCredential)
                .handle((aVoid, throwable) -> {
                    if (throwable == null || throwable.getCause() == null) {
                        return aVoid;
                    }

                    Throwable e = throwable.getCause();
                    if (e instanceof ResolvableApiException) {
                        Log.d(TAG, "Credential resolving");
                        ResolvableApiException rae = (ResolvableApiException) e;
                        resolveResult(rae, RC_READ);
                        mIsResolving = true;
                        mIsRequesting = false;
                    } else {
                        Log.e(TAG, "Credentials not retrieved", e);
                        mIsRequesting = false;
                        Toast.makeText(this, R.string.error_credentials_retrieve, Toast.LENGTH_SHORT).show();
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new SignInFragment())
                                .commit();
                    }
                    return aVoid;
                });

    }

    private void resolveResult(ResolvableApiException rae, int requestCode) {
        try {
            rae.startResolutionForResult(MainActivity.this, requestCode);
            mIsResolving = true;
        } catch (IntentSender.SendIntentException e) {
            Log.e(TAG, "Failed to send resolution.", e);
        }
    }

    protected void saveCredential(Credential credential, ArrayList<UnitDto> units) {
        boolean useSmartLock = sharedPref.getBoolean(SettingsConstants.USE_SMART_LOCK_PREFERENCE, true);

        if (!useSmartLock) {
            Intent intent = new Intent(MainActivity.this, ContentActivity.class);
            intent.putExtra("com.romif.securityalarm.androidclient.Units", units);
            startActivity(intent);
            finish();
            mIsResolving = false;
            return;
        }

        mCredentialsClient.save(credential).addOnCompleteListener(
                task -> {
                    Intent intent = new Intent(MainActivity.this, ContentActivity.class);
                    intent.putExtra("com.romif.securityalarm.androidclient.Units", units);
                    startActivity(intent);
                    finish();
                    mIsResolving = false;
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Credential saved");
                        return;
                    }

                    Exception e = task.getException();
                    if (e instanceof ResolvableApiException) {
                        // Try to resolve the save request. This will prompt the user if
                        // the credential is new.
                        ResolvableApiException rae = (ResolvableApiException) e;
                        resolveResult(rae, RC_SAVE);
                    } else {
                        // Request has no resolution
                        Toast.makeText(this, R.string.error_save_credentials, Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Attempt to save credential failed", e);
                    }
                });

    }

    protected void deleteCredential(Credential credential) {
        mCredentialsClient.delete(credential).addOnCompleteListener(
                task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Credential deleted");
                        setSignInEnabled(true);
                        setTheme(R.style.AppTheme);
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.fragment_container, new SignInFragment())
                                .commit();
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" + data);

        if (requestCode == RC_READ) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                processRetrievedCredential(credential);
            } else {
                Log.e(TAG, "Credential Read: NOT OK");
                setTheme(R.style.AppTheme);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SignInFragment())
                        .commit();
                //Toast.makeText(this, "Credential Read Failed", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == RC_SAVE) {
            Log.d(TAG, "Result code: " + resultCode);
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Credential Save: OK");
            } else {
                Log.e(TAG, "Credential Save Failed");
                Toast.makeText(this, R.string.error_credentials_retrieve, Toast.LENGTH_SHORT).show();
            }
        }

    }


    private CompletableFuture<String> processRetrievedCredential(Credential credential) {
        Log.d(TAG, "Process Retrieved Credential");
        boolean useSmartLock = sharedPref.getBoolean(SettingsConstants.USE_SMART_LOCK_PREFERENCE, true);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String wialonHost = sharedPref.getString(SettingsConstants.WIALON_HOST_PREFERENCE, getString(R.string.wialon_host));
        final Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                if (msg.what < 0) {
                    Toast.makeText(MainActivity.this, R.string.error_data_retrieve, Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(MainActivity.this::finishAndRemoveTask, 5000);
                }
            }
        };
        Intent intent = new Intent(MainActivity.this, ContentActivity.class);
        String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, getString(R.string.notification_name));
        long unitId = Long.parseLong(sharedPref.getString(SettingsConstants.UNIT_PREFERENCE, "0"));
        String geozoneName = sharedPref.getString(SettingsConstants.GEOZONE_NAME_PREFERENCE, getString(R.string.geozone_name));
        CompletableFuture<String> tokenFuture = useSmartLock ? WialonService.getToken(wialonHost, credential.getId(), credential.getPassword(), true) :
                CompletableFuture.completedFuture(sharedPref.getString(SettingsConstants.TOKEN, ""));
        return tokenFuture
                .thenCompose(token -> WialonService.login(wialonHost, token))
                .thenCompose(result -> WialonService.getUnitDtos(notificationName, unitId, geozoneName))
                .thenAccept(units -> {
                    Log.d(TAG, "units are retrieved");
                    mIsRequesting = false;
                    intent.putExtra("com.romif.securityalarm.androidclient.Units", new ArrayList<>(units));
                    startActivity(intent);
                    finish();
                })
                .thenCompose(result -> WialonService.logout())
                .handle((result, exception) -> {
                    mIsResolving = false;
                    if (exception == null) {
                        return result;
                    }
                    if (exception.getCause() instanceof WialonService.InvalidCredentialsException) {
                        Log.e(TAG, "Credentials are invalid. Username or password are incorrect.");
                        deleteCredential(credential);
                    } else {
                        Log.e(TAG, "Error while processing RetrievedCredential", exception);
                        handler.sendEmptyMessage(-1);
                    }

                    return result;
                });
    }

}
