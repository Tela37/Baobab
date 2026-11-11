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
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import td.teladoum.baobab.repositories.FriendRepository;
import td.teladoum.baobab.repositories.PostRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité d'affichage du profil de l'utilisateur connecté.
 * Présente la photo de profil, les informations personnelles et permet d'accéder à la modification du profil.
 */
public class ProfileActivity extends AppCompatActivity {

    private ImageView imgProfileAvatar;
    private TextView tvProfileLastName, tvProfileEmail, tvProfileDob;
    private TextView genreProfilAct, professionProfileAct, statusProfileAct, matrimonialProfilAct;
    private TextView telephoneProfileAct, tvProfileNeighborhood, tvProfileCity, tvProfileCountry;
    private Button btnOpenProfileUpdate, publicationBtnProfilAct, amiBtnProfilAct, blocageBtnProfil;

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

        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        loadUserData();

        imgProfileAvatar.setOnClickListener(v -> {
            User user = userRepository.getUserById(currentUserId);
            if (user != null && user.getProfileImage() != null && !user.getProfileImage().isEmpty()) {
                showImagePreviewDialog(user.getProfileImage());
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
            ibClose.setOnClickListener(v -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        }

        dialog.show();
    }

    private void initViews() {
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar);
        tvProfileLastName = findViewById(R.id.tvProfileLastName);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileDob = findViewById(R.id.tvProfileDob);
        genreProfilAct = findViewById(R.id.genre_profil_act);
        professionProfileAct = findViewById(R.id.profession_profile_act);
        statusProfileAct = findViewById(R.id.status_profile_act);
        matrimonialProfilAct = findViewById(R.id.matrimonial_Profil_act);
        telephoneProfileAct = findViewById(R.id.telephone_profile_act);
        tvProfileNeighborhood = findViewById(R.id.tvProfileNeighborhood);
        tvProfileCity = findViewById(R.id.tvProfileCity);
        tvProfileCountry = findViewById(R.id.tvProfileCountry);
        btnOpenProfileUpdate = findViewById(R.id.btnOpenProfileUpdate);
        publicationBtnProfilAct = findViewById(R.id.publicationBtn_profil_act);
        amiBtnProfilAct = findViewById(R.id.amiBtn_profile_act);
        blocageBtnProfil = findViewById(R.id.blocageBtn_profil);

        if (btnOpenProfileUpdate != null) {
            btnOpenProfileUpdate.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, Mise_a_jour_Activity.class)));
        }
        if (publicationBtnProfilAct != null) {
            publicationBtnProfilAct.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, MesPublicationActivity.class)));
        }
        if (amiBtnProfilAct != null) {
            amiBtnProfilAct.setOnClickListener(v -> startActivity(new Intent(ProfileActivity.this, AmiActivity.class)));
        }
        if (blocageBtnProfil != null) {
            blocageBtnProfil.setOnClickListener(v -> es.dmoral.toasty.Toasty.info(ProfileActivity.this, "Gestion du blocage dans Paramètres", es.dmoral.toasty.Toasty.LENGTH_SHORT).show());
        }
    }

    private void loadUserData() {
        PostRepository postRepository = new PostRepository(this);
        FriendRepository friendRepository = new FriendRepository(this);

        int postsCount = postRepository.getPostsByUserId(currentUserId, currentUserId).size();
        int friendsCount = friendRepository.getFriendsList(currentUserId, null).size();
        int blockedCount = userRepository.getBlockedCount(currentUserId);

        if (publicationBtnProfilAct != null) {
            publicationBtnProfilAct.setText("Publications (" + postsCount + ")");
        }
        if (amiBtnProfilAct != null) {
            amiBtnProfilAct.setText("Ami(e)s (" + friendsCount + ")");
        }
        if (blocageBtnProfil != null) {
            blocageBtnProfil.setText("Bloqué(e)s (" + blockedCount + ")");
        }

        User user = userRepository.getUserById(currentUserId);
        if (user != null) {
            displayUserData(user);
        } else {
            if (tvProfileLastName != null) tvProfileLastName.setText(sessionManager.getName());
            if (tvProfileEmail != null) tvProfileEmail.setText(sessionManager.getEmail());
        }
    }

    private void displayUserData(User user) {
        if (user == null) return;
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String fullName = (lastName + " " + firstName).trim();
        if (fullName.isEmpty()) {
            fullName = user.getName() != null ? user.getName() : "Utilisateur";
        }
        if (tvProfileLastName != null) tvProfileLastName.setText(fullName);
        if (tvProfileEmail != null) tvProfileEmail.setText(user.getEmail() != null ? user.getEmail() : "Non renseigné");
        if (tvProfileDob != null) tvProfileDob.setText(user.getDob() != null && !user.getDob().isEmpty() ? user.getDob() : "Non renseignée");
        if (genreProfilAct != null) genreProfilAct.setText(user.getGender() != null ? user.getGender() : "");
        if (professionProfileAct != null) professionProfileAct.setText(user.getProfession() != null && !user.getProfession().isEmpty() ? user.getProfession() : "Non renseignée");
        if (statusProfileAct != null) {
            statusProfileAct.setText(user.getBio() != null && !user.getBio().isEmpty() ? user.getBio() : "Membre BaobabTD");
        }
        if (matrimonialProfilAct != null) matrimonialProfilAct.setText(user.getRelationshipStatus() != null && !user.getRelationshipStatus().isEmpty() ? user.getRelationshipStatus() : "Non renseignée");
        if (telephoneProfileAct != null) telephoneProfileAct.setText(user.getPhone() != null && !user.getPhone().isEmpty() ? user.getPhone() : "Non renseigné");
        if (tvProfileNeighborhood != null) tvProfileNeighborhood.setText(user.getNeighborhood() != null && !user.getNeighborhood().isEmpty() ? user.getNeighborhood() : "Non renseigné");
        if (tvProfileCity != null) tvProfileCity.setText(user.getCity() != null && !user.getCity().isEmpty() ? user.getCity() : "Non renseignée");
        if (tvProfileCountry != null) tvProfileCountry.setText(user.getCountry() != null && !user.getCountry().isEmpty() ? user.getCountry() : "Non renseigné");

        ImageUtils.loadProfileImage(this, user.getProfileImage(), imgProfileAvatar);
    }

    private com.google.firebase.database.ValueEventListener userListener;
    private com.google.firebase.database.DatabaseReference userRef;
    private com.google.firebase.firestore.ListenerRegistration firestoreUserListener;

    @Override
    protected void onResume() {
        super.onResume();
        loadUserData();
        startRealtimeUserListener();
        startFirestoreUserListener();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRealtimeUserListener();
        stopFirestoreUserListener();
    }

    private void startRealtimeUserListener() {
        if (currentUserId == -1) return;
        userRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("users").child(String.valueOf(currentUserId));
        userListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    UserRepository.cacheUser(user);
                    displayUserData(user);
                }
            }

            @Override
            public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {}
        };
        userRef.addValueEventListener(userListener);
    }

    private void stopRealtimeUserListener() {
        if (userRef != null && userListener != null) {
            userRef.removeEventListener(userListener);
        }
    }

    private void startFirestoreUserListener() {
        if (currentUserId == -1) return;
        firestoreUserListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .document(String.valueOf(currentUserId))
                .addSnapshotListener((documentSnapshot, error) -> {
                    if (error != null || documentSnapshot == null || !documentSnapshot.exists()) return;

                    String email = documentSnapshot.getString("email");
                    if (email != null && !email.trim().isEmpty()) {
                        Long idLong = documentSnapshot.getLong("id");
                        int id = idLong != null ? idLong.intValue() : currentUserId;

                        User user = new User();
                        user.setId(id);
                        user.setName(documentSnapshot.getString("name"));
                        user.setFirstName(documentSnapshot.getString("first_name"));
                        user.setLastName(documentSnapshot.getString("last_name"));
                        user.setEmail(email);
                        user.setPhone(documentSnapshot.getString("phone"));
                        user.setDob(documentSnapshot.getString("dob"));
                        user.setNeighborhood(documentSnapshot.getString("neighborhood"));
                        user.setCity(documentSnapshot.getString("city"));
                        user.setCountry(documentSnapshot.getString("country"));
                        user.setProfileImage(documentSnapshot.getString("profile_image"));
                        user.setProfession(documentSnapshot.getString("profession"));
                        user.setRelationshipStatus(documentSnapshot.getString("relationship_status"));
                        user.setGender(documentSnapshot.getString("gender"));
                        user.setBio(documentSnapshot.getString("bio"));
                        Boolean isAdmin = documentSnapshot.getBoolean("is_admin");
                        if (isAdmin != null) user.setAdmin(isAdmin);

                        UserRepository.cacheUser(user);
                        displayUserData(user);
                    }
                });
    }

    private void stopFirestoreUserListener() {
        if (firestoreUserListener != null) {
            firestoreUserListener.remove();
            firestoreUserListener = null;
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

