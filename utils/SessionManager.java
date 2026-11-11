package td.teladoum.baobab.utils;

import td.teladoum.baobab.R;

import td.teladoum.baobab.models.*;
import td.teladoum.baobab.adapters.*;
import td.teladoum.baobab.repositories.*;
import td.teladoum.baobab.services.*;
import td.teladoum.baobab.firebase.*;
import td.teladoum.baobab.helpers.*;
import td.teladoum.baobab.notifications.*;
import td.teladoum.baobab.utils.*;
import td.teladoum.baobab.activities.*;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME = "BaobabSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_NAME = "name";
    private static final String KEY_PROFILE_IMAGE = "profileImage";
    private static final String KEY_ADS_ENABLED = "adsEnabled";
    private static final String KEY_PRIVATE_PIN = "privatePin";
    private static final String KEY_ACTIVE_BADGES = "activeBadges_";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void createSession(int userId, String email, String name) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_NAME, name);
        editor.apply();
    }

    public void createSession(int userId, String name, String email, String profileImage) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.putString(KEY_PROFILE_IMAGE, profileImage);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getProfileImage() {
        return prefs.getString(KEY_PROFILE_IMAGE, "");
    }

    public void setProfileImage(String profileImage) {
        editor.putString(KEY_PROFILE_IMAGE, profileImage);
        editor.apply();
    }

    public boolean isAdsEnabled() {
        return prefs.getBoolean(KEY_ADS_ENABLED, true);
    }

    public void setAdsEnabled(boolean enabled) {
        editor.putBoolean(KEY_ADS_ENABLED, enabled);
        editor.apply();
    }

    public boolean hasPrivatePin() {
        String pin = getPrivatePin();
        return pin != null && !pin.trim().isEmpty();
    }

    public String getPrivatePin() {
        return prefs.getString(KEY_PRIVATE_PIN, "");
    }

    public void setPrivatePin(String pin) {
        editor.putString(KEY_PRIVATE_PIN, pin);
        editor.apply();
    }

    public void saveActiveBadges(int userId, int count) {
        editor.putInt(KEY_ACTIVE_BADGES + userId, count);
        editor.apply();
    }

    public int getActiveBadges(int userId) {
        return prefs.getInt(KEY_ACTIVE_BADGES + userId, 0);
    }

    public void logout() {
        editor.clear();
        editor.commit();
    }
}

