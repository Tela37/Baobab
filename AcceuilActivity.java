package td.teladoumbaobabtd;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import td.teladoumbaobabtd.repository.NotificationRepository;
import td.teladoumbaobabtd.repository.PostRepository;
import td.teladoumbaobabtd.repository.StoryRepository;
import td.teladoumbaobabtd.repository.UserRepository;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

public class AcceuilActivity extends AppCompatActivity {

    private ImageView imgUserAvatar, imgPostPreview, imgVideoPreviewThumbnail;
    private EditText etPostContent;
    private ImageButton ibAttachPostImage, ibAttachPostVideo, ibRemoveVideoPreview;
    private View layoutVideoPreview;
    private Button btnPublishPost;
    private RecyclerView rvPosts, rvStories;
    private SwipeRefreshLayout swipeRefreshLayout;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    private PostRepository postRepository;
    private UserRepository userRepository;
    private StoryRepository storyRepository;
    private NotificationRepository notificationRepository;
    private SessionManager sessionManager;
    private TextView tvNotificationBadge;

    private PostAdapter postAdapter;
    private StoryAdapter storyAdapter;
    private final List<Post> postList = new ArrayList<>();
    private final List<Post> fullPostList = new ArrayList<>();
    private final List<UserStoryGroup> friendStoryGroupList = new ArrayList<>();
    private UserStoryGroup currentUserStoryGroup = null;

    private Uri selectedPostImageUri, selectedPostVideoUri;
    private String savedPostImagePath, savedPostVideoPath;

