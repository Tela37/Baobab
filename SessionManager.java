package td.teladoumbaobabtd;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Gestionnaire de session s'appuyant sur SharedPreferences.
 * Conserve l'état de connexion de l'utilisateur, l'identifiant utilisateur courant,
 * le code PIN pour les discussions privées et les préférences d'affichage de publicités.
 */
public class SessionManager {

    private static final String PREF_NAME = "MessagesSession";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";

    private static final String KEY_PRIVATE_PIN = "private_pin";
    private static final String KEY_PRIVATE_UNLOCKED = "private_unlocked";
    private static final String KEY_ADS_ENABLED = "ads_enabled";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void createSession(int userId, String name, String email) {
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_NAME, name);
        editor.putString(KEY_EMAIL, email);
        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false);
    }

    public int getUserId() {
        return prefs.getInt(KEY_USER_ID, -1);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String getPrivatePin() {
        return prefs.getString(KEY_PRIVATE_PIN, null);
    }

    public void setPrivatePin(String pin) {
        editor.putString(KEY_PRIVATE_PIN, pin);
        editor.apply();
    }

    public boolean hasPrivatePin() {
        String pin = getPrivatePin();
        return pin != null && !pin.trim().isEmpty();
    }

    public boolean isPrivateUnlocked() {
        return prefs.getBoolean(KEY_PRIVATE_UNLOCKED, false);
    }

    public void setPrivateUnlocked(boolean unlocked) {
        editor.putBoolean(KEY_PRIVATE_UNLOCKED, unlocked);
        editor.apply();
    }

    public boolean isAdsEnabled() {
        return prefs.getBoolean(KEY_ADS_ENABLED, true);
    }

    public void setAdsEnabled(boolean enabled) {
        editor.putBoolean(KEY_ADS_ENABLED, enabled);
        editor.apply();
    }

    public void logout() {
        editor.clear();
        editor.commit();
    }
}
