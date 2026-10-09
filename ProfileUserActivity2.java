package td.teladoumbaobabtd;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import td.teladoumbaobabtd.repository.UserRepository;

public class ProfileUserActivity2 extends AppCompatActivity {

    private ImageView imgProfileUserAvatar;
    private TextView tvProfileUserName;
    private TextView tvProfileUserEmail;
    private TextView tvProfileUserDob;
    private TextView tvProfileUserNeighborhood;
    private TextView tvProfileUserCity;
    private TextView tvProfileUserCountry;

    private UserRepository userRepository;
    private SessionManager sessionManager;

    private int targetUserId;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_user2);

        Toolbar toolbar = findViewById(R.id.toolbarProfileUser2);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        targetUserId = getIntent().getIntExtra("user_id", -1);
        if (targetUserId == -1) {
            String targetEmail = getIntent().getStringExtra("user_email");
            if (targetEmail != null && !targetEmail.isEmpty()) {
                User u = userRepository.getUserByEmail(targetEmail);
                if (u != null) targetUserId = u.getId();
            }
        }

        initViews();
        loadUserProfile();

        imgProfileUserAvatar.setOnClickListener(v -> {
            User user = userRepository.getUserById(targetUserId);
            String imgPath = user != null ? user.getProfileImage() : getIntent().getStringExtra("user_profile_image");
            if (imgPath != null && !imgPath.isEmpty()) {
                showImagePreviewDialog(imgPath);
            }
        });
    }

    private void showImagePreviewDialog(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        android.view.View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        android.widget.ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);
        if (imgEnlarged != null) {
            ImageUtils.enablePinchToZoom(imgEnlarged);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void initViews() {
        imgProfileUserAvatar = findViewById(R.id.imgProfileUserAvatar);
        tvProfileUserName = findViewById(R.id.tvProfileUserName);
        tvProfileUserEmail = findViewById(R.id.tvProfileUserEmail);
        tvProfileUserDob = findViewById(R.id.tvProfileUserDob);
        tvProfileUserNeighborhood = findViewById(R.id.tvProfileUserNeighborhood);
        tvProfileUserCity = findViewById(R.id.tvProfileUserCity);
        tvProfileUserCountry = findViewById(R.id.tvProfileUserCountry);
    }

    private void loadUserProfile() {
        User user = userRepository.getUserById(targetUserId);

        if (user == null) {
            String fallbackName = getIntent().getStringExtra("user_name");
            String fallbackImg = getIntent().getStringExtra("user_profile_image");
            tvProfileUserName.setText(fallbackName != null ? fallbackName : "Utilisateur");
            tvProfileUserEmail.setText("Email : Non disponible");
            tvProfileUserDob.setText("Date de naissance : Non disponible");
            tvProfileUserNeighborhood.setText("Quartier : Non disponible");
            tvProfileUserCity.setText("Ville : Non disponible");
            tvProfileUserCountry.setText("Pays : Non disponible");
            ImageUtils.loadProfileImage(this, fallbackImg, imgProfileUserAvatar);
            return;
        }

        tvProfileUserName.setText(user.getName() != null ? user.getName() : "Utilisateur");
        ImageUtils.loadProfileImage(this, user.getProfileImage(), imgProfileUserAvatar);

        boolean isSelf = (targetUserId == currentUserId);

        // Email
        if (user.isHideEmail() && !isSelf) {
            tvProfileUserEmail.setText("Email : Masqué par l'utilisateur 🔒");
        } else {
            tvProfileUserEmail.setText("Email : " + (user.getEmail() != null ? user.getEmail() : "Non renseigné"));
        }

        // Date de naissance
        if (user.isHideDob() && !isSelf) {
            tvProfileUserDob.setText("Date de naissance : Masquée 🔒");
        } else {
            tvProfileUserDob.setText("Date de naissance : " + (user.getDob() != null && !user.getDob().isEmpty() ? user.getDob() : "Non renseignée"));
        }

        // Localisation (Quartier, Ville, Pays)
        if (user.isHideLocation() && !isSelf) {
            tvProfileUserNeighborhood.setText("Quartier : Masqué 🔒");
            tvProfileUserCity.setText("Ville : Masquée 🔒");
            tvProfileUserCountry.setText("Pays : Masqué 🔒");
        } else {
            tvProfileUserNeighborhood.setText("Quartier : " + (user.getNeighborhood() != null && !user.getNeighborhood().isEmpty() ? user.getNeighborhood() : "Non renseigné"));
            tvProfileUserCity.setText("Ville : " + (user.getCity() != null && !user.getCity().isEmpty() ? user.getCity() : "Non renseignée"));
            tvProfileUserCountry.setText("Pays : " + (user.getCountry() != null && !user.getCountry().isEmpty() ? user.getCountry() : "Non renseigné"));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
