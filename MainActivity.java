package td.teladoumbaobabtd;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import td.teladoumbaobabtd.repository.PostRepository;
import td.teladoumbaobabtd.repository.UserRepository;

public class MainActivity extends AppCompatActivity {

    private ImageView imgUserAvatar, imgPostPreview;
    private EditText etPostContent;
    private ImageButton ibAttachPostImage;
    private Button btnPublishPost;
    private RecyclerView rvPosts;
    private SwipeRefreshLayout swipeRefreshLayout;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ActionBarDrawerToggle drawerToggle;

    private PostRepository postRepository;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    private PostAdapter postAdapter;
    private final List<Post> postList = new ArrayList<>();
    private final List<Post> fullPostList = new ArrayList<>();

    private Uri selectedPostImageUri;
    private String savedPostImagePath;

    private final ActivityResultLauncher<String> postImagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    selectedPostImageUri = uri;
                    imgPostPreview.setImageURI(uri);
                    imgPostPreview.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        postRepository = new PostRepository(this);
        userRepository = new UserRepository(this);

        initViews();
        setupNavigationDrawer(toolbar);
        checkPermissions();
        setupRecyclerView();

        loadCurrentUserAvatar();

        ibAttachPostImage.setOnClickListener(v -> postImagePickerLauncher.launch("image/*"));
        btnPublishPost.setOnClickListener(v -> publishPost());

        loadPosts();

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
        btnPublishPost = findViewById(R.id.btnPublishPost);
        rvPosts = findViewById(R.id.rvPosts);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                loadPosts();
                loadCurrentUserAvatar();
                swipeRefreshLayout.setRefreshing(false);
            });
        }

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
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
                startActivity(new Intent(MainActivity.this, AffichageMsg.class));
                return true;
            } else if (id == R.id.nav_friends) {
                startActivity(new Intent(MainActivity.this, AmiActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, ParametreActivity.class));
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
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
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
                Intent intent = new Intent(MainActivity.this, CommenterActivity.class);
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
                Intent intent = new Intent(MainActivity.this, CommenterActivity.class);
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

        if (content.isEmpty() && selectedPostImageUri == null) {
            Toast.makeText(this, "Écrivez un texte ou sélectionnez une photo", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPostImageUri != null) {
            savedPostImagePath = ImageUtils.saveImageToInternalStorage(this, selectedPostImageUri);
        }

        boolean success = postRepository.createPost(sessionManager.getUserId(), content, savedPostImagePath);

        if (success) {
            Toasty.success(this, "Publication partagée !", Toasty.LENGTH_SHORT).show();
            etPostContent.setText("");
            selectedPostImageUri = null;
            savedPostImagePath = null;
            imgPostPreview.setVisibility(View.GONE);
            loadPosts();
        } else {
            Toasty.error(this, "Erreur lors de la publication", Toasty.LENGTH_SHORT).show();
        }
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
                        Toasty.success(MainActivity.this, "Publication modifiée !", Toasty.LENGTH_SHORT).show();
                        loadPosts();
                    } else {
                        Toasty.error(MainActivity.this, "Erreur de modification", Toasty.LENGTH_SHORT).show();
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
                        Toasty.success(MainActivity.this, "Publication supprimée", Toasty.LENGTH_SHORT).show();
                        loadPosts();
                    } else {
                        Toasty.warning(MainActivity.this, "Erreur de suppression", Toasty.LENGTH_SHORT).show();
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

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }
        dialogView.setOnClickListener(v -> dialog.dismiss());

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

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (drawerToggle != null && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        int id = item.getItemId();
        if (id == R.id.action_messages) {
            startActivity(new Intent(MainActivity.this, AffichageMsg.class));
            return true;
        } else if (id == R.id.action_friends) {
            startActivity(new Intent(MainActivity.this, AmiActivity.class));
            return true;
        } else if (id == R.id.action_profile) {
            startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(MainActivity.this, ParametreActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }



    @Override
    protected void onResume() {
        super.onResume();
        loadPosts();
        loadCurrentUserAvatar();
        updateNavHeaderData();
        if (navigationView != null) {
            navigationView.setCheckedItem(R.id.nav_home);
        }
    }
}
