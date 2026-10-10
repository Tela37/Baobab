package td.teladoumbaobabtd;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import td.teladoumbaobabtd.repository.UserRepository;
import com.google.android.material.switchmaterial.SwitchMaterial;

import es.dmoral.toasty.Toasty;

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
    private SwitchMaterial switchHideEmail;
    private SwitchMaterial switchHideDob;
    private SwitchMaterial switchHideLocation;
    private Button btnChangeEmailParam;
    private Button btnChangePasswordParam;
    private Button btnPrivacyPolicyParam;
    private Button btnDeleteAccountParam;

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
        switchHideEmail = findViewById(R.id.switchHideEmail);
        switchHideDob = findViewById(R.id.switchHideDob);
        switchHideLocation = findViewById(R.id.switchHideLocation);
        btnChangeEmailParam = findViewById(R.id.btnChangeEmailParam);
        btnChangePasswordParam = findViewById(R.id.btnChangePasswordParam);
        btnPrivacyPolicyParam = findViewById(R.id.btnPrivacyPolicyParam);
        btnDeleteAccountParam = findViewById(R.id.btnDeleteAccountParam);
    }

    private void loadSettings() {
        boolean online = prefs.getBoolean("is_online", true);
        boolean notifications = prefs.getBoolean("notifications_enabled", true);
        boolean darkMode = prefs.getBoolean("dark_mode", false);

        switchOnlineStatus.setOnCheckedChangeListener(null);
        switchNotifications.setOnCheckedChangeListener(null);
        switchDarkMode.setOnCheckedChangeListener(null);
        if (switchAds != null) switchAds.setOnCheckedChangeListener(null);
        switchHideEmail.setOnCheckedChangeListener(null);
        switchHideDob.setOnCheckedChangeListener(null);
        switchHideLocation.setOnCheckedChangeListener(null);

        switchOnlineStatus.setChecked(online);
        switchNotifications.setChecked(notifications);
        switchDarkMode.setChecked(darkMode);
        if (switchAds != null) switchAds.setChecked(sessionManager.isAdsEnabled());

        User currentUser = userRepository.getUserById(currentUserId);
        if (currentUser != null) {
            switchHideEmail.setChecked(currentUser.isHideEmail());
            switchHideDob.setChecked(currentUser.isHideDob());
            switchHideLocation.setChecked(currentUser.isHideLocation());
        }
    }

    private void setupListeners() {
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

        switchHideEmail.setOnCheckedChangeListener((buttonView, isChecked) -> savePrivacySettings());
        switchHideDob.setOnCheckedChangeListener((buttonView, isChecked) -> savePrivacySettings());
        switchHideLocation.setOnCheckedChangeListener((buttonView, isChecked) -> savePrivacySettings());

        btnChangeEmailParam.setOnClickListener(v -> showChangeEmailDialog());
        btnChangePasswordParam.setOnClickListener(v -> showChangePasswordDialog());
        if (btnPrivacyPolicyParam != null) {
            btnPrivacyPolicyParam.setOnClickListener(v -> RegisterActivity.showPrivacyPolicyDialog(this));
        }
        if (btnDeleteAccountParam != null) {
            btnDeleteAccountParam.setOnClickListener(v -> showDeleteAccountConfirmationDialog());
        }
    }

    private void savePrivacySettings() {
        boolean hideEmail = switchHideEmail.isChecked();
        boolean hideDob = switchHideDob.isChecked();
        boolean hideLoc = switchHideLocation.isChecked();

        userRepository.updateUserPrivacy(currentUserId, hideEmail, hideDob, hideLoc);
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
            sessionManager.logout();
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
