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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.switchmaterial.SwitchMaterial;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.NotificationRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité des paramètres de l'application.
 * Permet de gérer le statut en ligne, les notifications, le mode sombre (Dark Mode),
 * l'activation des publicités, la confidentialité des données et la réinitialisation du mot de passe.
 */
public class ParametreActivity extends AppCompatActivity {

    private SwitchMaterial switchOnlineStatus;
    private SwitchMaterial switchNotifications;
    private SwitchMaterial switchDarkMode;
    private SwitchMaterial switchAds;
    private SwitchMaterial switchPrivateProfile;
    private Button btnChangeEmailParam;
    private Button btnChangePasswordParam;
    private Button btnPrivacyPolicyParam;
    private Button btnDeleteAccountParam;
    private Button btnAdminPanel;

    private SharedPreferences prefs;
    private UserRepository userRepository;
    private SessionManager sessionManager;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parametre);

        Toolbar toolbar = findViewById(R.id.toolbarParametre);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        prefs = getSharedPreferences("MessagesSession", MODE_PRIVATE);
        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        loadSettings();
        setupListeners();
    }

    private void initViews() {
        switchOnlineStatus = findViewById(R.id.switchOnlineStatus);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchDarkMode = findViewById(R.id.switchDarkMode);
        switchAds = findViewById(R.id.switchAds);
        switchPrivateProfile = findViewById(R.id.switchPrivateProfile);
        btnChangeEmailParam = findViewById(R.id.btnChangeEmailParam);
        btnChangePasswordParam = findViewById(R.id.btnChangePasswordParam);
        btnPrivacyPolicyParam = findViewById(R.id.btnPrivacyPolicyParam);
        btnDeleteAccountParam = findViewById(R.id.btnDeleteAccountParam);
        btnAdminPanel = findViewById(R.id.btnAdminPanel);
    }

    private void loadSettings() {
        boolean online = prefs.getBoolean("is_online", true);
        boolean notifications = prefs.getBoolean("notifications_enabled", true);
        boolean darkMode = prefs.getBoolean("dark_mode", false);

        switchOnlineStatus.setOnCheckedChangeListener(null);
        switchNotifications.setOnCheckedChangeListener(null);
        switchDarkMode.setOnCheckedChangeListener(null);
        if (switchAds != null) switchAds.setOnCheckedChangeListener(null);
        if (switchPrivateProfile != null) switchPrivateProfile.setOnCheckedChangeListener(null);

        switchOnlineStatus.setChecked(online);
        switchNotifications.setChecked(notifications);
        switchDarkMode.setChecked(darkMode);
        if (switchAds != null) switchAds.setChecked(sessionManager.isAdsEnabled());

        User currentUser = userRepository.getUserById(currentUserId);
        if (currentUser != null) {
            if (switchPrivateProfile != null) {
                switchPrivateProfile.setChecked(currentUser.isPrivateProfile());
            }
            if (btnAdminPanel != null) {
                btnAdminPanel.setVisibility(currentUser.isAdmin() ? View.VISIBLE : View.GONE);
            }
        } else {
            if (btnAdminPanel != null) {
                btnAdminPanel.setVisibility(View.GONE);
            }
        }
    }

    private void setupListeners() {
        setupLanguageSelector();
        Button btnAppUpdate = findViewById(R.id.btnAppUpdate);
        if (btnAppUpdate != null) {
            btnAppUpdate.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + getPackageName())));
                } catch (Exception e) {
                    Toasty.error(this, "Impossible d'ouvrir le Play Store", Toasty.LENGTH_SHORT).show();
                }
            });
        }

        switchOnlineStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("is_online", isChecked).apply();
            userRepository.updateUserOnlineStatus(currentUserId, isChecked);
            Toasty.info(this, isChecked ? "Présence en ligne activée 🟢" : "Présence en ligne masquée", Toasty.LENGTH_SHORT).show();
        });

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notifications_enabled", isChecked).apply();
            Toasty.info(this, isChecked ? "Notifications activées 🔔" : "Notifications désactivées", Toasty.LENGTH_SHORT).show();
        });

        if (switchAds != null) {
            switchAds.setOnCheckedChangeListener((buttonView, isChecked) -> {
                sessionManager.setAdsEnabled(isChecked);
                Toasty.info(this, isChecked ? "Publicités activées 📢" : "Publicités désactivées", Toasty.LENGTH_SHORT).show();
            });
        }

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;

            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            int targetMode = isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;

            if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
                AppCompatDelegate.setDefaultNightMode(targetMode);
            }
        });

        if (switchPrivateProfile != null) {
            switchPrivateProfile.setOnCheckedChangeListener((buttonView, isChecked) -> {
                userRepository.updateUserPrivacy(currentUserId, isChecked, isChecked, isChecked);
                Toasty.info(this, isChecked ? "Mode Profil Privé activé (Données masquées par ********) 🔒" : "Profil Public activé", Toasty.LENGTH_SHORT).show();
            });
        }

        btnChangeEmailParam.setOnClickListener(v -> showChangeEmailDialog());
        btnChangePasswordParam.setOnClickListener(v -> showChangePasswordDialog());
        if (btnPrivacyPolicyParam != null) {
            btnPrivacyPolicyParam.setOnClickListener(v -> RegisterActivity.showPrivacyPolicyDialog(this));
        }
        if (btnDeleteAccountParam != null) {
            btnDeleteAccountParam.setOnClickListener(v -> showDeleteAccountConfirmationDialog());
        }
        if (btnAdminPanel != null) {
            btnAdminPanel.setOnClickListener(v -> startActivity(new Intent(ParametreActivity.this, AdminActivity.class)));
        }
    }

    private void savePrivacySettings() {
        boolean isPrivate = switchPrivateProfile != null && switchPrivateProfile.isChecked();
        userRepository.updateUserPrivacy(currentUserId, isPrivate, isPrivate, isPrivate);
        Toasty.info(this, "Paramètres de confidentialité enregistrés", Toasty.LENGTH_SHORT).show();
    }

    public void showDeleteAccountConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer le compte ⚠️")
                .setMessage("Êtes-vous sûr de vouloir supprimer définitivement votre compte ? Cette action est irréversible.")
                .setPositiveButton("Supprimer", (dialog, which) -> performDeleteAccount())
                .setNegativeButton("Annuler", null)
                .show();
    }

    public void performDeleteAccount() {
        boolean deleted = userRepository.deleteUser(currentUserId);
        if (deleted) {
            if (currentUserId != -1) {
                NotificationRepository notificationRepo = new NotificationRepository(this);
                notificationRepo.clearNotificationsForUser(currentUserId);
            }
            sessionManager.logout();
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            } catch (Exception ignored) {}
            try {
                com.facebook.login.LoginManager.getInstance().logOut();
            } catch (Exception ignored) {}
            try {
                com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                        new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build();
                com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso).signOut();
            } catch (Exception ignored) {}

            Toasty.success(this, "Compte supprimé avec succès", Toasty.LENGTH_SHORT).show();
            Intent intent = new Intent(ParametreActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            Toasty.error(this, "Impossible de supprimer le compte", Toasty.LENGTH_SHORT).show();
        }
    }

    private void showChangeEmailDialog() {
        EditText etNewEmail = new EditText(this);
        etNewEmail.setHint("Nouvelle adresse e-mail");
        etNewEmail.setText(sessionManager.getEmail());

        new AlertDialog.Builder(this)
                .setTitle("Modifier l'e-mail")
                .setView(etNewEmail)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String newEmail = etNewEmail.getText().toString().trim();
                    if (!newEmail.isEmpty() && !newEmail.equalsIgnoreCase(sessionManager.getEmail())) {
                        boolean success = userRepository.updateUserEmail(currentUserId, newEmail);
                        if (success) {
                            sessionManager.createSession(currentUserId, sessionManager.getName(), newEmail);
                            Toasty.success(this, "E-mail mis à jour", Toasty.LENGTH_SHORT).show();
                        } else {
                            Toasty.error(this, "Cet e-mail est déjà utilisé", Toasty.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showChangePasswordDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etCurrentPass = new EditText(this);
        etCurrentPass.setHint("Mot de passe actuel");
        etCurrentPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText etNewPass = new EditText(this);
        etNewPass.setHint("Nouveau mot de passe");
        etNewPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(etCurrentPass);
        layout.addView(etNewPass);

        new AlertDialog.Builder(this)
                .setTitle("Modifier le mot de passe")
                .setView(layout)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String curPass = etCurrentPass.getText().toString().trim();
                    String newPass = etNewPass.getText().toString().trim();

                    if (curPass.isEmpty() || newPass.isEmpty()) {
                        Toasty.warning(this, "Champs incomplets", Toasty.LENGTH_SHORT).show();
                        return;
                    }

                    User user = userRepository.getUserById(currentUserId);
                    String hashedCur = PasswordUtils.hashPassword(curPass);
                    String unsaltedCur = PasswordUtils.hashPasswordUnsalted(curPass);

                    boolean valid = (user != null && user.getPassword() != null &&
                            (user.getPassword().equals(hashedCur) || user.getPassword().equals(unsaltedCur)));

                    if (!valid) {
                        Toasty.error(this, "Mot de passe actuel incorrect", Toasty.LENGTH_SHORT).show();
                        return;
                    }

                    String hashedNew = PasswordUtils.hashPassword(newPass);
                    userRepository.updateUserPassword(currentUserId, hashedNew);
                    Toasty.success(this, "Mot de passe modifié", Toasty.LENGTH_SHORT).show();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void setupLanguageSelector() {
        android.widget.TextView btnFr = findViewById(R.id.btnParamLangFrench);
        android.widget.TextView btnEn = findViewById(R.id.btnParamLangEnglish);
        android.widget.TextView btnAr = findViewById(R.id.btnParamLangArabic);

        String currentLang = LocaleHelper.getPersistedLanguage(this, "fr");
        if (btnFr != null) btnFr.setAlpha("fr".equalsIgnoreCase(currentLang) ? 1.0f : 0.4f);
        if (btnEn != null) btnEn.setAlpha("en".equalsIgnoreCase(currentLang) ? 1.0f : 0.4f);
        if (btnAr != null) btnAr.setAlpha("ar".equalsIgnoreCase(currentLang) ? 1.0f : 0.4f);

        if (btnFr != null) {
            btnFr.setOnClickListener(v -> changeAppLanguage("fr"));
        }
        if (btnEn != null) {
            btnEn.setOnClickListener(v -> changeAppLanguage("en"));
        }
        if (btnAr != null) {
            btnAr.setOnClickListener(v -> changeAppLanguage("ar"));
        }
    }

    private void changeAppLanguage(String langCode) {
        String current = LocaleHelper.getPersistedLanguage(this, "fr");
        if (!current.equalsIgnoreCase(langCode)) {
            LocaleHelper.setLocale(this, langCode);
            recreate();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

