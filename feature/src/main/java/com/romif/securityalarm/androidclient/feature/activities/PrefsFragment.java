package com.romif.securityalarm.androidclient.feature.activities;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

import com.romif.securityalarm.androidclient.feature.R;

public class PrefsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.pref, rootKey);
    }
}
