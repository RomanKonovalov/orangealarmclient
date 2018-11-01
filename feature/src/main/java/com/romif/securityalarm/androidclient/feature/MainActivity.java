package com.romif.securityalarm.androidclient.feature;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.google.android.gms.auth.api.credentials.Credential;
import com.google.android.gms.auth.api.credentials.Credentials;
import com.google.android.gms.auth.api.credentials.CredentialsClient;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResolvableApiException;
import com.romif.securityalarm.androidclient.feature.dto.UnitDto;
import com.romif.securityalarm.androidclient.feature.service.SecurityService;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

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
    private Handler mHandler;
    private CredentialsClient mCredentialsClient;
    private Properties properties = new Properties();
    private AlertDialog enableNotificationListenerAlertDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            properties.load(getBaseContext().getAssets().open("application.properties"));
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        mCredentialsClient = Credentials.getClient(this);


        if (savedInstanceState != null) {
            mIsResolving = savedInstanceState.getBoolean(IS_RESOLVING);
            mIsRequesting = savedInstanceState.getBoolean(IS_REQUESTING);
        }

        if (!NotificationManagerCompat.getEnabledListenerPackages(this).contains(getPackageName())) {        //ask for permission
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        }

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
     * Set the appropriate fragment given the state of the Activity and the Intent used to start it.
     * If the intent is a launcher intent the Splash Fragment is shown otherwise the SignIn Fragment is shown.
     *
     */
    private void setFragment() {
        Fragment fragment;
        String tag;

        fragment = new SignInFragment();
        tag = SIGN_IN_TAG;

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
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

    /**
     * Get the tag of the currently set Fragment.
     *
     * @return Tag of currently set Fragment, or null if no fragment is set.
     */
    private String getCurrentFragmentTag() {
        List<Fragment> fragmentList = getSupportFragmentManager().getFragments();
        if (fragmentList == null || fragmentList.size() == 0) {
            return null;
        }
        Fragment fragment = fragmentList.get(0);
        if (fragment != null) {
            return fragment.getTag();
        }
        return null;
    }

    protected boolean isResolving() {
        return mIsResolving;
    }

    protected boolean isRequesting() {
        return mIsRequesting;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

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
                        mIsRequesting = false;
                        resolveResult(rae, RC_READ);
                    } else if (e instanceof ApiException) {

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

    protected void saveCredential(Credential credential, String login, ArrayList<UnitDto> units) {

        mCredentialsClient.save(credential).addOnCompleteListener(
                task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Credential saved");
                        Intent intent = new Intent(MainActivity.this, ContentActivity.class);
                        intent.putExtra("login", login);
                        intent.putExtra("com.romif.securityalarm.androidclient.Units", units);
                        startActivity(intent);
                        finish();
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
                        // Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Attempt to save credential failed ");
                    }
                });

    }

    protected void deleteCredential(Credential credential) {
        mCredentialsClient.delete(credential).addOnCompleteListener(
                task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Credential deleted");
                        setSignInEnabled(true);
                        setFragment();
                    }
                });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult:" + requestCode + ":" + resultCode + ":" +
                data);

        if (requestCode == RC_READ) {
            if (resultCode == RESULT_OK) {
                Credential credential = data.getParcelableExtra(Credential.EXTRA_KEY);
                processRetrievedCredential(credential);
            } else {
                Log.e(TAG, "Credential Read: NOT OK");
                setFragment();
                mIsResolving = false;
                //Toast.makeText(this, "Credential Read Failed", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == RC_SAVE) {
            Log.d(TAG, "Result code: " + resultCode);
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Credential Save: OK");
            } else {
                Log.e(TAG, "Credential Save Failed");
            }
            startActivity(new Intent(this, ContentActivity.class));
            finish();
            mIsResolving = false;
        }

    }


    private CompletableFuture<String> processRetrievedCredential(Credential credential) {
        Log.d(TAG, "Process Retrieved Credential");
        StringBuilder loginBuilder = new StringBuilder();
        return WialonService.login((String) properties.get("wialon.host"), credential.getId(), credential.getPassword())
                .thenAccept(loginBuilder::append)
                .thenCompose(result -> WialonService.getUnits())
                .thenAccept(units -> {
                    Log.d(TAG, "login and units are retrieved");
                    Intent intent = new Intent(MainActivity.this, ContentActivity.class);
                    intent.putExtra("login", loginBuilder.toString());
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
                    }

                    return result;
                });
    }

}
