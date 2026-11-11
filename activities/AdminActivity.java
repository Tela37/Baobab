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

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.NotificationRepository;
import td.teladoum.baobab.repositories.PostRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Panneau d'administration pour examiner et modérer les signalements et gérer les utilisateurs.
 */
public class AdminActivity extends AppCompatActivity {

    private RecyclerView rvReports, rvUsers;
    private Button btnTabReports, btnTabUsers;
    private ReportsAdapter reportsAdapter;
    private UsersAdapter usersAdapter;
    private final List<PostRepository.ReportItem> reportsList = new ArrayList<>();
    private final List<User> usersList = new ArrayList<>();
    private PostRepository postRepository;
    private UserRepository userRepository;
    private NotificationRepository notificationRepository;

    private Uri selectedAdminVideoUri;
    private EditText etAdminVideoUrl;

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedAdminVideoUri = uri;
                    if (etAdminVideoUrl != null) {
                        etAdminVideoUrl.setText(uri.toString());
                    }
                    Toasty.info(this, "Vidéo sélectionnée depuis votre téléphone 🎬", Toasty.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        Toolbar toolbar = findViewById(R.id.toolbarAdmin);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userRepository = new UserRepository(this);
        notificationRepository = new NotificationRepository(this);
        SessionManager sessionManager = new SessionManager(this);
        User currentUser = userRepository.getUserById(sessionManager.getUserId());
        if (currentUser == null || !currentUser.isAdmin()) {
            Toasty.error(this, getString(R.string.toast_access_denied), Toasty.LENGTH_SHORT).show();
            finish();
            return;
        }

        postRepository = new PostRepository(this);

        SwitchMaterial switchSuperAdminControls = findViewById(R.id.switchSuperAdminControls);
        if (currentUser.getId() == userRepository.getSuperAdminId() || currentUser.getId() == 1) {
            switchSuperAdminControls.setVisibility(View.VISIBLE);
            switchSuperAdminControls.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (usersAdapter != null) {
                    usersAdapter.setShowAdminAndLimit(isChecked);
                }
            });
        }

        btnTabReports = findViewById(R.id.btnTabReports);
        btnTabUsers = findViewById(R.id.btnTabUsers);
        Button btnPublishAnnouncement = findViewById(R.id.btnPublishAnnouncement);
        if (btnPublishAnnouncement != null) {
            btnPublishAnnouncement.setOnClickListener(v -> showPublishAnnouncementDialog());
        }
        listenToAnnouncementState();
        Button btnPublishVideo = findViewById(R.id.btnPublishVideo);
        if (btnPublishVideo != null) {
            btnPublishVideo.setOnClickListener(v -> showPublishVideoDialog());
        }
        rvReports = findViewById(R.id.rvReports);
        rvUsers = findViewById(R.id.rvUsers);

        rvReports.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setLayoutManager(new LinearLayoutManager(this));

        reportsAdapter = new ReportsAdapter(reportsList, new OnReportActionListener() {
            @Override
            public void onDeletePost(PostRepository.ReportItem item) {
                new AlertDialog.Builder(AdminActivity.this)
                        .setTitle("Supprimer la publication 🗑️")
                        .setMessage("Voulez-vous supprimer ce post signalé ? Cette action est irréversible.")
                        .setPositiveButton("Supprimer", (dialog, which) -> {
                            postRepository.deletePost(item.postId, item.reporterId);
                            postRepository.deleteReport(item.reportId);
                            Toasty.success(AdminActivity.this, "Publication supprimée", Toasty.LENGTH_SHORT).show();
                            loadReports();
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            }

            @Override
            public void onDismiss(PostRepository.ReportItem item) {
                postRepository.deleteReport(item.reportId);
                Toasty.info(AdminActivity.this, "Signalement ignoré", Toasty.LENGTH_SHORT).show();
                loadReports();
            }
        });

        usersAdapter = new UsersAdapter(usersList, new OnUserActionListener() {
            @Override
            public void onDeleteUser(User user) {
                int superAdminId = userRepository.getSuperAdminId();
                if (user.getId() == superAdminId || user.getId() == 1) {
                    if (sessionManager.getUserId() != superAdminId) {
                        notificationRepository.addNotification(superAdminId, sessionManager.getUserId(), "ADMIN_SECURITY_ALERT", "USER", superAdminId, "⚠️ L'administrateur " + currentUser.getName() + " a tenté de supprimer l'Administrateur principal (Admin 1).");
                    }
                    Toasty.error(AdminActivity.this, "Impossible de supprimer l'Administrateur principal (Super Admin 1) 🚫", Toasty.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(AdminActivity.this)
                        .setTitle("Supprimer le compte ⚠️")
                        .setMessage("Voulez-vous vraiment supprimer le compte de " + user.getName() + " ? Cette action supprimera définitivement toutes ses données.")
                        .setPositiveButton("Supprimer", (dialog, which) -> {
                            userRepository.deleteUser(user.getId());
                            Toasty.success(AdminActivity.this, "Compte supprimé", Toasty.LENGTH_SHORT).show();
                            loadUsers();
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            }

            @Override
            public void onSuspendUser(User user) {
                int superAdminId = userRepository.getSuperAdminId();
                if (user.getId() == superAdminId || user.getId() == 1) {
                    if (sessionManager.getUserId() != superAdminId) {
                        notificationRepository.addNotification(superAdminId, sessionManager.getUserId(), "ADMIN_SECURITY_ALERT", "USER", superAdminId, "⚠️ L'administrateur " + currentUser.getName() + " a tenté de suspendre l'Administrateur principal (Admin 1).");
                    }
                    Toasty.error(AdminActivity.this, "Impossible de suspendre l'Administrateur principal (Super Admin 1) 🚫", Toasty.LENGTH_SHORT).show();
                    return;
                }
                boolean currentlySuspended = user.isSuspended();
                if (currentlySuspended) {
                    userRepository.suspendUser(user.getId(), false, null);
                    Toasty.success(AdminActivity.this, "Compte réactivé", Toasty.LENGTH_SHORT).show();
                    loadUsers();
                } else {
                    final EditText inputReason = new EditText(AdminActivity.this);
                    inputReason.setHint("Raison de la suspension (ex: Spam)");
                    new AlertDialog.Builder(AdminActivity.this)
                            .setTitle("Suspendre l'utilisateur 🚫")
                            .setView(inputReason)
                            .setPositiveButton("Suspendre", (dialog, which) -> {
                                String reason = inputReason.getText().toString().trim();
                                userRepository.suspendUser(user.getId(), true, reason.isEmpty() ? "Violation des règles" : reason);
                                Toasty.warning(AdminActivity.this, "Utilisateur suspendu", Toasty.LENGTH_SHORT).show();
                                loadUsers();
                            })
                            .setNegativeButton("Annuler", null)
                            .show();
                }
            }

            @Override
            public void onLimitShare(User user) {
                int superAdminId = userRepository.getSuperAdminId();
                if (user.getId() == superAdminId || user.getId() == 1) {
                    if (sessionManager.getUserId() != superAdminId) {
                        notificationRepository.addNotification(superAdminId, sessionManager.getUserId(), "ADMIN_SECURITY_ALERT", "USER", superAdminId, "⚠️ L'administrateur " + currentUser.getName() + " a tenté de limiter le partage de l'Administrateur principal (Admin 1).");
                    }
                    Toasty.error(AdminActivity.this, "Impossible de restreindre l'Administrateur principal (Super Admin 1) 🚫", Toasty.LENGTH_SHORT).show();
                    return;
                }
                String[] options = {"1 Heure", "24 Heures", "7 Jours", "30 Jours", "Permanent", "Retirer la limitation"};
                new AlertDialog.Builder(AdminActivity.this)
                        .setTitle("Limiter le partage pour " + user.getName())
                        .setItems(options, (dialog, which) -> {
                            Calendar calendar = Calendar.getInstance();
                            String untilDateStr = null;
                            String msg = "Limitation retirée";
                            switch (which) {
                                case 0:
                                    calendar.add(Calendar.HOUR_OF_DAY, 1);
                                    msg = "Partage limité pour 1 heure";
                                    break;
                                case 1:
                                    calendar.add(Calendar.DAY_OF_YEAR, 1);
                                    msg = "Partage limité pour 24 heures";
                                    break;
                                case 2:
                                    calendar.add(Calendar.DAY_OF_YEAR, 7);
                                    msg = "Partage limité pour 7 jours";
                                    break;
                                case 3:
                                    calendar.add(Calendar.DAY_OF_YEAR, 30);
                                    msg = "Partage limité pour 30 jours";
                                    break;
                                case 4:
                                    calendar.add(Calendar.YEAR, 100);
                                    msg = "Partage limité de façon permanente";
                                    break;
                                case 5:
                                    untilDateStr = null;
                                    msg = "Limitation de partage retirée";
                                    break;
                            }
                            if (which < 5) {
                                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                                untilDateStr = sdf.format(calendar.getTime());
                            }
                            userRepository.limitUserSharing(user.getId(), untilDateStr);
                            Toasty.info(AdminActivity.this, msg, Toasty.LENGTH_SHORT).show();
                            loadUsers();
                        })
                        .show();
            }

            @Override
            public void onToggleAdmin(User user) {
                int superAdminId = userRepository.getSuperAdminId();
                if (user.getId() == superAdminId || user.getId() == 1) {
                    if (sessionManager.getUserId() != superAdminId) {
                        notificationRepository.addNotification(superAdminId, sessionManager.getUserId(), "ADMIN_SECURITY_ALERT", "USER", superAdminId, "⚠️ L'administrateur " + currentUser.getName() + " a tenté de retirer les privilèges de l'Administrateur principal (Admin 1).");
                    }
                    Toasty.error(AdminActivity.this, "Impossible de rétrograder l'Administrateur principal (Super Admin 1) 🚫", Toasty.LENGTH_SHORT).show();
                    return;
                }
                boolean newAdminStatus = !user.isAdmin();
                userRepository.setAdmin(user.getId(), newAdminStatus);
                Toasty.success(AdminActivity.this, newAdminStatus ? user.getName() + " est désormais Administrateur 🛡️" : user.getName() + " n'est plus Administrateur", Toasty.LENGTH_SHORT).show();
                loadUsers();
            }
        });

        rvReports.setAdapter(reportsAdapter);
        rvUsers.setAdapter(usersAdapter);

        btnTabReports.setOnClickListener(v -> {
            btnTabReports.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#B71C1C")));
            btnTabReports.setTextColor(Color.WHITE);
            btnTabUsers.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            btnTabUsers.setTextColor(Color.parseColor("#424242"));
            rvReports.setVisibility(View.VISIBLE);
            rvUsers.setVisibility(View.GONE);
            loadReports();
        });

        btnTabUsers.setOnClickListener(v -> {
            btnTabUsers.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#B71C1C")));
            btnTabUsers.setTextColor(Color.WHITE);
            btnTabReports.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#E0E0E0")));
            btnTabReports.setTextColor(Color.parseColor("#424242"));
            rvUsers.setVisibility(View.VISIBLE);
            rvReports.setVisibility(View.GONE);
            loadUsers();
        });

        userRepository.syncAllUsersFromCloud(() -> {
            loadReports();
            loadUsers();
        });
    }

    private void loadReports() {
        List<PostRepository.ReportItem> list = postRepository.getReports();
        reportsList.clear();
        reportsList.addAll(list);
        reportsAdapter.notifyDataSetChanged();
    }

    private void loadUsers() {
        List<User> list = userRepository.getAllUsers();
        usersList.clear();
        usersList.addAll(list);
        usersAdapter.notifyDataSetChanged();
        updateUserStatistics();
    }

    private void updateUserStatistics() {
        TextView tvAdminStats = findViewById(R.id.tvAdminStats);
        if (tvAdminStats == null) return;

        List<User> list = userRepository.getAllUsers();
        int total = list.size();
        int male = 0;
        int female = 0;

        int dailyActive = 0;   // Actifs aujourd'hui / 24h
        int monthlyActive = 0; // Actifs ce mois-ci / 30j
        int yearlyActive = 0;  // Actifs cette année / 365j

        List<String> onlineUserNames = new ArrayList<>();

        long now = System.currentTimeMillis();
        long oneDayMs = 24L * 3600 * 1000;
        long thirtyDaysMs = 30L * oneDayMs;
        long yearMs = 365L * oneDayMs;

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());

        for (User u : list) {
            String g = u.getGender();
            if (g != null) {
                String gl = g.toLowerCase().trim();
                if (gl.contains("masculin") || gl.equals("m") || gl.equals("homme")) {
                    male++;
                } else if (gl.contains("féminin") || gl.contains("feminin") || gl.equals("f") || gl.equals("femme")) {
                    female++;
                }
            }

            // Utilisateurs en ligne
            if (u.isOnline()) {
                dailyActive++;
                monthlyActive++;
                yearlyActive++;
                String name = (u.getName() != null && !u.getName().trim().isEmpty()) ? u.getName() : u.getEmail();
                if (name != null) onlineUserNames.add(name);
            } else if (u.getLastSeen() != null && !u.getLastSeen().trim().isEmpty()) {
                try {
                    java.util.Date lastSeenDate = sdf.parse(u.getLastSeen());
                    if (lastSeenDate != null) {
                        long diff = now - lastSeenDate.getTime();
                        if (diff <= oneDayMs) {
                            dailyActive++;
                        }
                        if (diff <= thirtyDaysMs) {
                            monthlyActive++;
                        }
                        if (diff <= yearMs) {
                            yearlyActive++;
                        }
                    }
                } catch (Exception ignored) {
                    yearlyActive++;
                }
            } else {
                yearlyActive++;
            }
        }

        StringBuilder onlineListBuilder = new StringBuilder();
        if (onlineUserNames.isEmpty()) {
            onlineListBuilder.append("Aucun utilisateur en ligne pour le moment");
        } else {
            for (int i = 0; i < onlineUserNames.size(); i++) {
                if (i > 0) onlineListBuilder.append(", ");
                onlineListBuilder.append(onlineUserNames.get(i));
            }
        }

        String statsText = "📊 Statistiques de l'application :\n" +
                "• Total des abonnés : " + total + " (👨 " + male + " | 👩 " + female + ")\n" +
                "• 🟢 En ligne actuellement (" + onlineUserNames.size() + ") : " + onlineListBuilder.toString() + "\n" +
                "• 🟢 Connectés aujourd'hui (Journalier) : " + dailyActive + "\n" +
                "• 📅 Connectés ce mois-ci (Mensuel) : " + monthlyActive + "\n" +
                "• 🗓️ Connectés cette année (Annuel) : " + yearlyActive;

        tvAdminStats.setText(statsText);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private String currentAnnouncementText = null;

    private void listenToAnnouncementState() {
        Button btnPublishAnnouncement = findViewById(R.id.btnPublishAnnouncement);
        View layoutExistingAnnouncement = findViewById(R.id.layoutExistingAnnouncement);
        TextView tvAnnouncementContent = findViewById(R.id.tvAnnouncementContent);
        Button btnEditAnnouncement = findViewById(R.id.btnEditAnnouncement);
        Button btnDeleteAnnouncement = findViewById(R.id.btnDeleteAnnouncement);

        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("app_announcement")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        String note = snapshot.getValue(String.class);
                        currentAnnouncementText = note;

                        if (note != null && !note.trim().isEmpty()) {
                            if (btnPublishAnnouncement != null) btnPublishAnnouncement.setVisibility(View.GONE);
                            if (layoutExistingAnnouncement != null) layoutExistingAnnouncement.setVisibility(View.VISIBLE);
                            if (tvAnnouncementContent != null) tvAnnouncementContent.setText(note);
                        } else {
                            if (btnPublishAnnouncement != null) btnPublishAnnouncement.setVisibility(View.VISIBLE);
                            if (layoutExistingAnnouncement != null) layoutExistingAnnouncement.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                });

        if (btnEditAnnouncement != null) {
            btnEditAnnouncement.setOnClickListener(v -> showEditAnnouncementDialog());
        }

        if (btnDeleteAnnouncement != null) {
            btnDeleteAnnouncement.setOnClickListener(v -> showDeleteAnnouncementConfirmDialog());
        }
    }

    private void showEditAnnouncementDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.admin_announcement_hint));
        if (currentAnnouncementText != null) {
            input.setText(currentAnnouncementText);
        }

        new AlertDialog.Builder(this)
                .setTitle("Modifier l'annonce ✏️")
                .setView(input)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    if (!note.isEmpty()) {
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("app_announcement")
                                .setValue(note)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toasty.success(this, "Annonce mise à jour avec succès ! 🚀", Toasty.LENGTH_SHORT).show();
                                    } else {
                                        Toasty.error(this, "Erreur lors de la mise à jour", Toasty.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showDeleteAnnouncementConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer l'annonce 🗑️")
                .setMessage("Voulez-vous vraiment effacer et supprimer l'annonce actuellement en ligne ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    com.google.firebase.database.FirebaseDatabase.getInstance().getReference("app_announcement")
                            .removeValue()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    Toasty.success(this, "Annonce supprimée avec succès ! 🗑️", Toasty.LENGTH_SHORT).show();
                                } else {
                                    Toasty.error(this, "Erreur lors de la suppression de l'annonce", Toasty.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showPublishAnnouncementDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.admin_announcement_hint));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.admin_announcement_title))
                .setView(input)
                .setPositiveButton(getString(R.string.publish_action), (dialog, which) -> {
                    String note = input.getText().toString().trim();
                    if (!note.isEmpty()) {
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("app_announcement")
                                .setValue(note)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        Toasty.success(this, "Annonce publiée avec succès ! 🚀", Toasty.LENGTH_SHORT).show();
                                    } else {
                                        Toasty.error(this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
                                    }
                                });
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showPublishVideoDialog() {
        selectedAdminVideoUri = null;
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        EditText etTitle = new EditText(this);
        etTitle.setHint("Titre du film/série/vidéo...");

        EditText etDescription = new EditText(this);
        etDescription.setHint("Description...");

        Spinner spinnerCategory = new Spinner(this);
        String[] categories = new String[]{"Films & Séries", "Tutoriels", "Culture", "Divertissement"};
        android.widget.ArrayAdapter<String> catAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories);
        spinnerCategory.setAdapter(catAdapter);

        Button btnPickVideo = new Button(this);
        btnPickVideo.setText("📁 Choisir une vidéo depuis le téléphone");
        btnPickVideo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2E7D32")));
        btnPickVideo.setTextColor(Color.WHITE);

        EditText etUrl = new EditText(this);
        etUrl.setHint("Ou coller une URL MP4 distante...");
        etAdminVideoUrl = etUrl;

        ProgressBar uploadProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        uploadProgress.setIndeterminate(true);
        uploadProgress.setVisibility(View.GONE);

        btnPickVideo.setOnClickListener(v -> videoPickerLauncher.launch("video/*"));

        layout.addView(etTitle);
        layout.addView(etDescription);
        layout.addView(spinnerCategory);
        layout.addView(btnPickVideo);
        layout.addView(etUrl);
        layout.addView(uploadProgress);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Publier un Film / Série / Vidéo 🎥")
                .setView(layout)
                .setPositiveButton("Publier", null)
                .setNegativeButton("Annuler", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            String desc = etDescription.getText().toString().trim();
            String url = etUrl.getText().toString().trim();
            String category = spinnerCategory.getSelectedItem() != null ? spinnerCategory.getSelectedItem().toString() : "Films & Séries";

            if (selectedAdminVideoUri != null) {
                uploadProgress.setVisibility(View.VISIBLE);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                FirebaseHelper.getInstance().uploadMedia("video_contents", selectedAdminVideoUri, new FirebaseHelper.OnUploadCompleteListener() {
                    @Override
                    public void onSuccess(String downloadUrl) {
                        int videoId = Math.abs((title + "_" + System.currentTimeMillis()).hashCode());
                        VideoModel model = new VideoModel(videoId, title.isEmpty() ? "Vidéo BaobabTD" : title, desc, downloadUrl, category, TimeUtils.getCurrentLocalDateTime());
                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("video_contents")
                                .child(String.valueOf(videoId))
                                .setValue(model)
                                .addOnCompleteListener(task -> {
                                    uploadProgress.setVisibility(View.GONE);
                                    if (task.isSuccessful()) {
                                        Toasty.success(AdminActivity.this, "Vidéo téléversée et publiée avec succès ! 🚀", Toasty.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    } else {
                                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                                        Toasty.error(AdminActivity.this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        uploadProgress.setVisibility(View.GONE);
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        Toasty.error(AdminActivity.this, "Erreur de téléversement de la vidéo", Toasty.LENGTH_SHORT).show();
                    }
                });
            } else if (!url.isEmpty()) {
                int videoId = Math.abs((title + "_" + System.currentTimeMillis()).hashCode());
                VideoModel model = new VideoModel(videoId, title.isEmpty() ? "Vidéo BaobabTD" : title, desc, url, category, TimeUtils.getCurrentLocalDateTime());
                com.google.firebase.database.FirebaseDatabase.getInstance().getReference("video_contents")
                        .child(String.valueOf(videoId))
                        .setValue(model)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                Toasty.success(AdminActivity.this, "Vidéo publiée avec succès ! 🚀", Toasty.LENGTH_SHORT).show();
                                dialog.dismiss();
                            } else {
                                Toasty.error(AdminActivity.this, "Erreur lors de la publication de la vidéo", Toasty.LENGTH_SHORT).show();
                            }
                        });
            } else {
                Toasty.warning(AdminActivity.this, "Veuillez choisir une vidéo ou saisir une URL", Toasty.LENGTH_SHORT).show();
            }
        });
    }

    private interface OnReportActionListener {
        void onDeletePost(PostRepository.ReportItem item);
        void onDismiss(PostRepository.ReportItem item);
    }

    private interface OnUserActionListener {
        void onDeleteUser(User user);
        void onSuspendUser(User user);
        void onLimitShare(User user);
        void onToggleAdmin(User user);
    }

    private static class ReportsAdapter extends RecyclerView.Adapter<ReportsAdapter.ViewHolder> {
        private final List<PostRepository.ReportItem> list;
        private final OnReportActionListener listener;

        public ReportsAdapter(List<PostRepository.ReportItem> list, OnReportActionListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PostRepository.ReportItem item = list.get(position);
            holder.tvReportReporter.setText("Signalé par : " + (item.reporterName != null ? item.reporterName : "Utilisateur"));
            holder.tvReportReason.setText("Raison : " + (item.reason != null && !item.reason.isEmpty() ? item.reason : "Contenu inapproprié"));
            holder.tvReportContent.setText(item.postContent != null ? item.postContent : "(Média ou contenu vide)");

            if (item.imagePath != null && !item.imagePath.trim().isEmpty()) {
                holder.imgReportImage.setVisibility(View.VISIBLE);
                ImageUtils.loadFullImage(holder.itemView.getContext(), item.imagePath, holder.imgReportImage);
            } else if (item.videoPath != null && !item.videoPath.trim().isEmpty()) {
                holder.imgReportImage.setVisibility(View.VISIBLE);
                ImageUtils.loadVideoThumbnail(holder.itemView.getContext(), item.videoPath, holder.imgReportImage);
            } else {
                holder.imgReportImage.setVisibility(View.GONE);
            }

            holder.btnDeletePost.setOnClickListener(v -> {
                if (listener != null) listener.onDeletePost(item);
            });

            holder.btnDismissReport.setOnClickListener(v -> {
                if (listener != null) listener.onDismiss(item);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvReportReporter, tvReportReason, tvReportContent;
            ImageView imgReportImage;
            Button btnDeletePost, btnDismissReport;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvReportReporter = itemView.findViewById(R.id.tvReportReporter);
                tvReportReason = itemView.findViewById(R.id.tvReportReason);
                tvReportContent = itemView.findViewById(R.id.tvReportContent);
                imgReportImage = itemView.findViewById(R.id.imgReportImage);
                btnDeletePost = itemView.findViewById(R.id.btnDeletePost);
                btnDismissReport = itemView.findViewById(R.id.btnDismissReport);
            }
        }
    }

    private static class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.ViewHolder> {
        private final List<User> list;
        private final OnUserActionListener listener;
        private boolean showAdminAndLimit = false;

        public UsersAdapter(List<User> list, OnUserActionListener listener) {
            this.list = list;
            this.listener = listener;
        }

        public void setShowAdminAndLimit(boolean show) {
            this.showAdminAndLimit = show;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_user, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = list.get(position);
            holder.tvUserName.setText(user.getName() != null ? user.getName() : "Utilisateur #" + user.getId());
            holder.tvUserEmail.setText(user.getEmail() != null ? user.getEmail() : "");

            if (user.getId() == 1) {
                holder.tvUserStatus.setText("👑 Super Admin");
                holder.tvUserStatus.setBackgroundColor(Color.parseColor("#FFF8E1"));
                holder.tvUserStatus.setTextColor(Color.parseColor("#F57F17"));
                holder.btnToggleAdmin.setVisibility(View.GONE);
                holder.btnSuspendUser.setVisibility(View.GONE);
                holder.btnDeleteUser.setVisibility(View.GONE);
                holder.btnLimitShare.setVisibility(View.GONE);
            } else {
                holder.btnToggleAdmin.setVisibility(showAdminAndLimit ? View.VISIBLE : View.GONE);
                holder.btnLimitShare.setVisibility(showAdminAndLimit ? View.VISIBLE : View.GONE);
                holder.btnSuspendUser.setVisibility(View.VISIBLE);
                holder.btnDeleteUser.setVisibility(View.VISIBLE);

                if (user.isAdmin()) {
                    holder.tvUserStatus.setText("🛡️ Admin");
                    holder.tvUserStatus.setBackgroundColor(Color.parseColor("#E3F2FD"));
                    holder.tvUserStatus.setTextColor(Color.parseColor("#1565C0"));
                    holder.btnToggleAdmin.setText("Rétrograder");
                } else if (user.isSuspended()) {
                    holder.tvUserStatus.setText("Suspendu");
                    holder.tvUserStatus.setBackgroundColor(Color.parseColor("#FFCDD2"));
                    holder.tvUserStatus.setTextColor(Color.parseColor("#B71C1C"));
                    holder.btnSuspendUser.setText("Réactiver");
                    holder.btnToggleAdmin.setText("Promouvoir");
                } else {
                    holder.tvUserStatus.setText("Actif");
                    holder.tvUserStatus.setBackgroundColor(Color.parseColor("#C8E6C9"));
                    holder.tvUserStatus.setTextColor(Color.parseColor("#2E7D32"));
                    holder.btnSuspendUser.setText("Suspendre");
                    holder.btnToggleAdmin.setText("Promouvoir");
                }
            }

            StringBuilder restrictionText = new StringBuilder();
            if (user.isSuspended() && user.getSuspensionReason() != null) {
                restrictionText.append("Raison suspension: ").append(user.getSuspensionReason());
            }
            if (user.getShareLimitedUntil() != null) {
                if (restrictionText.length() > 0) restrictionText.append(" | ");
                restrictionText.append("Partage limité jusqu'au: ").append(user.getShareLimitedUntil());
            }

            if (restrictionText.length() > 0) {
                holder.tvUserRestriction.setText(restrictionText.toString());
                holder.tvUserRestriction.setVisibility(View.VISIBLE);
            } else {
                holder.tvUserRestriction.setVisibility(View.GONE);
            }

            holder.btnDeleteUser.setOnClickListener(v -> {
                if (listener != null) listener.onDeleteUser(user);
            });

            holder.btnSuspendUser.setOnClickListener(v -> {
                if (listener != null) listener.onSuspendUser(user);
            });

            holder.btnLimitShare.setOnClickListener(v -> {
                if (listener != null) listener.onLimitShare(user);
            });

            holder.btnToggleAdmin.setOnClickListener(v -> {
                if (listener != null) listener.onToggleAdmin(user);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvUserName, tvUserEmail, tvUserStatus, tvUserRestriction;
            Button btnDeleteUser, btnSuspendUser, btnLimitShare, btnToggleAdmin;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvUserName = itemView.findViewById(R.id.tvUserName);
                tvUserEmail = itemView.findViewById(R.id.tvUserEmail);
                tvUserStatus = itemView.findViewById(R.id.tvUserStatus);
                tvUserRestriction = itemView.findViewById(R.id.tvUserRestriction);
                btnDeleteUser = itemView.findViewById(R.id.btnDeleteUser);
                btnSuspendUser = itemView.findViewById(R.id.btnSuspendUser);
                btnLimitShare = itemView.findViewById(R.id.btnLimitShare);
                btnToggleAdmin = itemView.findViewById(R.id.btnToggleAdmin);
            }
        }
    }
}

