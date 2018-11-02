/**
 * Copyright Google Inc. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.romif.securityalarm.androidclient.feature.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
import com.romif.securityalarm.androidclient.feature.service.WialonService;

import java.io.IOException;
import java.util.Properties;

public class SignInFragment extends Fragment {

    private static final String TAG = "SignInFragment";
    private TextInputLayout mUsernameTextInputLayout;
    private EditText mUsernameEditText;
    private TextInputLayout mPasswordTextInputLayout;
    private EditText mPasswordEditText;
    private Button mSignInButton;
    private Button mClearButton;
    private ProgressBar mSignInProgressBar;
    private Properties properties = new Properties();

    @Override
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup container, Bundle savedInstanceState) {
        View view = layoutInflater.inflate(R.layout.fragment_sign_in, container, false);
        mUsernameTextInputLayout = view.findViewById(R.id.usernameTextInputLayout);
        mPasswordTextInputLayout = view.findViewById(R.id.passwordTextInputLayout);

        mUsernameEditText = view.findViewById(R.id.usernameEditText);
        mPasswordEditText = view.findViewById(R.id.passwordEditText);

        try {
            properties.load(getContext().getAssets().open("application.properties"));
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

        mSignInButton = view.findViewById(R.id.signInButton);
        mSignInButton.setOnClickListener(view1 -> {
            setSignEnabled(false);
            String username = mUsernameTextInputLayout.getEditText().getText().toString();
            //String password = mPasswordTextInputLayout.getEditText().getText().toString();
            String password = "Rjyjdfkjd1";

            final Handler handler = new Handler() {
                public void handleMessage(Message msg) {
                    if (msg.what < 0) {
                        setSignEnabled(true);
                        mUsernameEditText.setError(getString(R.string.credentials_invalid));
                        mPasswordEditText.setError(getString(R.string.credentials_invalid));
                    }
                }
            };


            StringBuilder loginBuilder = new StringBuilder();
            WialonService.login((String) properties.get("wialon.host"), username, password)
                    .thenAccept(loginBuilder::append)
                    .thenCompose(result -> WialonService.getUnits())
                    .thenAccept(units -> {
                        Credential credential = new Credential.Builder(username).setPassword(password).build();
                        ((MainActivity) getActivity()).saveCredential(credential, loginBuilder.toString(), units);
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
