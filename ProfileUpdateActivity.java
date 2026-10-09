package td.teladoumbaobabtd;

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

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

public class ProfileUpdateActivity extends AppCompatActivity {

    private ImageView imgUpdateProfileAvatar;
    private EditText etUpdateLastName, etUpdateFirstName, etUpdateDob, etUpdateNeighborhood, etUpdateCity, etUpdateCountry;
    private Button btnSaveProfileUpdate;

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
        setContentView(R.layout.activity_profile_update);

        Toolbar toolbar = findViewById(R.id.toolbarProfileUpdate);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        loadUserData();

        imgUpdateProfileAvatar.setOnClickListener(v -> launchImageCropper());
        etUpdateDob.setOnClickListener(v -> showDatePicker());
        btnSaveProfileUpdate.setOnClickListener(v -> saveProfileChanges());
    }

    private void initViews() {
        imgUpdateProfileAvatar = findViewById(R.id.imgUpdateProfileAvatar);
        etUpdateLastName = findViewById(R.id.etUpdateLastName);
        etUpdateFirstName = findViewById(R.id.etUpdateFirstName);
        etUpdateDob = findViewById(R.id.etUpdateDob);
        etUpdateNeighborhood = findViewById(R.id.etUpdateNeighborhood);
        etUpdateCity = findViewById(R.id.etUpdateCity);
        etUpdateCountry = findViewById(R.id.etUpdateCountry);
        btnSaveProfileUpdate = findViewById(R.id.btnSaveProfileUpdate);
    }

    private void loadUserData() {
        currentUser = userRepository.getUserById(currentUserId);
        if (currentUser != null) {
            etUpdateLastName.setText(currentUser.getLastName() != null ? currentUser.getLastName() : currentUser.getName());
            etUpdateFirstName.setText(currentUser.getFirstName() != null ? currentUser.getFirstName() : "");
            etUpdateDob.setText(currentUser.getDob() != null ? currentUser.getDob() : "");
            etUpdateNeighborhood.setText(currentUser.getNeighborhood() != null ? currentUser.getNeighborhood() : "");
            etUpdateCity.setText(currentUser.getCity() != null ? currentUser.getCity() : "");
            etUpdateCountry.setText(currentUser.getCountry() != null ? currentUser.getCountry() : "");

            ImageUtils.loadProfileImage(this, currentUser.getProfileImage(), imgUpdateProfileAvatar);
        }
    }

    private void showDatePicker() {
        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR) - 20;
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this, (view, selectedYear, selectedMonth, selectedDay) -> {
            String formattedDate = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear);
            etUpdateDob.setText(formattedDate);
        }, year, month, day);

        datePickerDialog.show();
    }

    private void saveProfileChanges() {
        String lastName = etUpdateLastName.getText().toString().trim();
        String firstName = etUpdateFirstName.getText().toString().trim();
        String dob = etUpdateDob.getText().toString().trim();
        String neighborhood = etUpdateNeighborhood.getText().toString().trim();
        String city = etUpdateCity.getText().toString().trim();
        String country = etUpdateCountry.getText().toString().trim();

        if (lastName.isEmpty()) {
            etUpdateLastName.setError("Nom requis");
            return;
        }

        if (firstName.isEmpty()) {
            etUpdateFirstName.setError("Prénom requis");
            return;
        }

        String savedImagePath = null;
        if (selectedImageUri != null) {
            savedImagePath = ImageUtils.saveImageToInternalStorage(this, selectedImageUri);
        } else if (currentUser != null) {
            savedImagePath = currentUser.getProfileImage();
        }

        boolean updated = userRepository.updateUserProfile(
                currentUserId,
                firstName,
                lastName,
                dob,
                neighborhood,
                city,
                country,
                savedImagePath
        );

        if (updated) {
            String combinedName = (lastName + " " + firstName).trim();
            sessionManager.createSession(currentUserId, combinedName, sessionManager.getEmail());

            Toasty.success(this, "Profil mis à jour", Toasty.LENGTH_SHORT).show();
            finish();
        } else {
            Toasty.error(this, "Erreur lors de la mise à jour", Toasty.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
