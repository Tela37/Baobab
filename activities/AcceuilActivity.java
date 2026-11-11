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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.ConversationRepository;
import td.teladoum.baobab.repositories.FriendRepository;
import td.teladoum.baobab.repositories.MessageRepository;
import td.teladoum.baobab.repositories.NotificationRepository;
import td.teladoum.baobab.repositories.PostRepository;
import td.teladoum.baobab.repositories.StoryRepository;
import td.teladoum.baobab.repositories.UserRepository;

public class AcceuilActivity extends AppCompatActivity {

    private ImageView imgUserAvatar, imgPostPreview, imgVideoPreviewThumbnail;
    private ProgressBar progressPost;
    private EditText etPostContent;
    private CheckBox cbIsSensitive;
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
    private ConversationRepository conversationRepository;
    private SessionManager sessionManager;
    private TextView tvNotificationBadge;
    private TextView tvMessageBadge;

    private AlertDialog activeStoryDialog;
    private AlertDialog activeVideoDialog;
    private AlertDialog activeImageDialog;
    private Handler storyTimerHandler;
    private Runnable storyTimerRunnable;

    private PostAdapter postAdapter;
    private StoryAdapter storyAdapter;
    private final List<Post> postList = new ArrayList<>();
    private final List<Post> fullPostList = new ArrayList<>();
    private final List<UserStoryGroup> friendStoryGroupList = new ArrayList<>();
    private UserStoryGroup currentUserStoryGroup = null;
    private final List<Uri> selectedPostImageUris = new ArrayList<>();

    private Uri selectedPostImageUri, selectedPostVideoUri;
    private String savedPostImagePath, savedPostVideoPath;

    private final ActivityResultLauncher<String> postVideoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPostVideoUri = uri;
                    if (layoutVideoPreview != null) {
                        layoutVideoPreview.setVisibility(View.VISIBLE);
                        if (imgVideoPreviewThumbnail != null) {
                            ImageUtils.loadVideoThumbnail(this, uri.toString(), imgVideoPreviewThumbnail);
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
                        if (cbIsSensitive != null) {
                            cbIsSensitive.setVisibility(View.VISIBLE);
                        }
                    }
                } else if (result.getError() != null) {
                    //Toasty.error(this, "Erreur lors du recadrage", Toasty.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> multiplePostImagePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    List<Uri> uris = new ArrayList<>();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            uris.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        uris.add(data.getData());
                    }

