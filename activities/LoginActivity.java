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
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.GraphRequest;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;

import java.util.Arrays;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.NotificationRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité de connexion (Login).
 * Gère l'authentification locale (Email/Mot de passe), la connexion Google SignIn et Facebook Login,
 * la connexion automatique avec "Se souvenir de moi" et le déverrouillage par empreinte biométrique.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private CheckBox cbShowPassword;
    private Button btnLogin, btnGoogleLogin;
    private LoginButton btnFacebookLogin;
    private TextView tvRegister, tvForgotPassword;
    private ProgressBar progressBar;

    private UserRepository userRepository;
    private SessionManager sessionManager;

    private GoogleSignInClient googleSignInClient;
    private CallbackManager callbackManager;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Task<GoogleSignInAccount> task =
                                    GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                            handleGoogleSignInResult(task);
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("MessagesSession", MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean("dark_mode", false);
        int targetMode = darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != targetMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        setContentView(R.layout.activity_login);

        TextView btnLangFrench = findViewById(R.id.btnLangFrench);
        TextView btnLangEnglish = findViewById(R.id.btnLangEnglish);
        TextView btnLangArabic = findViewById(R.id.btnLangArabic);

        String currentLang = LocaleHelper.getPersistedLanguage(this, "fr");
        highlightLanguageButton(currentLang, btnLangFrench, btnLangEnglish, btnLangArabic);

        if (btnLangFrench != null) {
            btnLangFrench.setOnClickListener(v -> changeLanguage("fr"));
        }
        if (btnLangEnglish != null) {
            btnLangEnglish.setOnClickListener(v -> changeLanguage("en"));
        }
        if (btnLangArabic != null) {
            btnLangArabic.setOnClickListener(v -> changeLanguage("ar"));
        }

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbShowPassword = findViewById(R.id.cbShowPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        btnFacebookLogin = findViewById(R.id.btnFacebookLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);
        progressBar = findViewById(R.id.progressBar);

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);

        checkAndAutoLogin();

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }

        setupGoogleSignIn();
        setupFacebookLogin();


        cbShowPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> {

            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty()) {
                etEmail.setError(getString(R.string.error_email_required));
                return;
            }

            if (password.isEmpty()) {
                etPassword.setError(getString(R.string.error_password_required));
                return;
            }

            if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
            btnLogin.setEnabled(false);

            String hashedPassword = PasswordUtils.hashPassword(password);
            User user = userRepository.getUser(email, hashedPassword);

            if (user != null) {
                // Synchronisation de la session Firebase Auth avec vérification
                FirebaseHelper.getInstance().loginUser(email, password, task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        android.util.Log.w("LoginActivity", "Firebase Auth sync note: " + task.getException().getMessage());
                    }
                });

                NotificationRepository notificationRepo = new NotificationRepository(this);
                notificationRepo.clearNotificationsForOtherUsers(user.getId());

                sessionManager.createSession(
                        user.getId(),
                        user.getName(),
                        user.getEmail(),
                        user.getProfileImage()
                );

                Toasty.success(this, "Bienvenue " + user.getName(), Toasty.LENGTH_SHORT).show();

                startActivity(new Intent(LoginActivity.this, AcceuilActivity.class));
                finish();

            } else {
                FirebaseHelper.getInstance().loginUser(email, password, authTask -> {
                    if (authTask.isSuccessful()) {
                        userRepository.fetchAndSyncUserFromFirestore(email, password, () -> {
                            User syncedUser = userRepository.getUser(email, PasswordUtils.hashPassword(password));
                            if (syncedUser == null) {
                                syncedUser = userRepository.getUserByEmail(email);
                                if (syncedUser != null && (syncedUser.getPassword() == null || syncedUser.getPassword().trim().isEmpty())) {
                                    syncedUser.setPassword(PasswordUtils.hashPassword(password));
                                    FirebaseHelper.getInstance().syncUserToRealtimeDb(syncedUser);
                                    FirebaseHelper.getInstance().syncUserProfileToFirestore(syncedUser);
                                }
                            }

                            if (syncedUser != null) {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                                btnLogin.setEnabled(true);

                                NotificationRepository notificationRepo = new NotificationRepository(this);
                                notificationRepo.clearNotificationsForOtherUsers(syncedUser.getId());

                                sessionManager.createSession(
                                        syncedUser.getId(),
                                        syncedUser.getName(),
                                        syncedUser.getEmail(),
                                        syncedUser.getProfileImage()
                                );

                                Toasty.success(this, "Bienvenue " + syncedUser.getName(), Toasty.LENGTH_SHORT).show();
                                startActivity(new Intent(LoginActivity.this, AcceuilActivity.class));
                                finish();
                            } else {
                                if (progressBar != null) progressBar.setVisibility(View.GONE);
                                btnLogin.setEnabled(true);
                                Toasty.error(this, "Erreur de synchronisation du profil", Toast.LENGTH_SHORT).show();
                            }
                        }, () -> {
                            if (progressBar != null) progressBar.setVisibility(View.GONE);
                            btnLogin.setEnabled(true);
                            Toasty.error(this, "Email ou mot de passe incorrect", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        btnLogin.setEnabled(true);
                        String errMsg = authTask.getException() != null ? authTask.getException().getMessage() : "Email ou mot de passe incorrect";
                        Toasty.error(this, errMsg, Toasty.LENGTH_LONG).show();
                    }
                });
            }
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkAndAutoLogin();
    }

    private void checkAndAutoLogin() {
        if (sessionManager == null || userRepository == null) return;

        com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (sessionManager.isLoggedIn() || fbUser != null) {
            if (fbUser != null && (!sessionManager.isLoggedIn() || sessionManager.getUserId() == -1)) {
                String email = fbUser.getEmail();
                if (email != null && !email.trim().isEmpty()) {
                    User user = userRepository.getUserByEmail(email);
                    if (user != null) {
                        sessionManager.createSession(user.getId(), user.getName(), user.getEmail(), user.getProfileImage());
                    } else {
                        String displayName = fbUser.getDisplayName() != null ? fbUser.getDisplayName() : email;
                        String photoUrl = fbUser.getPhotoUrl() != null ? fbUser.getPhotoUrl().toString() : null;
                        User socialUser = userRepository.findOrCreateSocialUser(email, displayName, photoUrl);
                        if (socialUser != null) {
                            sessionManager.createSession(socialUser.getId(), socialUser.getName(), socialUser.getEmail(), socialUser.getProfileImage());
                        }
                    }
                }
            }
            if (sessionManager.isLoggedIn() && sessionManager.getUserId() != -1) {
                Intent intent = new Intent(LoginActivity.this, AcceuilActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        }
    }

    private void showForgotPasswordDialog() {
        EditText etResetEmail = new EditText(this);
        etResetEmail.setHint("Entrez votre adresse email");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Mot de passe oublié ?")
                .setMessage("Entrez l'adresse email de votre compte pour réinitialiser votre mot de passe.")
                .setView(etResetEmail)
                .setPositiveButton("Continuer", (dialog, which) -> {
                    String resetEmail = etResetEmail.getText().toString().trim();
                    if (resetEmail.isEmpty()) {
                        Toasty.warning(this, "Email requis", Toasty.LENGTH_SHORT).show();
                        return;
                    }

                    if (!userRepository.emailExists(resetEmail)) {
                        Toasty.error(this, "Aucun compte associé à cet email", Toasty.LENGTH_SHORT).show();
                        return;
                    }

                    showResetPasswordFormDialog(resetEmail);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showResetPasswordFormDialog(String email) {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etNewPass = new EditText(this);
        etNewPass.setHint("Nouveau mot de passe");
        etNewPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        EditText etConfirmPass = new EditText(this);
        etConfirmPass.setHint("Confirmer le mot de passe");
        etConfirmPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(etNewPass);
        layout.addView(etConfirmPass);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Réinitialiser le mot de passe")
                .setMessage("Compte: " + email)
                .setView(layout)
                .setPositiveButton("Réinitialiser", (dialog, which) -> {
                    String np = etNewPass.getText().toString().trim();
                    String cp = etConfirmPass.getText().toString().trim();

                    if (np.isEmpty() || np.length() < 4) {
                        Toasty.warning(this, "Le mot de passe doit faire au moins 4 caractères", Toasty.LENGTH_SHORT).show();
                        return;
                    }

                    if (!np.equals(cp)) {
                        Toasty.error(this, "Les mots de passe ne correspondent pas", Toasty.LENGTH_SHORT).show();
                        return;
                    }

                    User user = userRepository.getUserByEmail(email);
                    if (user != null) {
                        String hashed = PasswordUtils.hashPassword(np);
                        boolean updated = userRepository.updateUserPassword(user.getId(), hashed);
                        if (updated) {
                            Toasty.success(this, "Mot de passe réinitialisé avec succès", Toasty.LENGTH_SHORT).show();
                        } else {
                            Toasty.error(this, "Erreur de réinitialisation", Toasty.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void setupGoogleSignIn() {
        GoogleSignInOptions.Builder gsoBuilder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile();

        int webClientIdRes = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (webClientIdRes != 0) {
            gsoBuilder.requestIdToken(getString(webClientIdRes));
        }

        GoogleSignInOptions gso = gsoBuilder.build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleLogin.setOnClickListener(v -> {
            if (googleSignInClient != null) {
                googleSignInClient.signOut().addOnCompleteListener(task -> {
                    Intent signInIntent = googleSignInClient.getSignInIntent();
                    googleSignInLauncher.launch(signInIntent);
                });
            }
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                String email = account.getEmail();
                String firstName = account.getGivenName();
                String lastName = account.getFamilyName();
                String displayName = account.getDisplayName();
                String photoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null;

                if (email != null && !email.isEmpty()) {
                    onGoogleSignInSuccess(email, firstName, lastName, displayName, photoUrl);
                } else {
                    Toasty.warning(this, "Impossible de récupérer l'email Google", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (ApiException e) {
            int statusCode = e.getStatusCode();
            if (statusCode == 12500 || statusCode == 10) {
                Toasty.error(this, "Erreur Google (" + statusCode + ") : L'empreinte SHA-1 de l'application doit être ajoutée sur Firebase Console.", Toasty.LENGTH_LONG).show();
            } else if (statusCode == 12501) {
                Toasty.info(this, "Connexion Google annulée", Toasty.LENGTH_SHORT).show();
            } else {
                Toasty.error(this, "Connexion Google indisponible (Code: " + statusCode + ")", Toasty.LENGTH_SHORT).show();
            }
        }
    }

    private void onGoogleSignInSuccess(String email, String firstName, String lastName, String displayName, String photoUrl) {
        User existingUser = userRepository.getUserByEmail(email);
        boolean isProfileComplete = existingUser != null &&
                ((existingUser.getDob() != null && !existingUser.getDob().trim().isEmpty()) ||
                        (existingUser.getCountry() != null && !existingUser.getCountry().trim().isEmpty()));

        if (existingUser != null && isProfileComplete) {
            NotificationRepository notificationRepo = new NotificationRepository(this);
            notificationRepo.clearNotificationsForOtherUsers(existingUser.getId());

            sessionManager.createSession(
                    existingUser.getId(),
                    existingUser.getName(),
                    existingUser.getEmail(),
                    existingUser.getProfileImage()
            );

            Toasty.success(this, "Bienvenue " + existingUser.getName(), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, AcceuilActivity.class));
            finish();
        } else {
            Intent intent = new Intent(LoginActivity.this, CreateProfileActivity.class);
            intent.putExtra("email", email);
            intent.putExtra("first_name", firstName != null ? firstName : "");
            intent.putExtra("last_name", lastName != null ? lastName : (displayName != null ? displayName : ""));
            intent.putExtra("photo_url", photoUrl);
            startActivity(intent);
            finish();
        }
    }

    private void setupFacebookLogin() {
        if (btnFacebookLogin != null) {
            btnFacebookLogin.setVisibility(View.GONE);
        }
        callbackManager = CallbackManager.Factory.create();
        if (btnFacebookLogin == null) return;
        btnFacebookLogin.setPermissions(Arrays.asList("email", "public_profile"));

        btnFacebookLogin.registerCallback(callbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                GraphRequest request = GraphRequest.newMeRequest(
                        loginResult.getAccessToken(),
                        (object, response) -> {
                            try {
                                String email = null;
                                String name = "Utilisateur Facebook";
                                String photoUrl = null;

                                if (object != null) {
                                    if (object.has("email")) {
                                        email = object.getString("email");
                                    }
                                    if (object.has("name")) {
                                        name = object.getString("name");
                                    }
                                    if (object.has("picture")) {
                                        JSONObject pictureData = object.getJSONObject("picture").optJSONObject("data");
                                        if (pictureData != null && pictureData.has("url")) {
                                            photoUrl = pictureData.getString("url");
                                        }
                                    }
                                }

                                if (email == null || email.isEmpty()) {
                                    email = "fb_" + loginResult.getAccessToken().getUserId() + "@facebook.com";
                                }

                                onSocialLoginSuccess(email, name, photoUrl);

                            } catch (Exception e) {
                                Toast.makeText(LoginActivity.this, "Erreur de traitement profil Facebook", Toast.LENGTH_SHORT).show();
                            }
                        }
                );

                Bundle parameters = new Bundle();
                parameters.putString("fields", "id,name,email,picture.type(large)");
                request.setParameters(parameters);
                request.executeAsync();
            }

            @Override
            public void onCancel() {
                Toasty.warning(LoginActivity.this, "Connexion Facebook annulée", Toasty.LENGTH_SHORT).show();
            }

            @Override
            public void onError(FacebookException error) {
                Toasty.error(LoginActivity.this, "Erreur Facebook: " + error.getMessage(), Toasty.LENGTH_SHORT).show();
            }
        });
    }

    private void changeLanguage(String langCode) {
        String current = LocaleHelper.getPersistedLanguage(this, "fr");
        if (!current.equalsIgnoreCase(langCode)) {
            LocaleHelper.setLocale(this, langCode);
            recreate();
        }
    }

    private void highlightLanguageButton(String langCode, TextView btnFr, TextView btnEn, TextView btnAr) {
        if (btnFr != null) btnFr.setAlpha("fr".equalsIgnoreCase(langCode) ? 1.0f : 0.4f);
        if (btnEn != null) btnEn.setAlpha("en".equalsIgnoreCase(langCode) ? 1.0f : 0.4f);
        if (btnAr != null) btnAr.setAlpha("ar".equalsIgnoreCase(langCode) ? 1.0f : 0.4f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onSocialLoginSuccess(String email, String name, String photoUrl) {
        User user = userRepository.findOrCreateSocialUser(email, name, photoUrl);
        if (user != null) {
            NotificationRepository notificationRepo = new NotificationRepository(this);
            notificationRepo.clearNotificationsForOtherUsers(user.getId());

            sessionManager.createSession(
                    user.getId(),
                    user.getName(),
                    user.getEmail(),
                    user.getProfileImage()
            );

            Toasty.success(this, "Bienvenue " + user.getName(), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, AcceuilActivity.class));
            finish();
        } else {
            Toasty.warning(this, "Erreur lors de la connexion sociale", Toasty.LENGTH_SHORT).show();
        }
    }
}

