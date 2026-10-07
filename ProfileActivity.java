package td.teladoumbaobabtd;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import td.teladoumbaobabtd.repository.UserRepository;

public class ProfileActivity extends AppCompatActivity {

    private ImageView imgProfileAvatar;
    private TextView tvProfileLastName;
    private TextView tvProfileFirstName;
    private TextView tvProfileEmail;
    private TextView tvProfileDob;
    private TextView tvProfileNeighborhood;
    private TextView tvProfileCity;
    private TextView tvProfileCountry;
    private Button btnOpenProfileUpdate;

    private UserRepository userRepository;
    private SessionManager sessionManager;
    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        Toolbar toolbar = findViewById(R.id.toolbarProfile);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initViews();

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);

        currentUserId = sessionManager.getUserId();

        loadUserData();

        btnOpenProfileUpdate.setOnClickListener(v -> {
            startActivity(new Intent(ProfileActivity.this, ProfileUpdateActivity.class));
        });
    }

    private void initViews() {
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar);
        tvProfileLastName = findViewById(R.id.tvProfileLastName);
        tvProfileFirstName = findViewById(R.id.tvProfileFirstName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileDob = findViewById(R.id.tvProfileDob);
        tvProfileNeighborhood = findViewById(R.id.tvProfileNeighborhood);
        tvProfileCity = findViewById(R.id.tvProfileCity);
        tvProfileCountry = findViewById(R.id.tvProfileCountry);
        btnOpenProfileUpdate = findViewById(R.id.btnOpenProfileUpdate);
    }

    private void loadUserData() {
        User user = userRepository.getUserById(currentUserId);
        if (user != null) {
            tvProfileLastName.setText("Nom : " + (user.getLastName() != null ? user.getLastName() : user.getName()));
            tvProfileFirstName.setText("Prénom : " + (user.getFirstName() != null ? user.getFirstName() : "Non renseigné"));
            tvProfileEmail.setText("Email : " + user.getEmail());
            tvProfileDob.setText("Date de naissance : " + (user.getDob() != null && !user.getDob().isEmpty() ? user.getDob() : "Non renseignée"));
            tvProfileNeighborhood.setText("Quartier : " + (user.getNeighborhood() != null && !user.getNeighborhood().isEmpty() ? user.getNeighborhood() : "Non renseigné"));
            tvProfileCity.setText("Ville : " + (user.getCity() != null && !user.getCity().isEmpty() ? user.getCity() : "Non renseignée"));
            tvProfileCountry.setText("Pays : " + (user.getCountry() != null && !user.getCountry().isEmpty() ? user.getCountry() : "Non renseigné"));

            ImageUtils.loadProfileImage(this, user.getProfileImage(), imgProfileAvatar);
        } else {
            tvProfileLastName.setText("Nom : " + sessionManager.getName());
            tvProfileEmail.setText("Email : " + sessionManager.getEmail());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