                    if (!uris.isEmpty()) {
                        selectedPostImageUris.clear();
                        selectedPostImageUris.addAll(uris);
                        selectedPostImageUri = uris.get(0);
                        imgPostPreview.setImageURI(selectedPostImageUri);
                        imgPostPreview.setVisibility(View.VISIBLE);
                        if (cbIsSensitive != null) {
                            cbIsSensitive.setVisibility(View.VISIBLE);
                        }
                        Toasty.info(this, uris.size() + " photo(s) sélectionnée(s)", Toasty.LENGTH_SHORT).show();
                    }
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
        androidx.activity.EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_acceuil);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            androidx.core.graphics.Insets statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, statusBarInsets.top, 0, 0);
            return insets;
        });

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(AcceuilActivity.this, LoginActivity.class));
            finish();
            return;
        }

        postRepository = new PostRepository(this);
        PostRepository.syncPendingPosts(this);
        MessageRepository.syncPendingMessages(this);
        PostRepository.loadAllPostsFromCloud(this, () -> {
            loadPosts();
        });
        userRepository = new UserRepository(this);
        storyRepository = new StoryRepository(this);
        notificationRepository = new NotificationRepository(this);
        conversationRepository = new ConversationRepository(this);

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

        ibAttachPostImage.setOnClickListener(v -> showPostMediaSelectionOptions());
        btnPublishPost.setOnClickListener(v -> publishPost());

        loadPosts();
        userRepository.syncAllUsersFromCloud(() -> {
            loadPosts();
            loadStories();
        });
        loadStories();
        checkAppAnnouncement();

        // Chargement de la bannière et préchargement de l'interstitiel AdMob
        android.widget.FrameLayout adBannerContainer = findViewById(R.id.adBannerContainer);
        AdMobManager.loadBannerAd(this, adBannerContainer);
        AdMobManager.preloadInterstitialAd(this);

        // Interception du bouton "Retour" (Back) pour appliquer le comportement "à la Facebook"
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 1. Si le menu latéral (Drawer) est ouvert, on le ferme d'abord
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                }
                // 2. Si une boîte de dialogue de story est affichée, on la ferme
                else if (activeStoryDialog != null && activeStoryDialog.isShowing()) {
                    activeStoryDialog.dismiss();
                }
                // 3. Si un lecteur vidéo est ouvert en plein écran, on le ferme
                else if (activeVideoDialog != null && activeVideoDialog.isShowing()) {
                    activeVideoDialog.dismiss();
                }
                // 4. Si un aperçu d'image est ouvert, on le ferme
                else if (activeImageDialog != null && activeImageDialog.isShowing()) {
                    activeImageDialog.dismiss();
                }
                // 5. Si aucun élément intermédiaire n'est ouvert :
                // On utilise moveTaskToBack(true) au lieu de finish().
                // Explication pour débutant :
                // - finish() détruit l'activité et ferme l'application.
                // - moveTaskToBack(true) masque simplement l'application et la place en arrière-plan
                //   (exactement comme si l'utilisateur avait appuyé sur le bouton "Accueil" / Home).
                //   Ainsi, l'application reste active en mémoire et réapparaît instantanément lors de la réouverture.
                else {
                    moveTaskToBack(true);
                }
            }
        });
    }

    private void initViews() {
        imgUserAvatar = findViewById(R.id.imgUserAvatar);
        imgPostPreview = findViewById(R.id.imgPostPreview);
        cbIsSensitive = findViewById(R.id.cbIsSensitive);
        etPostContent = findViewById(R.id.etPostContent);
        ibAttachPostImage = findViewById(R.id.ibAttachPostImage);
        ibAttachPostVideo = findViewById(R.id.ibAttachPostVideo);
        layoutVideoPreview = findViewById(R.id.layoutVideoPreview);
        imgVideoPreviewThumbnail = findViewById(R.id.imgVideoPreviewThumbnail);
        ibRemoveVideoPreview = findViewById(R.id.ibRemoveVideoPreview);
        btnPublishPost = findViewById(R.id.btnPublishPost);
        progressPost = findViewById(R.id.progressPost);
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
                userRepository.syncAllUsersFromCloud(() -> {
                    loadPosts();
                    loadStories();
                    loadCurrentUserAvatar();
                    swipeRefreshLayout.setRefreshing(false);
                });
            });
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
    }

    private void setupStoriesRecyclerView() {
        if (rvStories == null) return;

        User currentUser = userRepository.getUserById(sessionManager.getUserId());
        String currentAvatar = (currentUser != null && currentUser.getProfileImage() != null) ? currentUser.getProfileImage() : sessionManager.getProfileImage();

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

        User currentUser = userRepository.getUserById(currentUserId);
        String currentAvatar = (currentUser != null && currentUser.getProfileImage() != null) ? currentUser.getProfileImage() : sessionManager.getProfileImage();

        if (storyAdapter == null) {
            setupStoriesRecyclerView();
        } else {
            storyAdapter.updateData(friendStoryGroupList, currentUserStoryGroup, currentAvatar);
        }
    }

    private void showPublishStoryDialog(Uri imageUri) {
        String base64Image = ImageUtils.uriToBase64(this, imageUri);
        String savedPath = base64Image != null && !base64Image.trim().isEmpty() ? base64Image : ImageUtils.saveImageToInternalStorage(this, imageUri);
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

        dismissActiveDialogs();

        final List<Story> stories = group.getStories();
        final int[] currentStoryIndex = {0};
        final int[] storyProgress = {0};

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_story_viewer, null);
        ImageView imgStoryViewerPhoto = dialogView.findViewById(R.id.imgStoryViewerPhoto);
        ImageView imgStoryViewerAvatar = dialogView.findViewById(R.id.imgStoryViewerAvatar);
        TextView tvAuthorName = dialogView.findViewById(R.id.tvStoryViewerAuthorName);
        TextView tvStoryTime = dialogView.findViewById(R.id.tvStoryViewerTime);
        TextView tvStoryViewsCount = dialogView.findViewById(R.id.tvStoryViewsCount);
        TextView tvCaption = dialogView.findViewById(R.id.tvStoryViewerCaption);
        TextView tvCurrentReaction = dialogView.findViewById(R.id.tvCurrentReaction);
        EditText etStoryComment = dialogView.findViewById(R.id.etStoryComment);
        Button btnSendStoryComment = dialogView.findViewById(R.id.btnSendStoryComment);
        ProgressBar progressStoryTimer = dialogView.findViewById(R.id.progressStoryTimer);
        ImageButton ibClose = dialogView.findViewById(R.id.ibCloseStoryViewer);

        activeStoryDialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        final boolean[] isStoryPaused = {false};

        storyTimerHandler = new Handler(android.os.Looper.getMainLooper());
        storyTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (isStoryPaused[0]) {
                    if (storyTimerHandler != null) storyTimerHandler.postDelayed(this, 100);
                    return;
                }

                storyProgress[0] += 2;
                if (progressStoryTimer != null) {
                    progressStoryTimer.setProgress(storyProgress[0]);
                }

                if (storyProgress[0] >= 100) {
                    // Story terminée -> passer à la suivante
                    if (currentStoryIndex[0] + 1 < stories.size()) {
                        currentStoryIndex[0]++;
                        storyProgress[0] = 0;
                        renderCurrentStory(stories.get(currentStoryIndex[0]), tvAuthorName, tvStoryTime, tvStoryViewsCount, imgStoryViewerAvatar, imgStoryViewerPhoto, tvCaption, tvCurrentReaction);
                        if (storyTimerHandler != null) storyTimerHandler.postDelayed(this, 100);
                    } else {
                        // Toutes les stories de cet utilisateur sont finies -> fermer le visualiseur
                        if (activeStoryDialog != null && activeStoryDialog.isShowing())
                            activeStoryDialog.dismiss();
                    }
                } else {
                    if (storyTimerHandler != null) storyTimerHandler.postDelayed(this, 100);
                }
            }
        };

        // Afficher la première story
        renderCurrentStory(stories.get(0), tvAuthorName, tvStoryTime, tvStoryViewsCount, imgStoryViewerAvatar, imgStoryViewerPhoto, tvCaption, tvCurrentReaction);
        storyTimerHandler.postDelayed(storyTimerRunnable, 100);

        activeStoryDialog.setOnDismissListener(d -> {
            if (storyTimerHandler != null && storyTimerRunnable != null) {
                storyTimerHandler.removeCallbacks(storyTimerRunnable);
            }
            activeStoryDialog = null;
        });

        if (ibClose != null) {
            ibClose.setOnClickListener(v -> {
                if (activeStoryDialog != null && activeStoryDialog.isShowing())
                    activeStoryDialog.dismiss();
            });
        }

        // Navigation tactile & appui sans relâcher pour mettre la story en pause
        if (imgStoryViewerPhoto != null) {
            imgStoryViewerPhoto.setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    isStoryPaused[0] = true; // Pause sur appui maintenu
                    return true;
                } else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                    long duration = event.getEventTime() - event.getDownTime();
                    isStoryPaused[0] = false; // Reprise au relâchement

                    if (duration < 250) { // Si c'est un tap court, passer à la story suivante
                        if (currentStoryIndex[0] + 1 < stories.size()) {
                            currentStoryIndex[0]++;
                            storyProgress[0] = 0;
                            if (progressStoryTimer != null) progressStoryTimer.setProgress(0);
                            renderCurrentStory(stories.get(currentStoryIndex[0]), tvAuthorName, tvStoryTime, tvStoryViewsCount, imgStoryViewerAvatar, imgStoryViewerPhoto, tvCaption, tvCurrentReaction);
                        } else {
                            if (activeStoryDialog != null && activeStoryDialog.isShowing())
                                activeStoryDialog.dismiss();
                        }
                    }
                    return true;
                }
                return false;
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

        if (btnSendStoryComment != null && etStoryComment != null) {
            btnSendStoryComment.setOnClickListener(v -> {
                String comment = etStoryComment.getText().toString().trim();
                if (!comment.isEmpty()) {
                    Story currentStory = stories.get(currentStoryIndex[0]);
                    boolean sent = storyRepository.addStoryComment(currentStory.getId(), sessionManager.getUserId(), comment);
                    if (sent) {
                        etStoryComment.setText("");
                        Toasty.success(this, "Réponse envoyée à l'auteur 💬", Toasty.LENGTH_SHORT).show();
                    } else {
                        Toasty.error(this, "Erreur lors de l'envoi", Toasty.LENGTH_SHORT).show();
                    }
                }
            });
        }

        activeStoryDialog.show();
    }

    private void renderCurrentStory(
            Story story,
            TextView tvAuthorName,
            TextView tvStoryTime,
            TextView tvStoryViewsCount,
            ImageView imgStoryViewerAvatar,
            ImageView imgStoryViewerPhoto,
            TextView tvCaption,
            TextView tvCurrentReaction) {

        if (story == null) return;

        storyRepository.recordStoryView(story.getId(), sessionManager.getUserId());
        int viewsCount = storyRepository.getStoryViewsCount(story.getId());
        if (tvStoryViewsCount != null) {
            tvStoryViewsCount.setText("👁️ " + viewsCount + " vue" + (viewsCount > 1 ? "s" : ""));
        }

        if (tvAuthorName != null) tvAuthorName.setText(story.getUserName());
        if (tvStoryTime != null) tvStoryTime.setText(formatStoryExpiration(story.getCreatedAt()));
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

    private String formatStoryExpiration(String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) return "Expire bientôt ⏳";

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault());
            java.util.Date date = sdf.parse(createdAt);
            if (date == null) return "Expire bientôt ⏳";

            long createdMillis = date.getTime();
            long now = System.currentTimeMillis();
            long elapsedMillis = now - createdMillis;
            long totalMillis24h = 24 * 60 * 60 * 1000L;
            long remainingMillis = totalMillis24h - elapsedMillis;

            if (remainingMillis <= 0) {
                return "Expirée ⏳";
            }

            long remainingHours = remainingMillis / (60 * 60 * 1000L);
            long remainingMinutes = (remainingMillis % (60 * 60 * 1000L)) / (60 * 1000L);

            if (remainingHours > 0) {
                return "Expire dans " + remainingHours + "h " + remainingMinutes + "m ⏳";
            } else {
                return "Expire dans " + remainingMinutes + " min ⏳";
            }
        } catch (Exception e) {
            return "Expire bientôt ⏳";
        }
    }

    private void setupNavigationDrawer(Toolbar toolbar) {
        if (drawerLayout == null || navigationView == null) return;

        drawerToggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        updateNavHeaderData();
        updateDrawerBadges();

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
            } else if (id == R.id.nav_notifications) {
                startActivity(new Intent(AcceuilActivity.this, NotificationActivity.class));
                return true;
            } else if (id == R.id.nav_groups) {
                startActivity(new Intent(AcceuilActivity.this, GroupListActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(AcceuilActivity.this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_my_posts) {
                startActivity(new Intent(AcceuilActivity.this, MesPublicationActivity.class));
                return true;
            } else if (id == R.id.nav_videos) {
                startActivity(new Intent(AcceuilActivity.this, VideoActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(AcceuilActivity.this, ParametreActivity.class));
                return true;
            } else if (id == R.id.nav_ads) {
                startActivity(new Intent(AcceuilActivity.this, PubliciteActivity.class));
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

            User currentUser = userRepository.getUserById(sessionManager.getUserId());
            String name = currentUser != null && currentUser.getName() != null ? currentUser.getName() : sessionManager.getName();
            String profileImage = currentUser != null ? currentUser.getProfileImage() : sessionManager.getProfileImage();

            if (navHeaderName != null) {
                navHeaderName.setText(name);
            }
            if (navHeaderAvatar != null) {
                ImageUtils.loadProfileImage(this, profileImage, navHeaderAvatar);
            }
        }
    }

    private void updateDrawerBadges() {
        if (navigationView == null) return;
        Menu menu = navigationView.getMenu();

        int totalUnreadMessages = 0;
        int unreadNotifications = notificationRepository != null ? notificationRepository.getUnreadNotificationCount(sessionManager.getUserId()) : 0;
        int pendingRequests = 0;

        try {
            ConversationRepository convRepo = new ConversationRepository(this);
            List<ConversationItem> convs = convRepo.getConversations(sessionManager.getUserId());
            for (ConversationItem item : convs) {
                totalUnreadMessages += item.getUnreadCount();
            }
        } catch (Exception ignored) {
        }

        try {
            FriendRepository friendRepo = new FriendRepository(this);
            pendingRequests = friendRepo.getPendingRequests(sessionManager.getUserId()).size();
        } catch (Exception ignored) {
        }

        MenuItem itemMessages = menu.findItem(R.id.nav_messages);
        if (itemMessages != null) {
            String title = "Messages 💬";
            if (totalUnreadMessages > 0) {
                title += " (" + totalUnreadMessages + ")";
            }
            itemMessages.setTitle(title);
        }

        MenuItem itemFriends = menu.findItem(R.id.nav_friends);
        if (itemFriends != null) {
            String title = "Mes Amis 👥";
            if (pendingRequests > 0) {
                title += " (" + pendingRequests + ")";
            }
            itemFriends.setTitle(title);
        }

        MenuItem itemNotifications = menu.findItem(R.id.nav_notifications);
        if (itemNotifications != null) {
            String title = "Notifications 🔔";
            if (unreadNotifications > 0) {
                title += " (" + unreadNotifications + ")";
            }
            itemNotifications.setTitle(title);
        }
    }

    private void performLogout() {
        int currentUserId = sessionManager.getUserId();
        if (currentUserId != -1 && notificationRepository != null) {
            notificationRepository.clearNotificationsForUser(currentUserId);
        }
        sessionManager.logout();
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        } catch (Exception ignored) {
        }
        try {
            com.facebook.login.LoginManager.getInstance().logOut();
        } catch (Exception ignored) {
        }
        try {
            com.google.android.gms.auth.api.signin.GoogleSignInOptions gso =
                    new com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                            com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN).build();
            com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(this, gso).signOut();
        } catch (Exception ignored) {
        }

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
                final EditText etReason = new EditText(AcceuilActivity.this);
                etReason.setHint("Raison du signalement (ex: contenu inadapté)");
                new AlertDialog.Builder(AcceuilActivity.this)
                        .setTitle("Signaler la publication 🚩")
                        .setMessage("Voulez-vous signaler cette publication aux modérateurs ?")
                        .setView(etReason)
                        .setPositiveButton("Signaler", (dialog, which) -> {
                            String reason = etReason.getText().toString().trim();
                            boolean reported = postRepository.reportPost(post.getId(), sessionManager.getUserId(), reason);
                            if (reported) {
                                Toasty.success(AcceuilActivity.this, "Publication signalée aux modérateurs", Toasty.LENGTH_SHORT).show();
                            } else {
                                Toasty.error(AcceuilActivity.this, "Erreur lors du signalement", Toasty.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
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
            public void onImageClick(Post post) {
                showImagePreviewDialog(post);
            }

            @Override
            public void onVideoClick(String videoPath) {
                showVideoPlayerDialog(videoPath);
            }
        });

        rvPosts.setAdapter(postAdapter);
    }

    private void checkAppAnnouncement() {
        TextView tvAppAnnouncement = findViewById(R.id.tvAppAnnouncement);
        if (tvAppAnnouncement == null) return;

        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("app_update_version")
                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot versionSnapshot) {
                        Long requiredVersionObj = versionSnapshot.getValue(Long.class);
                        int requiredVersion = requiredVersionObj != null ? requiredVersionObj.intValue() : 0;
                        int installedVersion = 1;
                        try {
                            installedVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                        } catch (Exception ignored) {}

                        final boolean isUpToDate = installedVersion >= requiredVersion && requiredVersion > 0;

                        com.google.firebase.database.FirebaseDatabase.getInstance().getReference("app_announcement")
                                .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                                    @Override
                                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                                        String announcement = snapshot.getValue(String.class);
                                        if (!isUpToDate && announcement != null && !announcement.trim().isEmpty()) {
                                            tvAppAnnouncement.setText("📢 " + announcement);
                                            tvAppAnnouncement.setVisibility(View.VISIBLE);

                                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                                if (tvAppAnnouncement != null) {
                                                    tvAppAnnouncement.setVisibility(View.GONE);
                                                }
                                            }, 30000);
                                        } else {
                                            tvAppAnnouncement.setVisibility(View.GONE);
                                        }
                                    }

                                    @Override
                                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                                        tvAppAnnouncement.setVisibility(View.GONE);
                                    }
                                });
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    }
                });
    }

    private void loadCurrentUserAvatar() {
        User user = userRepository.getUserById(sessionManager.getUserId());
        String profileImage = user != null ? user.getProfileImage() : null;
        if (profileImage == null || profileImage.trim().isEmpty()) {
            profileImage = sessionManager.getProfileImage();
        }
        if (profileImage != null && !profileImage.trim().isEmpty()) {
            ImageUtils.loadProfileImage(this, profileImage, imgUserAvatar);
        } else {
            imgUserAvatar.setImageResource(R.drawable.ic_profile);
        }
    }

    private void showPostMediaSelectionOptions() {
        String[] options = {
                "Prendre/Choisir une photo",
                "Plusieurs photos (Jusqu'à 10)"
        };
        new AlertDialog.Builder(this)
                .setTitle("Joindre une photo")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchPostImageCropper();
                    } else if (which == 1) {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        multiplePostImagePickerLauncher.launch(Intent.createChooser(intent, "Sélectionner des photos"));
                    }
                })
                .show();
    }

    private void publishPost() {
        String content = etPostContent.getText().toString().trim();

        if (content.isEmpty() && selectedPostImageUris.isEmpty() && selectedPostImageUri == null && selectedPostVideoUri == null) {
            Toasty.warning(this, "Écrivez un texte ou sélectionnez une photo/vidéo", Toasty.LENGTH_SHORT).show();
            return;
        }

        if (progressPost != null) progressPost.setVisibility(View.VISIBLE);
        if (btnPublishPost != null) btnPublishPost.setVisibility(View.GONE);

        boolean isSensitive = cbIsSensitive != null && cbIsSensitive.isChecked();

        if (selectedPostVideoUri != null) {
            savedPostVideoPath = ImageUtils.saveVideoToInternalStorage(this, selectedPostVideoUri);
            if (PostRepository.isNetworkAvailable(this)) {
                String localVideoPath = savedPostVideoPath;
                if (progressPost != null) progressPost.setVisibility(View.VISIBLE);
                if (btnPublishPost != null) btnPublishPost.setVisibility(View.GONE);

                FirebaseHelper.getInstance().uploadMedia("post_videos", selectedPostVideoUri, new FirebaseHelper.OnUploadCompleteListener() {
                    @Override
                    public void onSuccess(String downloadUrl) {
                        uploadPostImagesAndFinalize(content, downloadUrl, isSensitive);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        uploadPostImagesAndFinalize(content, localVideoPath, isSensitive);
                    }
                });
                return;
            }
        }

        uploadPostImagesAndFinalize(content, savedPostVideoPath, isSensitive);
    }

    private void uploadPostImagesAndFinalize(String content, String videoPath, boolean isSensitive) {
        if (!selectedPostImageUris.isEmpty()) {
            StringBuilder base64Builder = new StringBuilder();
            for (Uri uri : selectedPostImageUris) {
                String base64 = ImageUtils.uriToBase64(this, uri);
                if (base64 != null && !base64.trim().isEmpty()) {
                    if (base64Builder.length() > 0) base64Builder.append("||");
                    base64Builder.append(base64);
                }
            }
            finalizePostPublish(content, base64Builder.toString(), videoPath, isSensitive);
            return;
        }

        if (selectedPostImageUri != null) {
            String base64Image = ImageUtils.uriToBase64(this, selectedPostImageUri);
            if (base64Image != null && !base64Image.trim().isEmpty()) {
                savedPostImagePath = base64Image;
            } else {
                savedPostImagePath = ImageUtils.saveImageToInternalStorage(this, selectedPostImageUri);
            }
        }

        finalizePostPublish(content, savedPostImagePath, videoPath, isSensitive);
    }

    private void uploadMultiplePostImages(String content, String videoPath, boolean isSensitive, int index, StringBuilder urlsBuilder) {
        if (index >= selectedPostImageUris.size() || index >= 10) {
            finalizePostPublish(content, urlsBuilder.toString(), videoPath, isSensitive);
            return;
        }

        Uri uri = selectedPostImageUris.get(index);
        FirebaseHelper.getInstance().uploadMedia("post_images", uri, new FirebaseHelper.OnUploadCompleteListener() {
            @Override
            public void onSuccess(String downloadUrl) {
                if (urlsBuilder.length() > 0) {
                    urlsBuilder.append("||");
                }
                urlsBuilder.append(downloadUrl);
                uploadMultiplePostImages(content, videoPath, isSensitive, index + 1, urlsBuilder);
            }

            @Override
            public void onFailure(Exception e) {
                String localPath = ImageUtils.saveImageToInternalStorage(AcceuilActivity.this, uri);
                if (localPath != null) {
                    if (urlsBuilder.length() > 0) {
                        urlsBuilder.append("||");
                    }
                    urlsBuilder.append(localPath);
                }
                uploadMultiplePostImages(content, videoPath, isSensitive, index + 1, urlsBuilder);
            }
        });
    }

    private void finalizePostPublish(String content, String imagePath, String videoPath, boolean isSensitive) {
        if (progressPost != null) progressPost.setVisibility(View.VISIBLE);
        if (btnPublishPost != null) btnPublishPost.setVisibility(View.GONE);

        boolean success = postRepository.createPost(sessionManager.getUserId(), content, imagePath, videoPath, isSensitive);

        if (progressPost != null) progressPost.setVisibility(View.GONE);

        if (success) {
            if (!PostRepository.isNetworkAvailable(this)) {
                Toasty.warning(this, "⚠️ Mode Hors-Ligne : Publication enregistrée localement ! Elle sera envoyée au Cloud dès le retour du réseau.", Toasty.LENGTH_LONG).show();
            } else {
                Toasty.success(this, "Publication partagée !", Toasty.LENGTH_SHORT).show();
            }
            etPostContent.setText("");
            selectedPostImageUri = null;
            selectedPostImageUris.clear();
            selectedPostVideoUri = null;
            savedPostImagePath = null;
            savedPostVideoPath = null;
            imgPostPreview.setVisibility(View.GONE);
            if (cbIsSensitive != null) {
                cbIsSensitive.setVisibility(View.GONE);
                cbIsSensitive.setChecked(false);
            }
            if (layoutVideoPreview != null) layoutVideoPreview.setVisibility(View.GONE);
            if (btnPublishPost != null) btnPublishPost.setVisibility(View.VISIBLE);
            loadPosts();
        } else {
            if (btnPublishPost != null) btnPublishPost.setVisibility(View.VISIBLE);
            Toasty.error(this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
        }
    }

    private void showVideoPlayerDialog(String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) return;

        dismissActiveDialogs();

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

    private void loadPosts() {
        List<Post> newPosts = postRepository.getAllPosts(sessionManager.getUserId());
        fullPostList.clear();
        fullPostList.addAll(newPosts);

        postList.clear();
        postList.addAll(newPosts);
        postAdapter.notifyDataSetChanged();
    }

    /**
     * Filtre le fil d'actualités par hashtag (#) ou mot-clé.
     * @param tag Le hashtag ou mot-clé sélectionné.
     */
    public void filterPostsByTag(String tag) {
        filterPosts(tag);
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

        dismissActiveDialogs();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);
        ImageButton ibDownload = dialogView.findViewById(R.id.ibDownloadPreview);
        if (ibDownload != null) {
            ibDownload.bringToFront();
            ibDownload.setOnClickListener(v -> ImageUtils.downloadImageToGallery(this, imagePath));
        }
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
                if (activeImageDialog != null && activeImageDialog.isShowing())
                    activeImageDialog.dismiss();
            });
        }

        activeImageDialog.setOnDismissListener(d -> activeImageDialog = null);
        activeImageDialog.show();
    }

    private void showImagePreviewDialog(Post post) {
        if (post == null || post.getImagePath() == null || post.getImagePath().trim().isEmpty())
            return;

        dismissActiveDialogs();

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);
        ImageButton ibDownload = dialogView.findViewById(R.id.ibDownloadPreview);
        if (ibDownload != null) {
            ibDownload.bringToFront();
            ibDownload.setOnClickListener(v -> ImageUtils.downloadImageToGallery(this, post.getImagePath()));
        }
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

        MenuItem messageItem = menu.findItem(R.id.action_messages);
        if (messageItem != null) {
            View actionView = messageItem.getActionView();
            if (actionView != null) {
                tvMessageBadge = actionView.findViewById(R.id.tvMessageBadge);
                actionView.setOnClickListener(v -> {
                    startActivity(new Intent(AcceuilActivity.this, MainActivity.class));
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
        if (sessionManager == null) return;
        int currentUserId = sessionManager.getUserId();
        int unreadNotifs = notificationRepository != null ? notificationRepository.getUnreadNotificationCount(currentUserId) : 0;
        int unreadMessages = conversationRepository != null ? conversationRepository.getTotalUnreadCount(currentUserId) : 0;

        if (tvNotificationBadge != null) {
            if (unreadNotifs > 0) {
                tvNotificationBadge.setVisibility(View.VISIBLE);
                tvNotificationBadge.setText(unreadNotifs > 9 ? "9+" : String.valueOf(unreadNotifs));
            } else {
                tvNotificationBadge.setVisibility(View.GONE);
            }
        }

        if (tvMessageBadge != null) {
            if (unreadMessages > 0) {
                tvMessageBadge.setVisibility(View.VISIBLE);
                tvMessageBadge.setText(unreadMessages > 9 ? "9+" : String.valueOf(unreadMessages));
            } else {
                tvMessageBadge.setVisibility(View.GONE);
            }
        }

        int totalUnread = unreadNotifs + unreadMessages;
        NotificationHelper.updateAppBadge(this, totalUnread);
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
        } else if (id == R.id.action_friends) {
            startActivity(new Intent(AcceuilActivity.this, AmiActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkAndSyncOfflineData();
        loadPosts();
        loadStories();
        updateNotificationBadge();
        loadCurrentUserAvatar();
        android.widget.FrameLayout adBannerContainer = findViewById(R.id.adBannerContainer);
        AdMobManager.resumeBannerAd(adBannerContainer);
        if (adBannerContainer != null && adBannerContainer.getChildCount() == 0) {
            AdMobManager.loadBannerAd(this, adBannerContainer);
        }
        updateNavHeaderData();
        updateDrawerBadges();
        if (navigationView != null) {
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }

    private void checkAndSyncOfflineData() {
        if (PostRepository.isNetworkAvailable(this)) {
            int postsSynced = PostRepository.syncPendingPosts(this);
            int messagesSynced = MessageRepository.syncPendingMessages(this);
            if (postsSynced > 0 || messagesSynced > 0) {
                Toasty.success(this, "🚀 Connexion rétablie : " + (postsSynced + messagesSynced) + " élément(s) synchronisé(s) !", Toasty.LENGTH_LONG).show();
                loadPosts();
            }
        } else {
            List<Post> pendingPosts = PostRepository.getOfflinePendingPosts(this);
            List<Message> pendingMessages = MessageRepository.getOfflinePendingMessages(this);
            int pendingCount = pendingPosts.size() + pendingMessages.size();
            if (pendingCount > 0) {
                Toasty.warning(this, "⚡ Mode Hors-Ligne : " + pendingCount + " élément(s) en attente de synchronisation.", Toasty.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        android.widget.FrameLayout adBannerContainer = findViewById(R.id.adBannerContainer);
        AdMobManager.pauseBannerAd(adBannerContainer);
    }

    @Override
    protected void onStop() {
        super.onStop();
        dismissActiveDialogs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissActiveDialogs();
        android.widget.FrameLayout adBannerContainer = findViewById(R.id.adBannerContainer);
        AdMobManager.destroyBannerAd(adBannerContainer);
    }

    private void dismissActiveDialogs() {
        if (storyTimerHandler != null && storyTimerRunnable != null) {
            storyTimerHandler.removeCallbacks(storyTimerRunnable);
        }
        if (activeStoryDialog != null && activeStoryDialog.isShowing()) {
            activeStoryDialog.dismiss();
            activeStoryDialog = null;
        }
        if (activeVideoDialog != null && activeVideoDialog.isShowing()) {
            activeVideoDialog.dismiss();
            activeVideoDialog = null;
        }
        if (activeImageDialog != null && activeImageDialog.isShowing()) {
            activeImageDialog.dismiss();
            activeImageDialog = null;
        }
    }
}

