package com.romif.securityalarm.androidclient.feature;

import android.os.Bundle;
import android.support.v7.preference.PreferenceFragmentCompat;

public class PrefsFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.pref, rootKey);
    }
}
