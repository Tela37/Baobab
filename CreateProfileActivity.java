package td.teladoumbaobabtd;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import td.teladoumbaobabtd.repository.UserRepository;

import java.util.Calendar;

import es.dmoral.toasty.Toasty;

/**
 * Activité de création de profil utilisateur après la première inscription.
 * Permet de définir le nom, prénom, photo de profil (recadrage), date de naissance et localisation.
 */
public class CreateProfileActivity extends AppCompatActivity {

    private ImageView imgCreateProfileAvatar;
    private EditText etLastName, etFirstName, etDob, etNeighborhood, etCity, etCountry;
    private TextView tvEmailDisplay;
    private Button btnSubmitProfile;

    private UserRepository userRepository;
    private SessionManager sessionManager;

    private Uri selectedImageUri;
    private String userEmail;
    private String userPassword;

    private final ActivityResultLauncher<CropImageContractOptions> imagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgCreateProfileAvatar.setImageURI(uri);
                    }
                } else if (result.getError() != null) {
                    Toasty.error(this, "Erreur lors du recadrage", Toasty.LENGTH_SHORT).show();
                }
            });

    private void launchImageCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.aspectRatioX = 1;
        options.aspectRatioY = 1;
        options.fixAspectRatio = true;
        options.activityTitle = "Recadrer la photo de profil";
        options.cropMenuCropButtonTitle = "Valider";
        options.activityMenuIconColor = android.graphics.Color.WHITE;
        options.toolbarColor = android.graphics.Color.parseColor("#2196F3");
        options.toolbarTitleColor = android.graphics.Color.WHITE;
        options.toolbarBackButtonColor = android.graphics.Color.WHITE;
        options.toolbarTintColor = android.graphics.Color.WHITE;
        imagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_profile);

        Toolbar toolbar = findViewById(R.id.toolbarCreateProfile);
        setSupportActionBar(toolbar);

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);

        initViews();
        readIntentData();

        imgCreateProfileAvatar.setOnClickListener(v -> launchImageCropper());
        etDob.setOnClickListener(v -> showDatePicker());
        btnSubmitProfile.setOnClickListener(v -> submitProfile());
    }

    private void initViews() {
        imgCreateProfileAvatar = findViewById(R.id.imgCreateProfileAvatar);
        etLastName = findViewById(R.id.etLastName);
        etFirstName = findViewById(R.id.etFirstName);
        tvEmailDisplay = findViewById(R.id.tvEmailDisplay);
        etDob = findViewById(R.id.etDob);
        etNeighborhood = findViewById(R.id.etNeighborhood);
        etCity = findViewById(R.id.etCity);
        etCountry = findViewById(R.id.etCountry);
        btnSubmitProfile = findViewById(R.id.btnSubmitProfile);
    }

    private void readIntentData() {
        Intent intent = getIntent();
        userEmail = intent.getStringExtra("email");
        userPassword = intent.getStringExtra("password");

        if (userEmail != null) {
            tvEmailDisplay.setText(userEmail);
        } else {
            tvEmailDisplay.setText("email@example.com");
        }
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR) - 20;
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            String formattedDate = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear);
            etDob.setText(formattedDate);
        }, year, month, day);

        datePickerDialog.show();
    }

    private void submitProfile() {
        String lastName = etLastName.getText().toString().trim();
        String firstName = etFirstName.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String neighborhood = etNeighborhood.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String country = etCountry.getText().toString().trim();

        if (lastName.isEmpty()) {
            etLastName.setError("Nom requis");
            return;
        }

        if (firstName.isEmpty()) {
            etFirstName.setError("Prénom requis");
            return;
        }

        String savedImagePath = null;
        if (selectedImageUri != null) {
            savedImagePath = ImageUtils.saveImageToInternalStorage(this, selectedImageUri);
            // Téléversement cloud arrière-plan vers Firebase Storage
            FirebaseHelper.getInstance().uploadMedia("profile_images", selectedImageUri, new FirebaseHelper.OnUploadCompleteListener() {
                @Override
                public void onSuccess(String downloadUrl) {
                }

                @Override
                public void onFailure(Exception e) {
                }
            });
        }

        String pwdToSave = userPassword != null ? userPassword : PasswordUtils.hashPassword("123456");

        boolean created = userRepository.insertFullUser(
                firstName,
                lastName,
                userEmail,
                pwdToSave,
                dob,
                neighborhood,
                city,
                country,
                savedImagePath
        );

        if (!created && userRepository.emailExists(userEmail)) {
            User existing = userRepository.getUserByEmail(userEmail);
            if (existing != null) {
                userRepository.updateUserProfile(
                        existing.getId(),
                        firstName,
                        lastName,
                        dob,
                        neighborhood,
                        city,
                        country,
                        savedImagePath
                );
                created = true;
            }
        }

        if (created) {
            User user = userRepository.getUserByEmail(userEmail);
            if (user != null) {
                sessionManager.createSession(user.getId(), user.getName(), user.getEmail());
            }

            Toasty.success(this, "Profil créé avec succès", Toasty.LENGTH_SHORT).show();
            startActivity(new Intent(CreateProfileActivity.this, AcceuilActivity.class));
            finish();
        } else {
            Toasty.error(this, "Erreur lors de la création du profil", Toasty.LENGTH_SHORT).show();
        }
    }
}
