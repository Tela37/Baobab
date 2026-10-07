package td.teladoumbaobabtd;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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
import td.teladoumbaobabtd.repository.UserRepository;

public class LoginActivity extends AppCompatActivity {

    private EditText etEmail, etPassword;
    private CheckBox cbShowPassword;
    private Button btnLogin, btnGoogleLogin;
    private LoginButton btnFacebookLogin;
    private TextView tvRegister, tvForgotPassword;

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
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("MessagesSession", MODE_PRIVATE);
        boolean darkMode = prefs.getBoolean("dark_mode", false);
        int targetMode = darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO;
        if (androidx.appcompat.app.AppCompatDelegate.getDefaultNightMode() != targetMode) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(targetMode);
        }

        setContentView(R.layout.activity_login);

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        cbShowPassword = findViewById(R.id.cbShowPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnGoogleLogin = findViewById(R.id.btnGoogleLogin);
        btnFacebookLogin = findViewById(R.id.btnFacebookLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgotPassword = findViewById(R.id.tvForgotPassword);

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);

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
                etEmail.setError("Email requis");
                return;
            }

            if (password.isEmpty()) {
                etPassword.setError("Mot de passe requis");
                return;
            }

            String hashedPassword = PasswordUtils.hashPassword(password);
            User user = userRepository.getUser(email, hashedPassword);

            if (user != null) {

                sessionManager.createSession(
                        user.getId(),
                        user.getName(),
                        user.getEmail()
                );

                Toasty.success(this, "Bienvenue " + user.getName(), Toasty.LENGTH_SHORT).show();

                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();

            } else {

                Toast.makeText(
                        this,
                        "Email ou mot de passe incorrect",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
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
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestProfile()
                .build();

        googleSignInClient = GoogleSignIn.getClient(this, gso);

        btnGoogleLogin.setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                String email = account.getEmail();
                String name = account.getDisplayName();
                String photoUrl = account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null;

                if (email != null && !email.isEmpty()) {
                    onSocialLoginSuccess(email, name != null ? name : email, photoUrl);
                } else {
                    Toast.makeText(this, "Impossible de récupérer l'email Google", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (ApiException e) {
            Toast.makeText(this, "Connexion Google annulée ou indisponible (" + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupFacebookLogin() {
        callbackManager = CallbackManager.Factory.create();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        callbackManager.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void onSocialLoginSuccess(String email, String name, String photoUrl) {
        User user = userRepository.findOrCreateSocialUser(email, name, photoUrl);
        if (user != null) {
            sessionManager.createSession(
                    user.getId(),
                    user.getName(),
                    user.getEmail()
            );

            Toasty.success(this, "Bienvenue " + user.getName(), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        } else {
            Toasty.warning(this, "Erreur lors de la connexion sociale", Toasty.LENGTH_SHORT).show();
        }
    }
}
