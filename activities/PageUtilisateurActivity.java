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
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Collections;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.ConversationRepository;
import td.teladoum.baobab.repositories.PostRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité de page de profil d'un utilisateur (Style Facebook).
 * Affiche l'image et le nom du profil. Un clic sur le nom ouvre une boîte de dialogue
 * avec les informations personnelles (email, dob, lieu avec 🔒) et le bouton Bloquer/Débloquer.
 * Un clic sur l'image l'agrandit. Contient aussi une boîte pour écrire et la liste des publications.
 */
public class PageUtilisateurActivity extends AppCompatActivity {

    private ImageView imgProfileUserAvatar;
    private TextView tvProfileUserName;

    private EditText etPostContent;
    private View layoutSelectedImage;
    private ImageView imgSelectedPostImage;
    private ImageButton btnRemoveSelectedImage;
    private ImageButton ibAttachImage;
    private Button btnPublishPost;
    private RecyclerView rvUserPosts;

    private UserRepository userRepository;
    private PostRepository postRepository;

    private int targetUserId = -1;
    private int currentUserId;
    private String savedPostImagePath;

    private PostAdapter postAdapter;
    private final List<Post> userPostsList = new ArrayList<>();
    private AlertDialog activeImageDialog;
    private AlertDialog activeVideoDialog;

    private final ActivityResultLauncher<CropImageContractOptions> imagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        savedPostImagePath = ImageUtils.saveImageToInternalStorage(this, uri);
                        if (savedPostImagePath != null) {
                            imgSelectedPostImage.setImageURI(uri);
                            layoutSelectedImage.setVisibility(View.VISIBLE);
                        } else {
                            Toasty.error(this, "Erreur lors du chargement de l'image", Toasty.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_page_utilisateur);

        Toolbar toolbar = findViewById(R.id.toolbarPageUtilisateur);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        userRepository = new UserRepository(this);
        postRepository = new PostRepository(this);
        SessionManager sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        targetUserId = getIntent().getIntExtra("user_id", -1);
        String targetName = getIntent().getStringExtra("user_name");
        String targetImg = getIntent().getStringExtra("user_profile_image");

        if (targetUserId == -1) {
            String targetEmail = getIntent().getStringExtra("user_email");
            if (targetEmail != null && !targetEmail.isEmpty()) {
                User u = userRepository.getUserByEmail(targetEmail);
                if (u != null) targetUserId = u.getId();
            }
        }

        initViews();
        setupRecyclerView();
        loadUserInfo(targetName, targetImg);
        userRepository.syncAllUsersFromCloud(() -> {
            loadUserInfo(targetName, targetImg);
        });
        loadUserPosts();

        btnPublishPost.setOnClickListener(v -> publishPostOnProfile());
        ibAttachImage.setOnClickListener(v -> launchImageCropper());
        btnRemoveSelectedImage.setOnClickListener(v -> {
            savedPostImagePath = null;
            layoutSelectedImage.setVisibility(View.GONE);
        });

        imgProfileUserAvatar.setOnClickListener(v -> {
            User user = userRepository.getUserById(targetUserId);
            String imgPath = user != null ? user.getProfileImage() : targetImg;
            if (imgPath != null && !imgPath.isEmpty()) {
                showImagePreviewDialog(imgPath);
            }
        });

        tvProfileUserName.setOnClickListener(v -> showUserProfileDetailsDialog());
    }

    private void initViews() {
        imgProfileUserAvatar = findViewById(R.id.imgProfileUserAvatar);
        tvProfileUserName = findViewById(R.id.tvProfileUserName);

        etPostContent = findViewById(R.id.etPostContent);
        layoutSelectedImage = findViewById(R.id.layoutSelectedImage);
        imgSelectedPostImage = findViewById(R.id.imgSelectedPostImage);
        btnRemoveSelectedImage = findViewById(R.id.btnRemoveSelectedImage);
        ibAttachImage = findViewById(R.id.ibAttachImage);
        btnPublishPost = findViewById(R.id.btnPublishPost);
        rvUserPosts = findViewById(R.id.rvUserPosts);
    }

