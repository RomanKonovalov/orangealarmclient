package com.romif.securityalarm.androidclient.feature.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import com.google.android.gms.auth.api.credentials.Credential;
import com.romif.securityalarm.androidclient.feature.R;
import com.romif.securityalarm.androidclient.feature.SettingsConstants;
import com.romif.securityalarm.androidclient.feature.service.WialonService;

public class SignInFragment extends Fragment {

    private static final String TAG = "SignInFragment";
    private TextInputLayout mUsernameTextInputLayout;
    private EditText mUsernameEditText;
    private TextInputLayout mPasswordTextInputLayout;
    private EditText mPasswordEditText;
    private Button mSignInButton;
    private Button mClearButton;
    private ProgressBar mSignInProgressBar;

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState) {
        View view = layoutInflater.inflate(R.layout.fragment_sign_in, container, false);
        mUsernameTextInputLayout = view.findViewById(R.id.usernameTextInputLayout);
        mPasswordTextInputLayout = view.findViewById(R.id.passwordTextInputLayout);

        mUsernameEditText = view.findViewById(R.id.usernameEditText);
        mPasswordEditText = view.findViewById(R.id.passwordEditText);

        mSignInButton = view.findViewById(R.id.signInButton);
        mSignInButton.setOnClickListener(view1 -> {
            setSignEnabled(false);
            String username = mUsernameTextInputLayout.getEditText().getText().toString();
            String password = mPasswordTextInputLayout.getEditText().getText().toString();

            final Handler handler = new Handler() {
                public void handleMessage(Message msg) {
                    if (msg.what < 0) {
                        setSignEnabled(true);
                        mUsernameEditText.setError(getString(R.string.credentials_invalid));
                        mPasswordEditText.setError(getString(R.string.credentials_invalid));
                    }
                }
            };

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getContext());
            String notificationName = sharedPref.getString(SettingsConstants.NOTIFICATION_NAME_PREFERENCE, getString(R.string.notification_name));
            long unitId = Long.parseLong(sharedPref.getString(SettingsConstants.UNIT_PREFERENCE, "0"));
            boolean useSmartLock = sharedPref.getBoolean(SettingsConstants.USE_SMART_LOCK_PREFERENCE, true);
            String wialonHost = sharedPref.getString(SettingsConstants.WIALON_HOST_PREFERENCE, getString(R.string.wialon_host));
            String geozoneName = sharedPref.getString(SettingsConstants.GEOZONE_NAME_PREFERENCE, getString(R.string.geozone_name));
            WialonService.getToken(wialonHost, username, password, useSmartLock)
                    .thenApply(token -> {
                        if (!useSmartLock) {
                            SharedPreferences.Editor sharedPrefEditor = android.preference.PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
                            sharedPrefEditor.putString(SettingsConstants.TOKEN, token);
                            sharedPrefEditor.apply();
                        }
                        return token;
                    })
                    .thenCompose(token -> WialonService.login(wialonHost, token))
                    .thenCompose(result -> WialonService.getUnitDtos(notificationName, unitId, geozoneName))
                    .thenAccept(units -> {
                        Credential credential = new Credential.Builder(username).setPassword(password).build();
                        ((MainActivity) getActivity()).saveCredential(credential, units);
                        handler.sendEmptyMessage(1);
                    })
                    .thenCompose(result -> WialonService.logout())
                    .handle((result, exception) -> {
                        if (exception == null) {
                            return result;
                        }
                        Log.e(TAG, "Credentials are invalid. Username or password are incorrect.");
                        handler.sendEmptyMessage(-1);
                        return result;
                    });
        });

        mClearButton = view.findViewById(R.id.clearButton);
        mClearButton.setOnClickListener(view12 -> {
            mUsernameTextInputLayout.getEditText().setText("");
            mPasswordTextInputLayout.getEditText().setText("");
        });

        mSignInProgressBar = view.findViewById(R.id.signInProgress);
        mSignInProgressBar.setVisibility(ProgressBar.INVISIBLE);

        return view;
    }

    public void onResume() {
        super.onResume();
        /*if (((MainActivity) getActivity()).isResolving() || ((MainActivity) getActivity()).isRequesting()) {
            setSignEnabled(false);
        } else {
            setSignEnabled(true);
        }*/
    }

    /**
     * Enable or disable Sign In form.
     *
     * @param enable Enable form when true, disable when false.
     */
    protected void setSignEnabled(boolean enable) {
        mSignInButton.setEnabled(enable);
        mClearButton.setEnabled(enable);
        mUsernameEditText.setEnabled(enable);
        mPasswordEditText.setEnabled(enable);
        if (!enable) {
            mSignInProgressBar.setVisibility(ProgressBar.VISIBLE);
        } else {
            mSignInProgressBar.setVisibility(ProgressBar.INVISIBLE);
        }
    }


}
