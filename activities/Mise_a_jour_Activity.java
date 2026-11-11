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
import android.net.Uri;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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
 * Activité de mise à jour des informations de profil utilisateur (Mise_a_jour_Activity).
 * Permet d'éditer le nom, prénom, photo de profil avec téléversement Firebase Storage, date de naissance, profession,
 * situation amoureuse, téléphone, genre, quartier, ville et pays.
 */
public class Mise_a_jour_Activity extends AppCompatActivity {

    private ImageView imgUpdateProfileAvatar;
    private EditText etUpdateLastName, etUpdateFirstName, etUpdateNeighborhood, etUpdateCity, etUpdateCountry, etUpdateProfession, etUpdateBio, etUpdateRelationshipStatus, etUpdateTelephone;
    private TextView etUpdateDob, tvUpdateEmail;
    private Spinner spinnerGender;
    private Button btnSaveProfileUpdate;
    private android.widget.ProgressBar progressBarMiseAJour;

    private UserRepository userRepository;
    private SessionManager sessionManager;

    private Uri selectedImageUri;
    private int currentUserId;
    private User currentUser;

    private final ActivityResultLauncher<CropImageContractOptions> imagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        selectedImageUri = uri;
                        imgUpdateProfileAvatar.setImageURI(uri);
                    }
                } else if (result.getError() != null) {
                  //  Toasty.error(this, getString(R.string.toast_crop_error), Toasty.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mise_a_jour);

        Toolbar toolbar = findViewById(R.id.toolbarProfileUpdate);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();
        currentUser = userRepository.getUserById(currentUserId);

        imgUpdateProfileAvatar = findViewById(R.id.mAj_img_Profile);
        etUpdateLastName = findViewById(R.id.etUpdateLastName);
        etUpdateFirstName = findViewById(R.id.etUpdateFirstName);
        etUpdateDob = findViewById(R.id.etUpdateDob);
        etUpdateProfession = findViewById(R.id.mAj_profession);
        etUpdateBio = findViewById(R.id.mAj_bio);
        etUpdateRelationshipStatus = findViewById(R.id.mAj_matrimonial_Et);
        etUpdateTelephone = findViewById(R.id.update_telephone);
        tvUpdateEmail = findViewById(R.id.update_email_btn);
        etUpdateNeighborhood = findViewById(R.id.etUpdateNeighborhood);
        etUpdateCity = findViewById(R.id.etUpdateCity);
        etUpdateCountry = findViewById(R.id.etUpdateCountry);
        spinnerGender = findViewById(R.id.spinner);
        btnSaveProfileUpdate = findViewById(R.id.btnSaveProfileUpdate);
        progressBarMiseAJour = findViewById(R.id.progressBarMiseAJour);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.gender_array,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);

        populateUserData();

        imgUpdateProfileAvatar.setOnClickListener(v -> launchImageCropper());
        etUpdateDob.setOnClickListener(v -> showDatePicker());
        btnSaveProfileUpdate.setOnClickListener(v -> saveProfileChanges());
    }

    private void populateUserData() {
        if (currentUser == null) return;
        if (currentUser.getLastName() != null) etUpdateLastName.setText(currentUser.getLastName());
        if (currentUser.getFirstName() != null) etUpdateFirstName.setText(currentUser.getFirstName());
        if (currentUser.getDob() != null) etUpdateDob.setText(currentUser.getDob());
        if (currentUser.getProfession() != null) etUpdateProfession.setText(currentUser.getProfession());
        if (currentUser.getBio() != null) etUpdateBio.setText(currentUser.getBio());
        if (currentUser.getRelationshipStatus() != null) etUpdateRelationshipStatus.setText(currentUser.getRelationshipStatus());
        if (currentUser.getPhone() != null) etUpdateTelephone.setText(currentUser.getPhone());
        if (currentUser.getEmail() != null && tvUpdateEmail != null) tvUpdateEmail.setText(currentUser.getEmail());
        if (currentUser.getNeighborhood() != null) etUpdateNeighborhood.setText(currentUser.getNeighborhood());
        if (currentUser.getCity() != null) etUpdateCity.setText(currentUser.getCity());
        if (currentUser.getCountry() != null) etUpdateCountry.setText(currentUser.getCountry());
        if (currentUser.getProfileImage() != null && !currentUser.getProfileImage().isEmpty()) {
            ImageUtils.loadProfileImage(this, currentUser.getProfileImage(), imgUpdateProfileAvatar);
        }

        String gender = currentUser.getGender();
        if (gender != null) {
            ArrayAdapter adapter = (ArrayAdapter) spinnerGender.getAdapter();
            if (adapter != null) {
                int position = adapter.getPosition(gender);
                if (position >= 0) spinnerGender.setSelection(position);
            }
        }
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
                    etUpdateDob.setText(selectedDate);
                },
                year, month, day
        );
        datePickerDialog.show();
    }

    private void saveProfileChanges() {
        String lastName = etUpdateLastName.getText().toString().trim();
        String firstName = etUpdateFirstName.getText().toString().trim();
        String dob = etUpdateDob.getText().toString().trim();
        String profession = etUpdateProfession.getText().toString().trim();
        String bio = etUpdateBio.getText().toString().trim();
        String relationshipStatus = etUpdateRelationshipStatus.getText().toString().trim();
        String telephone = etUpdateTelephone.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem() != null ? spinnerGender.getSelectedItem().toString() : "Masculin";
        String neighborhood = etUpdateNeighborhood.getText().toString().trim();
        String city = etUpdateCity.getText().toString().trim();
        String country = etUpdateCountry.getText().toString().trim();

        if (lastName.isEmpty()) {
            etUpdateLastName.setError(getString(R.string.error_name_required));
            return;
        }

        if (firstName.isEmpty()) {
            etUpdateFirstName.setError(getString(R.string.error_firstname_required));
            return;
        }

        if (btnSaveProfileUpdate != null) btnSaveProfileUpdate.setVisibility(android.view.View.GONE);
        if (progressBarMiseAJour != null) progressBarMiseAJour.setVisibility(android.view.View.VISIBLE);

        String savedImagePath = currentUser != null ? currentUser.getProfileImage() : null;

        if (selectedImageUri != null) {
            String base64Image = ImageUtils.uriToBase64(this, selectedImageUri);
            if (base64Image != null && !base64Image.trim().isEmpty()) {
                savedImagePath = base64Image;
            } else {
                savedImagePath = ImageUtils.saveImageToInternalStorage(this, selectedImageUri);
            }
        }

        finalizeProfileUpdate(currentUserId, firstName, lastName, dob, neighborhood, city, country, savedImagePath, profession, relationshipStatus, gender, telephone, bio);
    }

    private void finalizeProfileUpdate(int currentUserId, String firstName, String lastName, String dob,
                                     String neighborhood, String city, String country, String imagePath,
                                     String profession, String relationshipStatus, String gender,
                                     String telephone, String bio) {
        boolean updated = userRepository.updateUserProfile(
                currentUserId, firstName, lastName, dob, neighborhood, city, country,
                imagePath, profession, relationshipStatus, gender, telephone, bio
        );

        if (updated) {
            String combinedName = (lastName + " " + firstName).trim();
            sessionManager.createSession(currentUserId, combinedName, sessionManager.getEmail(), imagePath);

            User updatedUser = userRepository.getUserById(currentUserId);
            if (updatedUser != null) {
                FirebaseHelper.getInstance().syncUserProfileToFirestore(updatedUser);
                FirebaseHelper.getInstance().syncUserToRealtimeDb(updatedUser);
            }

            Toasty.success(this, getString(R.string.toast_profile_updated), Toast.LENGTH_SHORT).show();
            finish();
        } else {
            if (progressBarMiseAJour != null) progressBarMiseAJour.setVisibility(android.view.View.GONE);
            if (btnSaveProfileUpdate != null) btnSaveProfileUpdate.setVisibility(android.view.View.VISIBLE);
            Toasty.error(this, getString(R.string.toast_profile_update_error), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

