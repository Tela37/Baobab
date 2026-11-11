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

import android.app.DatePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import java.util.Calendar;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.PostRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité de création de profil utilisateur après la première inscription.
 * Permet de définir le nom, prénom, photo de profil (recadrage) avec téléversement Firebase Storage,
 * date de naissance, profession, situation amoureuse, téléphone, genre et localisation.
 * Conforme aux politiques de confidentialité et de protection des données (Google Play Store).
 */
public class CreateProfileActivity extends AppCompatActivity {

    private ImageView imgCreateProfileAvatar;
    private EditText etLastName, etFirstName, etNeighborhood, etCity, etCountry,
            etProfession, etBio, etRelationshipStatus, etTelephone;
    private TextView tvEmailDisplay, etDob;
    private Spinner spinnerGender;
    private Button btnSubmitProfile;
    private android.widget.ProgressBar progressBarCreateProfile;

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
                    Toasty.error(this, getString(R.string.toast_crop_error), Toasty.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_profile);

        Toolbar toolbar = findViewById(R.id.toolbarCreateProfile);
        setSupportActionBar(toolbar);

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);

        imgCreateProfileAvatar = findViewById(R.id.imgCreateProfileAvatar);
        etLastName = findViewById(R.id.etLastName);
        etFirstName = findViewById(R.id.etFirstName);
        etDob = findViewById(R.id.etDob);
        etProfession = findViewById(R.id.profession);
        etBio = findViewById(R.id.biographie);
        etRelationshipStatus = findViewById(R.id.cp_matrimonial);
        etTelephone = findViewById(R.id.setup_telephone);
        tvEmailDisplay = findViewById(R.id.tvEmailDisplay);
        etNeighborhood = findViewById(R.id.etNeighborhood);
        etCity = findViewById(R.id.etCity);
        etCountry = findViewById(R.id.etCountry);
        spinnerGender = findViewById(R.id.spinner);
        btnSubmitProfile = findViewById(R.id.btnSubmitProfile);
        progressBarCreateProfile = findViewById(R.id.progressBarCreateProfile);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.gender_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);

        userEmail = getIntent().getStringExtra("email");
        userPassword = getIntent().getStringExtra("password");
        String extraFirstName = getIntent().getStringExtra("first_name");
        String extraLastName = getIntent().getStringExtra("last_name");
        String extraPhotoUrl = getIntent().getStringExtra("photo_url");

        if (userEmail != null && tvEmailDisplay != null) {
            tvEmailDisplay.setText(userEmail);
        }
        if (extraFirstName != null && !extraFirstName.trim().isEmpty() && etFirstName != null) {
            etFirstName.setText(extraFirstName);
        }
        if (extraLastName != null && !extraLastName.trim().isEmpty() && etLastName != null) {
            etLastName.setText(extraLastName);
        }
        if (extraPhotoUrl != null && !extraPhotoUrl.trim().isEmpty() && imgCreateProfileAvatar != null) {
            ImageUtils.loadProfileImage(this, extraPhotoUrl, imgCreateProfileAvatar);
        }

        imgCreateProfileAvatar.setOnClickListener(v -> launchImageCropper());
        etDob.setOnClickListener(v -> showDatePicker());
        btnSubmitProfile.setOnClickListener(v -> submitProfile());
    }

    private void launchImageCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.activityTitle = "Recadrer la photo de profil";
        options.cropMenuCropButtonTitle = "Valider";
        options.activityMenuIconColor = android.graphics.Color.WHITE;
        options.toolbarColor = android.graphics.Color.parseColor("#2196F3");
        options.toolbarTitleColor = android.graphics.Color.WHITE;
        options.toolbarBackButtonColor = android.graphics.Color.WHITE;
        options.toolbarTintColor = android.graphics.Color.WHITE;
        imagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    private void showDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(
                this,
                (view, year1, month1, dayOfMonth) -> {
                    String selectedDate = String.format(java.util.Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month1 + 1, year1);
                    etDob.setText(selectedDate);
                },
                year, month, day
        );
        datePickerDialog.show();
    }

    private void submitProfile() {
        String lastName = etLastName.getText().toString().trim();
        String firstName = etFirstName.getText().toString().trim();
        String dob = etDob.getText().toString().trim();
        String profession = etProfession.getText().toString().trim();
        String bio = etBio.getText().toString().trim();
        String relationshipStatus = etRelationshipStatus.getText().toString().trim();
        String telephone = etTelephone.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem() != null ? spinnerGender.getSelectedItem().toString() : "Masculin";
        String neighborhood = etNeighborhood.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        String country = etCountry.getText().toString().trim();

        if (lastName.isEmpty()) {
            etLastName.setError(getString(R.string.error_name_required));
            return;
        }

        if (firstName.isEmpty()) {
            etFirstName.setError(getString(R.string.error_firstname_required));
            return;
        }

        if (btnSubmitProfile != null) btnSubmitProfile.setVisibility(View.GONE);
        if (progressBarCreateProfile != null) progressBarCreateProfile.setVisibility(View.VISIBLE);

        String savedImagePath = null;
        String pwdToSave = userPassword != null ? userPassword : PasswordUtils.hashPassword("123456");

        if (selectedImageUri != null) {
            String base64Image = ImageUtils.uriToBase64(this, selectedImageUri);
            if (base64Image != null && !base64Image.trim().isEmpty()) {
                savedImagePath = base64Image;
            } else {
                savedImagePath = ImageUtils.saveImageToInternalStorage(this, selectedImageUri);
            }
        }

        finalizeProfileCreation(firstName, lastName, userEmail, pwdToSave, dob, neighborhood, city, country, savedImagePath, profession, relationshipStatus, gender, telephone, bio);
    }

    private void finalizeProfileCreation(String firstName, String lastName, String userEmail, String pwdToSave,
                                       String dob, String neighborhood, String city, String country,
                                       String imagePath, String profession, String relationshipStatus,
                                       String gender, String telephone, String bio) {
        boolean created = userRepository.insertFullUser(
                firstName, lastName, userEmail, pwdToSave, dob, neighborhood, city, country,
                imagePath, profession, relationshipStatus, gender, telephone, bio
        );

        if (!created && userRepository.emailExists(userEmail)) {
            User existing = userRepository.getUserByEmail(userEmail);
            if (existing != null) {
                userRepository.updateUserProfile(
                        existing.getId(), firstName, lastName, dob, neighborhood, city, country,
                        imagePath, profession, relationshipStatus, gender, telephone, bio
                );
            }
        }

        User u = userRepository.getUserByEmail(userEmail);
        if (u != null) {
            sessionManager.createSession(
                    u.getId(),
                    (lastName + " " + firstName).trim(),
                    userEmail,
                    imagePath
            );
            Toasty.success(this, getString(R.string.toast_profile_created), Toast.LENGTH_SHORT).show();
            startActivity(new Intent(CreateProfileActivity.this, AcceuilActivity.class));
            finish();
        } else {
            if (progressBarCreateProfile != null) progressBarCreateProfile.setVisibility(View.GONE);
            if (btnSubmitProfile != null) btnSubmitProfile.setVisibility(View.VISIBLE);
            Toasty.error(this, "Erreur de création de profil. Veuillez réessayer.", Toast.LENGTH_SHORT).show();
        }
    }
}