    private void setupRecyclerView() {
        rvUserPosts.setLayoutManager(new LinearLayoutManager(this));
        postAdapter = new PostAdapter(userPostsList, currentUserId, new PostAdapter.OnPostClickListener() {
            @Override
            public void onLikeClick(Post post) {
                postRepository.toggleLikePost(post.getId(), currentUserId);
                loadUserPosts();
            }

            @Override
            public void onReactionClick(Post post, String reactionType) {
                postRepository.setPostReaction(post.getId(), currentUserId, reactionType);
                loadUserPosts();
            }

            @Override
            public void onCommentClick(Post post) {
                Intent intent = new Intent(PageUtilisateurActivity.this, CommenterActivity.class);
                intent.putExtra("post_id", post.getId());
                startActivity(intent);
            }

            @Override
            public void onShareClick(Post post) {
                postRepository.recordPostShare(post.getId(), currentUserId);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Publication de " + post.getAuthorName() + " sur BaobabTD :\n" + (post.getContent() != null ? post.getContent() : ""));
                startActivity(Intent.createChooser(shareIntent, "Partager via"));
                loadUserPosts();
            }

            @Override
            public void onReportClick(Post post) {
                final EditText etReason = new EditText(PageUtilisateurActivity.this);
                etReason.setHint("Raison du signalement (ex: contenu inadapté)");
                new AlertDialog.Builder(PageUtilisateurActivity.this)
                        .setTitle("Signaler la publication 🚩")
                        .setMessage("Voulez-vous signaler cette publication aux modérateurs ?")
                        .setView(etReason)
                        .setPositiveButton("Signaler", (dialog, which) -> {
                            String reason = etReason.getText().toString().trim();
                            boolean reported = postRepository.reportPost(post.getId(), currentUserId, reason);
                            if (reported) {
                                Toasty.success(PageUtilisateurActivity.this, "Publication signalée aux modérateurs", Toasty.LENGTH_SHORT).show();
                            } else {
                                Toasty.error(PageUtilisateurActivity.this, "Erreur lors du signalement", Toasty.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            }

            @Override
            public void onEditClick(Post post) {
            }

            @Override
            public void onDeleteClick(Post post) {
                if (post.getUserId() == currentUserId || targetUserId == currentUserId) {
                    postRepository.deletePost(post.getId(), currentUserId);
                    loadUserPosts();
                }
            }

            @Override
            public void onImageClick(String imagePath) {
                showImagePreviewDialog(imagePath);
            }

            @Override
            public void onImageClick(Post post) {
                showImagePreviewDialog(post);
            }

            @Override
            public void onVideoClick(String videoPath) {
                showVideoPlayerDialog(videoPath);
            }
        });
        rvUserPosts.setAdapter(postAdapter);
    }

    private void loadUserInfo(String fallbackName, String fallbackImg) {
        User user = userRepository.getUserById(targetUserId);
        String name = (user != null && user.getName() != null && !user.getName().trim().isEmpty()) ? user.getName() : fallbackName;
        String imgPath = (user != null && user.getProfileImage() != null && !user.getProfileImage().trim().isEmpty()) ? user.getProfileImage() : fallbackImg;

        tvProfileUserName.setText(name != null && !name.trim().isEmpty() ? name : "Utilisateur");
        if (imgPath != null && !imgPath.trim().isEmpty()) {
            ImageUtils.loadProfileImage(this, imgPath, imgProfileUserAvatar);
        } else {
            imgProfileUserAvatar.setImageResource(R.drawable.ic_profile);
        }
    }

    private void showUserProfileDetailsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_user_profile_details, null);
        ImageView imgDialogAvatar = dialogView.findViewById(R.id.imgProfileAvatar);
        TextView tvDialogName = dialogView.findViewById(R.id.tvDialogName);
        TextView tvDialogStatus = dialogView.findViewById(R.id.status_person_profile);
        TextView tvDialogDob = dialogView.findViewById(R.id.tvDialogDob);
        TextView tvDialogGender = dialogView.findViewById(R.id.genre_person_profile);
        TextView tvDialogProfession = dialogView.findViewById(R.id.profession_person_profile);
        TextView tvDialogMatrimonial = dialogView.findViewById(R.id.matrimonial_person_profile);
        TextView tvDialogEmail = dialogView.findViewById(R.id.tvDialogEmail);
        TextView tvPhone = dialogView.findViewById(R.id.phone_person_profile);
        TextView tvDialogNeighborhood = dialogView.findViewById(R.id.tvDialogNeighborhood);
        TextView tvDialogCity = dialogView.findViewById(R.id.tvDialogCity);
        TextView tvDialogCountry = dialogView.findViewById(R.id.tvDialogCountry);
        Button btnDialogBlockUser = dialogView.findViewById(R.id.btnBlockUser);
        Toolbar toolbar = dialogView.findViewById(R.id.toolbarProfileUser2);

        User user = userRepository.getUserById(targetUserId);
        String name = user != null ? user.getName() : getIntent().getStringExtra("user_name");
        String img = user != null ? user.getProfileImage() : getIntent().getStringExtra("user_profile_image");

        if (tvDialogName != null) tvDialogName.setText(name != null ? name : "Utilisateur");
        if (imgDialogAvatar != null) ImageUtils.loadProfileImage(this, img, imgDialogAvatar);

        boolean isSelf = (targetUserId == currentUserId);

        if (user == null) {
            if (tvDialogEmail != null) tvDialogEmail.setText("Non disponible");
            if (tvDialogDob != null) tvDialogDob.setText("Non disponible");
            if (tvDialogGender != null) tvDialogGender.setText("Non disponible");
            if (tvDialogProfession != null) tvDialogProfession.setText("Non disponible");
            if (tvDialogMatrimonial != null) tvDialogMatrimonial.setText("Non disponible");
            if (tvPhone != null) tvPhone.setText("Non disponible");
            if (tvDialogNeighborhood != null) tvDialogNeighborhood.setText("Non disponible");
            if (tvDialogCity != null) tvDialogCity.setText("Non disponible");
            if (tvDialogCountry != null) tvDialogCountry.setText("Non disponible");
        } else {
            if (tvDialogStatus != null) tvDialogStatus.setText("Membre BaobabTD");

            // DOB
            if (tvDialogDob != null) {
                if ((user.isHideDob() || user.isPrivateProfile()) && !isSelf) {
                    tvDialogDob.setText("********");
                } else {
                    tvDialogDob.setText(user.getDob() != null && !user.getDob().isEmpty() ? user.getDob() : "Non renseignée");
                }
            }

            // Gender / Sexe
            if (tvDialogGender != null) {
                tvDialogGender.setText(user.getGender() != null && !user.getGender().isEmpty() ? user.getGender() : "Non renseigné");
            }

            // Profession
            if (tvDialogProfession != null) {
                tvDialogProfession.setText(user.getProfession() != null && !user.getProfession().isEmpty() ? user.getProfession() : "Non renseignée");
            }

            // Relationship Status / Situation amoureuse
            if (tvDialogMatrimonial != null) {
                tvDialogMatrimonial.setText(user.getRelationshipStatus() != null && !user.getRelationshipStatus().isEmpty() ? user.getRelationshipStatus() : "Non renseignée");
            }

            // Email
            if (tvDialogEmail != null) {
                if ((user.isHideEmail() || user.isPrivateProfile()) && !isSelf) {
                    tvDialogEmail.setText("********");
                } else {
                    tvDialogEmail.setText(user.getEmail() != null ? user.getEmail() : "Non renseigné");
                }
            }

            // Phone
            if (tvPhone != null) {
                if (user.isPrivateProfile() && !isSelf) {
                    tvPhone.setText("********");
                } else {
                    tvPhone.setText(user.getPhone() != null && !user.getPhone().isEmpty() ? user.getPhone() : "Non renseigné");
                }
            }

            // Location
            if ((user.isHideLocation() || user.isPrivateProfile()) && !isSelf) {
                if (tvDialogNeighborhood != null) tvDialogNeighborhood.setText("********");
                if (tvDialogCity != null) tvDialogCity.setText("********");
                if (tvDialogCountry != null) tvDialogCountry.setText("********");
            } else {
                if (tvDialogNeighborhood != null)
                    tvDialogNeighborhood.setText(user.getNeighborhood() != null && !user.getNeighborhood().isEmpty() ? user.getNeighborhood() : "Non renseigné");
                if (tvDialogCity != null)
                    tvDialogCity.setText(user.getCity() != null && !user.getCity().isEmpty() ? user.getCity() : "Non renseignée");
                if (tvDialogCountry != null)
                    tvDialogCountry.setText(user.getCountry() != null && !user.getCountry().isEmpty() ? user.getCountry() : "Non renseigné");
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (toolbar != null) {
            toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
            toolbar.setNavigationOnClickListener(v -> dialog.dismiss());
        }

        // Block button state inside dialog
        if (btnDialogBlockUser != null) {
            if (targetUserId == currentUserId || targetUserId == -1) {
                btnDialogBlockUser.setVisibility(View.GONE);
            } else {
                btnDialogBlockUser.setVisibility(View.VISIBLE);
                boolean isBlocked = userRepository.isUserBlocked(currentUserId, targetUserId);
                if (isBlocked) {
                    btnDialogBlockUser.setText("Débloquer cet utilisateur 🟢");
                    btnDialogBlockUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#388E3C")));
                } else {
                    btnDialogBlockUser.setText("Bloquer cet utilisateur 🚫");
                    btnDialogBlockUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#B71C1C")));
                }

                btnDialogBlockUser.setOnClickListener(v -> {
                    boolean blocked = userRepository.isUserBlocked(currentUserId, targetUserId);
                    if (blocked) {
                        userRepository.unblockUser(currentUserId, targetUserId);
                        Toasty.success(this, "Utilisateur débloqué", Toasty.LENGTH_SHORT).show();
                    } else {
                        userRepository.blockUser(currentUserId, targetUserId);
                        Toasty.warning(this, "Utilisateur bloqué", Toasty.LENGTH_SHORT).show();
                    }
                    boolean nowBlocked = userRepository.isUserBlocked(currentUserId, targetUserId);
                    if (nowBlocked) {
                        btnDialogBlockUser.setText("Débloquer cet utilisateur 🟢");
                        btnDialogBlockUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#388E3C")));
                    } else {
                        btnDialogBlockUser.setText("Bloquer cet utilisateur 🚫");
                        btnDialogBlockUser.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#B71C1C")));
                    }
                });
            }
        }

        Button btnDialogChat = dialogView.findViewById(R.id.btnDialogChat);
        if (btnDialogChat != null) {
            if (targetUserId == currentUserId || targetUserId == -1) {
                btnDialogChat.setVisibility(View.GONE);
            } else {
                btnDialogChat.setVisibility(View.VISIBLE);
                btnDialogChat.setOnClickListener(v -> {
                    dialog.dismiss();
                    ConversationRepository conversationRepository = new ConversationRepository(this);
                    int conversationId = conversationRepository.createConversationIfNotExists(currentUserId, targetUserId);
                    Intent intent = new Intent(PageUtilisateurActivity.this, ChatActivity.class);
                    intent.putExtra("conversation_id", conversationId);
                    intent.putExtra("receiver_id", targetUserId);
                    intent.putExtra("receiver_name", name);
                    intent.putExtra("receiver_profile_image", img);
                    startActivity(intent);
                });
            }
        }

        dialog.show();
    }

    private void loadUserPosts() {
        if (targetUserId != -1) {
            List<Post> posts = postRepository.getPostsByUserId(targetUserId, currentUserId);
            Collections.sort(posts, (p1, p2) -> Boolean.compare(p2.isPinned(), p1.isPinned()));
            userPostsList.clear();
            userPostsList.addAll(posts);
            postAdapter.notifyDataSetChanged();
        }
    }

    private void launchImageCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.activityTitle = "Recadrer l'image";
        imagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    private void publishPostOnProfile() {
        String content = etPostContent.getText().toString().trim();
        if (content.isEmpty() && savedPostImagePath == null) {
            Toasty.warning(this, "Écrivez un texte ou ajoutez une photo", Toasty.LENGTH_SHORT).show();
            return;
        }

        boolean success = postRepository.createPost(targetUserId != -1 ? targetUserId : currentUserId, content, savedPostImagePath);
        if (success) {
            etPostContent.setText("");
            savedPostImagePath = null;
            layoutSelectedImage.setVisibility(View.GONE);
            Toasty.success(this, "Publication réussie !", Toasty.LENGTH_SHORT).show();
            loadUserPosts();
        } else {
            Toasty.error(this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
        }
    }

    private void showImagePreviewDialog(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);
        View layoutPreviewBottom = dialogView.findViewById(R.id.layoutPreviewBottom);
        if (layoutPreviewBottom != null) {
            layoutPreviewBottom.setVisibility(View.GONE);
        }

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);
        if (imgEnlarged != null) {
            ImageUtils.enablePinchToZoom(imgEnlarged);
        }

        activeImageDialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> {
                if (activeImageDialog != null && activeImageDialog.isShowing()) {
                    activeImageDialog.dismiss();
                }
            });
        }

        activeImageDialog.setOnDismissListener(d -> activeImageDialog = null);
        activeImageDialog.show();
    }

    private void showImagePreviewDialog(Post post) {
        if (post == null || post.getImagePath() == null || post.getImagePath().trim().isEmpty())
            return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);
        View layoutPreviewBottom = dialogView.findViewById(R.id.layoutPreviewBottom);
        TextView tvPreviewAuthorName = dialogView.findViewById(R.id.tvPreviewAuthorName);
        TextView tvPreviewStats = dialogView.findViewById(R.id.tvPreviewStats);
        TextView tvPreviewReaction = dialogView.findViewById(R.id.tvPreviewReaction);

        ImageUtils.loadFullImage(this, post.getImagePath(), imgEnlarged);
        if (imgEnlarged != null) {
            ImageUtils.enablePinchToZoom(imgEnlarged);
        }

        if (layoutPreviewBottom != null) {
            layoutPreviewBottom.setVisibility(View.VISIBLE);

            if (tvPreviewAuthorName != null) {
                if (post.getAuthorName() != null && !post.getAuthorName().isEmpty()) {
                    tvPreviewAuthorName.setVisibility(View.VISIBLE);
                    tvPreviewAuthorName.setText(post.getAuthorName());
                } else {
                    tvPreviewAuthorName.setVisibility(View.GONE);
                }
            }

            if (tvPreviewStats != null) {
                String stats = "👍 " + post.getLikesCount() + "    💬 " + post.getCommentsCount() + "    ↗ " + post.getSharesCount();
                tvPreviewStats.setText(stats);
            }

            if (tvPreviewReaction != null) {
                String reaction = post.getUserReactionType();
                String emoji = null;
                int tintColor = android.graphics.Color.parseColor("#2196F3");

                if ("LOVE".equalsIgnoreCase(reaction)) {
                    emoji = "❤️";
                    tintColor = android.graphics.Color.RED;
                } else if ("HAHA".equalsIgnoreCase(reaction)) {
                    emoji = "😆";
                    tintColor = android.graphics.Color.parseColor("#FF9800");
                } else if ("WOW".equalsIgnoreCase(reaction)) {
                    emoji = "😮";
                    tintColor = android.graphics.Color.parseColor("#2196F3");
                } else if ("SAD".equalsIgnoreCase(reaction)) {
                    emoji = "😢";
                    tintColor = android.graphics.Color.parseColor("#9C27B0");
                } else if ("ANGRY".equalsIgnoreCase(reaction)) {
                    emoji = "😡";
                    tintColor = android.graphics.Color.RED;
                } else if ("LIKE".equalsIgnoreCase(reaction) || post.isLikedByCurrentUser()) {
                    emoji = "👍";
                    tintColor = android.graphics.Color.parseColor("#2196F3");
                }

                if (emoji != null) {
                    tvPreviewReaction.setVisibility(View.VISIBLE);
                    tvPreviewReaction.setText(emoji);
                    tvPreviewReaction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(tintColor));
                } else {
                    tvPreviewReaction.setVisibility(View.GONE);
                }
            }
        }

        activeImageDialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> {
                if (activeImageDialog != null && activeImageDialog.isShowing())
                    activeImageDialog.dismiss();
            });
        }

        activeImageDialog.setOnDismissListener(d -> activeImageDialog = null);
        activeImageDialog.show();
    }

    private void showVideoPlayerDialog(String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video_player, null);
        android.widget.VideoView videoViewPlayer = dialogView.findViewById(R.id.videoViewPlayer);
        ImageButton ibClose = dialogView.findViewById(R.id.ibCloseVideoPlayer);

        android.widget.MediaController mediaController = new android.widget.MediaController(this);
        mediaController.setAnchorView(videoViewPlayer);
        videoViewPlayer.setMediaController(mediaController);
        videoViewPlayer.setVideoPath(videoPath);

        activeVideoDialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> {
                videoViewPlayer.stopPlayback();
                if (activeVideoDialog != null && activeVideoDialog.isShowing())
                    activeVideoDialog.dismiss();
            });
        }

        activeVideoDialog.setOnDismissListener(d -> {
            videoViewPlayer.stopPlayback();
            activeVideoDialog = null;
        });

        activeVideoDialog.show();
        videoViewPlayer.start();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

