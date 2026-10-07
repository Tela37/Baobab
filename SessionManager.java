package td.teladoumbaobabtd;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {

    private static final String PREF_NAME =
            "MessagesSession";

    private static final String KEY_IS_LOGGED_IN =
            "isLoggedIn";

    private static final String KEY_USER_ID =
            "userId";

    private static final String KEY_NAME =
            "name";

    private static final String KEY_EMAIL =
            "email";

    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    public SessionManager(Context context) {

        prefs = context.getSharedPreferences(
                PREF_NAME,
                Context.MODE_PRIVATE
        );

        editor = prefs.edit();
    }

    public void createSession(
            int userId,
            String name,
            String email) {

        editor.putBoolean(
                KEY_IS_LOGGED_IN,
                true
        );

        editor.putInt(
                KEY_USER_ID,
                userId
        );

        editor.putString(
                KEY_NAME,
                name
        );

        editor.putString(
                KEY_EMAIL,
                email
        );

        editor.apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(
                KEY_IS_LOGGED_IN,
                false
        );
    }

    public int getUserId() {
        return prefs.getInt(
                KEY_USER_ID,
                -1
        );
    }

    public String getName() {
        return prefs.getString(
                KEY_NAME,
                ""
        );
    }

    public String getEmail() {
        return prefs.getString(
                KEY_EMAIL,
                ""
        );
    }

    public void logout() {
        editor.clear();
        editor.commit();
    }
}