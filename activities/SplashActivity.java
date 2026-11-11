package td.teladoum.baobab.activities;

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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Écran de démarrage (Splash Screen) affichant le logo 'baoba1' en plein écran.
 * Redirige automatiquement après 1,5 seconde vers AcceuilActivity (si déjà connecté)
 * ou vers LoginActivity.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1500; // 1,5 seconde

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        // Appliquer le thème clair/sombre sauvegardé
        SharedPreferences prefs = getSharedPreferences("MessagesSession", MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean("dark_mode", false);
        int targetMode = darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != targetMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        setContentView(R.layout.activity_splash);

        // Configuration Plein Écran
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.navigationBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;

            SessionManager sessionManager = new SessionManager(SplashActivity.this);
            Intent intent;
            if (sessionManager.isLoggedIn()) {
                intent = new Intent(SplashActivity.this, AcceuilActivity.class);
            } else {
                intent = new Intent(SplashActivity.this, LoginActivity.class);
            }
            startActivity(intent);
            finish();
        }, SPLASH_DELAY_MS);
    }
}

