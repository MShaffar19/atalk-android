/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.*;

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.OSGiPreferenceActivity;

/**
 * Provisioning preferences Settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ProvisioningSettings extends OSGiPreferenceActivity
{
    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new MyPreferenceFragment()).commit();
    }

    public static class MyPreferenceFragment extends PreferenceFragmentCompat
            implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        /**
         * Used preference keys
         */
        private final static String P_KEY_PROVISIONING_METHOD = aTalkApp.getResString(R.string.pref_key_provisioning_METHOD);

        private final static String P_KEY_USER = aTalkApp.getResString(R.string.pref_key_provisioning_USERNAME);

        private final static String P_KEY_PASS = aTalkApp.getResString(R.string.pref_key_provisioning_PASSWORD);

        private final static String P_KEY_FORGET_PASS = aTalkApp.getResString(R.string.pref_key_provisioning_FORGET_PASSWORD);

        private final static String P_KEY_UUID = aTalkApp.getResString(R.string.pref_key_provisioning_UUID);

        private final static String P_KEY_URL = aTalkApp.getResString(R.string.pref_key_provisioning_URL);

        /**
         * Username edit text
         */
        private EditTextPreference usernamePreference;

        /**
         * Password edit text
         */
        private EditTextPreference passwordPreference;

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
        {
            setPreferencesFromResource(R.xml.provisioning_preferences, rootKey);

            // Load UUID
            EditTextPreference edtPref = findPreference(P_KEY_UUID);
            edtPref.setText(AndroidGUIActivator.getConfigurationService().getString(edtPref.getKey()));

            CredentialsStorageService cSS = AndroidGUIActivator.getCredentialsStorageService();
            String password = cSS.loadPassword(P_KEY_PASS);

            Preference forgetPass = findPreference(P_KEY_FORGET_PASS);
            ConfigurationService config = AndroidGUIActivator.getConfigurationService();
            // Enable clear credentials button if password exists
            if (StringUtils.isNotEmpty(password)) {
                forgetPass.setEnabled(true);
            }
            // Forget password action handler
            forgetPass.setOnPreferenceClickListener(preference -> {
                askForgetPassword();
                return false;
            });

            // Initialize username and password fields
            usernamePreference = findPreference(P_KEY_USER);
            usernamePreference.setText(config.getString(P_KEY_USER));

            passwordPreference = findPreference(P_KEY_PASS);
            passwordPreference.setText(password);
        }

        /**
         * Asks the user for confirmation of password clearing and eventually clears it.
         */
        private void askForgetPassword()
        {
            AlertDialog.Builder askForget = new AlertDialog.Builder(getContext());
            askForget.setTitle(R.string.service_gui_REMOVE)
                    .setMessage(R.string.plugin_provisioning_REMOVE_CREDENTIALS_MESSAGE)
                    .setPositiveButton(R.string.service_gui_YES, (dialog, which) -> {
                        AndroidGUIActivator.getCredentialsStorageService().removePassword(P_KEY_PASS);
                        AndroidGUIActivator.getConfigurationService().removeProperty(P_KEY_USER);

                        usernamePreference.setText("");
                        passwordPreference.setText("");
                    }).setNegativeButton(R.string.service_gui_NO, (dialog, which) -> dialog.dismiss()).show();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onResume()
        {
            super.onResume();
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onPause()
        {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
            super.onPause();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key)
        {
            if (key.equals(P_KEY_PROVISIONING_METHOD)) {
                if ("NONE".equals(sharedPreferences.getString(P_KEY_PROVISIONING_METHOD, null))) {
                    AndroidGUIActivator.getConfigurationService().setProperty(P_KEY_URL, null);
                }
            }
        }
    }
}