    private final ActivityResultLauncher<String> postVideoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPostVideoUri = uri;
                    if (layoutVideoPreview != null) {
                        layoutVideoPreview.setVisibility(View.VISIBLE);
                        if (imgVideoPreviewThumbnail != null) {
                            ImageUtils.loadFullImage(this, uri.toString(), imgVideoPreviewThumbnail);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<CropImageContractOptions> postImagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        selectedPostImageUri = uri;
                        imgPostPreview.setImageURI(uri);
                        imgPostPreview.setVisibility(View.VISIBLE);
                    }
                } else if (result.getError() != null) {
                    Toasty.error(this, "Erreur lors du recadrage", Toasty.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<CropImageContractOptions> storyImagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        showPublishStoryDialog(uri);
                    }
                }
            });

    private void launchPostImageCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.activityTitle = "Recadrer l'image";
        options.cropMenuCropButtonTitle = "Valider";
        options.activityMenuIconColor = android.graphics.Color.WHITE;
        options.toolbarColor = android.graphics.Color.parseColor("#2196F3");
        options.toolbarTitleColor = android.graphics.Color.WHITE;
        options.toolbarBackButtonColor = android.graphics.Color.WHITE;
        options.toolbarTintColor = android.graphics.Color.WHITE;
        postImagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    private void launchStoryCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.activityTitle = "Recadrer la story (24h)";
        options.cropMenuCropButtonTitle = "Valider";
        options.activityMenuIconColor = android.graphics.Color.WHITE;
        options.toolbarColor = android.graphics.Color.parseColor("#2196F3");
        options.toolbarTitleColor = android.graphics.Color.WHITE;
        options.toolbarBackButtonColor = android.graphics.Color.WHITE;
        options.toolbarTintColor = android.graphics.Color.WHITE;
        storyImagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acceuil);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(AcceuilActivity.this, LoginActivity.class));
            finish();
            return;
        }

        postRepository = new PostRepository(this);
        userRepository = new UserRepository(this);
        storyRepository = new StoryRepository(this);
        notificationRepository = new NotificationRepository(this);

        initViews();
        setupNavigationDrawer(toolbar);
        checkPermissions();
        setupRecyclerView();
        setupStoriesRecyclerView();

        loadCurrentUserAvatar();

        imgUserAvatar.setOnClickListener(v -> {
            User currentUser = userRepository.getUserById(sessionManager.getUserId());
            if (currentUser != null && currentUser.getProfileImage() != null && !currentUser.getProfileImage().isEmpty()) {
                showImagePreviewDialog(currentUser.getProfileImage());
            }
        });

        ibAttachPostImage.setOnClickListener(v -> launchPostImageCropper());
        btnPublishPost.setOnClickListener(v -> publishPost());

        loadPosts();
        loadStories();

        // Chargement de la bannière et préchargement de l'interstitiel AdMob
        android.widget.FrameLayout adBannerContainer = findViewById(R.id.adBannerContainer);
        AdMobManager.loadBannerAd(this, adBannerContainer);
        AdMobManager.preloadInterstitialAd(this);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    finish();
                }
            }
        });
    }

    private void initViews() {
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        imgPostPreview = findViewById(R.id.imgPostPreview);
        etPostContent = findViewById(R.id.etPostContent);
        ibAttachPostImage = findViewById(R.id.ibAttachPostImage);
        ibAttachPostVideo = findViewById(R.id.ibAttachPostVideo);
        layoutVideoPreview = findViewById(R.id.layoutVideoPreview);
        imgVideoPreviewThumbnail = findViewById(R.id.imgVideoPreviewThumbnail);
        ibRemoveVideoPreview = findViewById(R.id.ibRemoveVideoPreview);
        btnPublishPost = findViewById(R.id.btnPublishPost);
        rvPosts = findViewById(R.id.rvPosts);
        rvStories = findViewById(R.id.rvStories);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        if (ibAttachPostVideo != null) {
            ibAttachPostVideo.setOnClickListener(v -> postVideoPickerLauncher.launch("video/*"));
        }
        if (ibRemoveVideoPreview != null) {
            ibRemoveVideoPreview.setOnClickListener(v -> {
                selectedPostVideoUri = null;
                savedPostVideoPath = null;
                if (layoutVideoPreview != null) layoutVideoPreview.setVisibility(View.GONE);
            });
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadPosts();
                loadStories();
                loadCurrentUserAvatar();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
    }

    private void setupStoriesRecyclerView() {
        if (rvStories == null) return;

        User currentUser = userRepository.getUserById(sessionManager.getUserId());
        String currentAvatar = currentUser != null ? currentUser.getProfileImage() : null;

        storyAdapter = new StoryAdapter(
                friendStoryGroupList,
                currentUserStoryGroup,
                currentAvatar,
                () -> launchStoryCropper(),
                this::showStoryGroupViewerDialog
        );

        rvStories.setAdapter(storyAdapter);
    }

    private void loadStories() {
        if (storyRepository == null) return;

        List<UserStoryGroup> allGroups = storyRepository.getGroupedRecentStories();
        friendStoryGroupList.clear();
        currentUserStoryGroup = null;

        int currentUserId = sessionManager.getUserId();
        for (UserStoryGroup group : allGroups) {
            if (group.getUserId() == currentUserId) {
                currentUserStoryGroup = group;
            } else {
                friendStoryGroupList.add(group);
            }
        }

        if (storyAdapter == null) {
            setupStoriesRecyclerView();
        } else {
            storyAdapter.updateData(friendStoryGroupList, currentUserStoryGroup);
        }
    }

    private void showPublishStoryDialog(Uri imageUri) {
        String savedPath = ImageUtils.saveImageToInternalStorage(this, imageUri);
        if (savedPath == null) {
            Toasty.warning(this, "Erreur lors de la préparation de la story", Toasty.LENGTH_SHORT).show();
            return;
        }

        EditText etCaption = new EditText(this);
        etCaption.setHint("Ajouter une légende (optionnel)...");

        new AlertDialog.Builder(this)
                .setTitle("Publier une story (24h) 📸")
                .setView(etCaption)
                .setPositiveButton("Partager", (dialog, which) -> {
                    String caption = etCaption.getText().toString().trim();
                    boolean created = storyRepository.createStory(sessionManager.getUserId(), savedPath, caption);
                    if (created) {
                        Toasty.success(this, "Story publiée !", Toasty.LENGTH_SHORT).show();
                        loadStories();
                    } else {
                        Toasty.error(this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    /**
     * Visualiseur de stories regroupées par utilisateur avec défilement automatique,
     * minuteur visuel, navigation tactile (clic) et réactions émojis.
     */
    private void showStoryGroupViewerDialog(UserStoryGroup group) {
        if (group == null || group.getStories().isEmpty()) return;

        final List<Story> stories = group.getStories();
        final int[] currentStoryIndex = {0};
        final int[] storyProgress = {0};

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_story_viewer, null);
        ImageView imgStoryViewerPhoto = dialogView.findViewById(R.id.imgStoryViewerPhoto);
        ImageView imgStoryViewerAvatar = dialogView.findViewById(R.id.imgStoryViewerAvatar);
        TextView tvAuthorName = dialogView.findViewById(R.id.tvStoryViewerAuthorName);
        TextView tvStoryTime = dialogView.findViewById(R.id.tvStoryViewerTime);
        TextView tvCaption = dialogView.findViewById(R.id.tvStoryViewerCaption);
        TextView tvCurrentReaction = dialogView.findViewById(R.id.tvCurrentReaction);
        ProgressBar progressStoryTimer = dialogView.findViewById(R.id.progressStoryTimer);
        ImageButton ibClose = dialogView.findViewById(R.id.ibCloseStoryViewer);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        Handler storyTimerHandler = new Handler();
        Runnable storyTimerRunnable = new Runnable() {
            @Override
            public void run() {
                storyProgress[0] += 2;
                if (progressStoryTimer != null) {
                    progressStoryTimer.setProgress(storyProgress[0]);
                }

                if (storyProgress[0] >= 100) {
                    // Story terminée -> passer à la suivante
                    if (currentStoryIndex[0] + 1 < stories.size()) {
                        currentStoryIndex[0]++;
                        storyProgress[0] = 0;
                        renderCurrentStory(stories.get(currentStoryIndex[0]), tvAuthorName, tvStoryTime, imgStoryViewerAvatar, imgStoryViewerPhoto, tvCaption, tvCurrentReaction);
                        storyTimerHandler.postDelayed(this, 100);
                    } else {
                        // Toutes les stories de cet utilisateur sont finies -> fermer le visualiseur
                        dialog.dismiss();
                    }
                } else {
                    storyTimerHandler.postDelayed(this, 100);
                }
            }
        };

        // Afficher la première story
        renderCurrentStory(stories.get(0), tvAuthorName, tvStoryTime, imgStoryViewerAvatar, imgStoryViewerPhoto, tvCaption, tvCurrentReaction);
        storyTimerHandler.postDelayed(storyTimerRunnable, 100);

        dialog.setOnDismissListener(d -> storyTimerHandler.removeCallbacks(storyTimerRunnable));

        if (ibClose != null) {
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }

        // Navigation tactile : Avancer à la story suivante lors d'un clic sur la photo
        if (imgStoryViewerPhoto != null) {
            imgStoryViewerPhoto.setOnClickListener(v -> {
                if (currentStoryIndex[0] + 1 < stories.size()) {
                    currentStoryIndex[0]++;
                    storyProgress[0] = 0;
                    if (progressStoryTimer != null) progressStoryTimer.setProgress(0);
                    renderCurrentStory(stories.get(currentStoryIndex[0]), tvAuthorName, tvStoryTime, imgStoryViewerAvatar, imgStoryViewerPhoto, tvCaption, tvCurrentReaction);
                } else {
                    dialog.dismiss();
                }
            });
        }

        // Configuration des boutons de réaction émojis
        View.OnClickListener reactionListener = v -> {
            TextView btnEmoji = (TextView) v;
            String emoji = btnEmoji.getText().toString();
            Story currentStory = stories.get(currentStoryIndex[0]);
            boolean success = storyRepository.setStoryReaction(currentStory.getId(), sessionManager.getUserId(), emoji);
            if (success) {
                if (tvCurrentReaction != null) {
                    tvCurrentReaction.setVisibility(View.VISIBLE);
                    tvCurrentReaction.setText("Vous avez réagi : " + emoji);
                }
                Toasty.success(this, "Réaction " + emoji + " envoyée !", Toasty.LENGTH_SHORT).show();
            }
        };

        TextView btnHeart = dialogView.findViewById(R.id.btnEmojiHeart);
        TextView btnLaugh = dialogView.findViewById(R.id.btnEmojiLaugh);
        TextView btnLike = dialogView.findViewById(R.id.btnEmojiLike);
        TextView btnWow = dialogView.findViewById(R.id.btnEmojiWow);
        TextView btnSad = dialogView.findViewById(R.id.btnEmojiSad);
        TextView btnAngry = dialogView.findViewById(R.id.btnEmojiAngry);

        if (btnHeart != null) btnHeart.setOnClickListener(reactionListener);
        if (btnLaugh != null) btnLaugh.setOnClickListener(reactionListener);
        if (btnLike != null) btnLike.setOnClickListener(reactionListener);
        if (btnWow != null) btnWow.setOnClickListener(reactionListener);
        if (btnSad != null) btnSad.setOnClickListener(reactionListener);
        if (btnAngry != null) btnAngry.setOnClickListener(reactionListener);

        dialog.show();
    }

    private void renderCurrentStory(
            Story story,
            TextView tvAuthorName,
            TextView tvStoryTime,
            ImageView imgStoryViewerAvatar,
            ImageView imgStoryViewerPhoto,
            TextView tvCaption,
            TextView tvCurrentReaction) {

        if (story == null) return;

        if (tvAuthorName != null) tvAuthorName.setText(story.getUserName());
        if (tvStoryTime != null) tvStoryTime.setText(story.getCreatedAt());
        ImageUtils.loadProfileImage(this, story.getUserAvatar(), imgStoryViewerAvatar);
        ImageUtils.loadFullImage(this, story.getImagePath(), imgStoryViewerPhoto);

        if (story.getCaption() != null && !story.getCaption().trim().isEmpty() && tvCaption != null) {
            tvCaption.setVisibility(View.VISIBLE);
            tvCaption.setText(story.getCaption());
        } else if (tvCaption != null) {
            tvCaption.setVisibility(View.GONE);
        }

        String currentReaction = storyRepository.getStoryReaction(story.getId(), sessionManager.getUserId());
        if (currentReaction != null && tvCurrentReaction != null) {
            tvCurrentReaction.setVisibility(View.VISIBLE);
            tvCurrentReaction.setText("Vous avez réagi : " + currentReaction);
        } else if (tvCurrentReaction != null) {
            tvCurrentReaction.setVisibility(View.GONE);
        }
    }

    private void setupNavigationDrawer(Toolbar toolbar) {
        if (drawerLayout == null || navigationView == null) return;

        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        updateNavHeaderData();

        navigationView.setCheckedItem(R.id.nav_home);

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_messages) {
                startActivity(new Intent(AcceuilActivity.this, MainActivity.class));
                return true;
            } else if (id == R.id.nav_friends) {
                startActivity(new Intent(AcceuilActivity.this, AmiActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(AcceuilActivity.this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(AcceuilActivity.this, ParametreActivity.class));
                return true;
            } else if (id == R.id.nav_logout) {
                performLogout();
                return true;
            }
            return false;
        });
    }

    private void updateNavHeaderData() {
        if (navigationView == null) return;

        View headerView = navigationView.getHeaderView(0);
        if (headerView != null) {
            ImageView navHeaderAvatar = headerView.findViewById(R.id.navHeaderAvatar);
            TextView navHeaderName = headerView.findViewById(R.id.navHeaderName);
            TextView navHeaderEmail = headerView.findViewById(R.id.navHeaderEmail);

            User currentUser = userRepository.getUserById(sessionManager.getUserId());
            if (currentUser != null) {
                if (navHeaderName != null) {
                    navHeaderName.setText(currentUser.getName() != null ? currentUser.getName() : sessionManager.getName());
                }
                if (navHeaderEmail != null) {
                    navHeaderEmail.setText(currentUser.getEmail() != null ? currentUser.getEmail() : sessionManager.getEmail());
                }
                if (navHeaderAvatar != null) {
                    ImageUtils.loadProfileImage(this, currentUser.getProfileImage(), navHeaderAvatar);
                }
            } else {
                if (navHeaderName != null) navHeaderName.setText(sessionManager.getName());
                if (navHeaderEmail != null) navHeaderEmail.setText(sessionManager.getEmail());
            }
        }
    }

    private void performLogout() {
        sessionManager.logout();
        try {
            com.facebook.login.LoginManager.getInstance().logOut();
        } catch (Exception ignored) {}
        try {
            com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                    new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build();
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso).signOut();
        } catch (Exception ignored) {}

        Toasty.info(this, "Déconnexion réussie", Toasty.LENGTH_SHORT).show();
        Intent intent = new Intent(AcceuilActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void setupRecyclerView() {
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        rvPosts.setNestedScrollingEnabled(false);

        postAdapter = new PostAdapter(postList, sessionManager.getUserId(), new PostAdapter.OnPostClickListener() {
            @Override
            public void onLikeClick(Post post) {
                postRepository.toggleLikePost(post.getId(), sessionManager.getUserId());
                loadPosts();
            }

            @Override
            public void onReactionClick(Post post, String reactionType) {
                postRepository.setPostReaction(post.getId(), sessionManager.getUserId(), reactionType);
                loadPosts();
            }

            @Override
            public void onCommentClick(Post post) {
                Intent intent = new Intent(AcceuilActivity.this, CommenterActivity.class);
                intent.putExtra("post_id", post.getId());
                startActivity(intent);
            }

            @Override
            public void onShareClick(Post post) {
                postRepository.recordPostShare(post.getId(), sessionManager.getUserId());
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Publication de " + post.getAuthorName() + " sur BaobabTD :\n" + (post.getContent() != null ? post.getContent() : ""));
                startActivity(Intent.createChooser(shareIntent, "Partager via"));
                loadPosts();
            }

            @Override
            public void onReportClick(Post post) {
                Intent intent = new Intent(AcceuilActivity.this, CommenterActivity.class);
                intent.putExtra("post_id", post.getId());
                startActivity(intent);
            }

            @Override
            public void onEditClick(Post post) {
                showEditPostDialog(post);
            }

            @Override
            public void onDeleteClick(Post post) {
                showDeletePostDialog(post);
            }

            @Override
            public void onImageClick(String imagePath) {
                showImagePreviewDialog(imagePath);
            }

            @Override
            public void onVideoClick(String videoPath) {
                showVideoPlayerDialog(videoPath);
            }
        });

        rvPosts.setAdapter(postAdapter);
    }

    private void loadCurrentUserAvatar() {
        User user = userRepository.getUserById(sessionManager.getUserId());
        if (user != null) {
            ImageUtils.loadProfileImage(this, user.getProfileImage(), imgUserAvatar);
        }
    }

    private void publishPost() {
        String content = etPostContent.getText().toString().trim();

        if (content.isEmpty() && selectedPostImageUri == null && selectedPostVideoUri == null) {
            Toast.makeText(this, "Écrivez un texte ou sélectionnez une photo/vidéo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPostImageUri != null) {
            savedPostImagePath = ImageUtils.saveImageToInternalStorage(this, selectedPostImageUri);
        }

        if (selectedPostVideoUri != null) {
            savedPostVideoPath = ImageUtils.saveVideoToInternalStorage(this, selectedPostVideoUri);
        }

        boolean success = postRepository.createPost(sessionManager.getUserId(), content, savedPostImagePath, savedPostVideoPath);

        if (success) {
            Toasty.success(this, "Publication partagée !", Toasty.LENGTH_SHORT).show();
            etPostContent.setText("");
            selectedPostImageUri = null;
            selectedPostVideoUri = null;
            savedPostImagePath = null;
            savedPostVideoPath = null;
            imgPostPreview.setVisibility(View.GONE);
            if (layoutVideoPreview != null) layoutVideoPreview.setVisibility(View.GONE);
            loadPosts();
        } else {
            Toasty.error(this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
        }
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

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> {
                videoViewPlayer.stopPlayback();
                dialog.dismiss();
            });
        }

        dialog.setOnDismissListener(d -> videoViewPlayer.stopPlayback());
        dialog.show();
        videoViewPlayer.start();
    }

    private void loadPosts() {
        List<Post> newPosts = postRepository.getAllPosts(sessionManager.getUserId());
        fullPostList.clear();
        fullPostList.addAll(newPosts);

        postList.clear();
        postList.addAll(newPosts);
        postAdapter.notifyDataSetChanged();
    }

    private void filterPosts(String query) {
        if (query == null || query.trim().isEmpty()) {
            postList.clear();
            postList.addAll(fullPostList);
            postAdapter.notifyDataSetChanged();
            return;
        }

        String lower = query.toLowerCase().trim();
        List<Post> filtered = new ArrayList<>();
        for (Post p : fullPostList) {
            if ((p.getContent() != null && p.getContent().toLowerCase().contains(lower)) ||
                    (p.getAuthorName() != null && p.getAuthorName().toLowerCase().contains(lower))) {
                filtered.add(p);
            }
        }

        postList.clear();
        postList.addAll(filtered);
        postAdapter.notifyDataSetChanged();
    }

    private void showEditPostDialog(Post post) {
        if (!TimeUtils.isWithinHours(post.getCreatedAt(), 10)) {
            Toasty.warning(this, "Modification de la publication impossible après 10 heures", Toasty.LENGTH_SHORT).show();
            return;
        }

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(32, 16, 32, 16);

        EditText etEditContent = new EditText(this);
        etEditContent.setHint("Modifier le texte...");
        etEditContent.setText(post.getContent());
        container.addView(etEditContent);

        final String[] updatedImagePath = new String[]{post.getImagePath()};

        if (post.getImagePath() != null && !post.getImagePath().isEmpty()) {
            ImageView imgPreview = new ImageView(this);
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 300);
            lp.topMargin = 16;
            imgPreview.setLayoutParams(lp);
            ImageUtils.loadFullImage(this, post.getImagePath(), imgPreview);
            container.addView(imgPreview);

            Button btnRemoveImg = new Button(this);
            btnRemoveImg.setText("Supprimer la photo");
            btnRemoveImg.setOnClickListener(v -> {
                updatedImagePath[0] = null;
                imgPreview.setVisibility(View.GONE);
                btnRemoveImg.setVisibility(View.GONE);
            });
            container.addView(btnRemoveImg);
        }

        new AlertDialog.Builder(this)
                .setTitle("Modifier la publication")
                .setView(container)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String newText = etEditContent.getText().toString().trim();
                    boolean updated = postRepository.updatePost(post.getId(), sessionManager.getUserId(), newText, updatedImagePath[0]);
                    if (updated) {
                        Toasty.success(AcceuilActivity.this, "Publication modifiée !", Toasty.LENGTH_SHORT).show();
                        loadPosts();
                    } else {
                        Toasty.error(AcceuilActivity.this, "Erreur de modification", Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showDeletePostDialog(Post post) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la publication")
                .setMessage("Voulez-vous vraiment supprimer cette publication ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    boolean deleted = postRepository.deletePost(post.getId(), sessionManager.getUserId());
                    if (deleted) {
                        Toasty.success(AcceuilActivity.this, "Publication supprimée", Toasty.LENGTH_SHORT).show();
                        loadPosts();
                    } else {
                        Toasty.warning(AcceuilActivity.this, "Erreur de suppression", Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showImagePreviewDialog(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);
        if (imgEnlarged != null) {
            ImageUtils.enablePinchToZoom(imgEnlarged);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Rechercher un post...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        filterPosts(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        filterPosts(newText);
                        return true;
                    }
                });
            }
        }

        MenuItem notificationItem = menu.findItem(R.id.action_notifications);
        if (notificationItem != null) {
            View actionView = notificationItem.getActionView();
            if (actionView != null) {
                tvNotificationBadge = actionView.findViewById(R.id.tvNotificationBadge);
                actionView.setOnClickListener(v -> {
                    if (notificationRepository != null) {
                        notificationRepository.markAllAsRead(sessionManager.getUserId());
                    }
                    if (tvNotificationBadge != null) {
                        tvNotificationBadge.setVisibility(View.GONE);
                    }
                    startActivity(new Intent(AcceuilActivity.this, NotificationActivity.class));
                });
            }
        }
        updateNotificationBadge();

        return true;
    }

    private void updateNotificationBadge() {
        if (notificationRepository == null || sessionManager == null) return;
        int unreadCount = notificationRepository.getUnreadNotificationCount(sessionManager.getUserId());
        if (tvNotificationBadge != null) {
            if (unreadCount > 0) {
                tvNotificationBadge.setVisibility(View.VISIBLE);
                tvNotificationBadge.setText(unreadCount > 9 ? "9+" : String.valueOf(unreadCount));
            } else {
                tvNotificationBadge.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        int id = item.getItemId();
        if (id == R.id.action_notifications) {
            if (notificationRepository != null) {
                notificationRepository.markAllAsRead(sessionManager.getUserId());
            }
            if (tvNotificationBadge != null) {
                tvNotificationBadge.setVisibility(View.GONE);
            }
            startActivity(new Intent(AcceuilActivity.this, NotificationActivity.class));
            return true;
        } else if (id == R.id.action_messages) {
            startActivity(new Intent(AcceuilActivity.this, MainActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPosts();
        loadStories();
        updateNotificationBadge();
        loadCurrentUserAvatar();
        android.widget.FrameLayout adBannerContainer = findViewById(R.id.adBannerContainer);
        AdMobManager.loadBannerAd(this, adBannerContainer);
        updateNavHeaderData();
        if (navigationView != null) {
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }
}
