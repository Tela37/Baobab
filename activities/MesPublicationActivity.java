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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.PostRepository;

/**
 * Activité récapitulant toutes les publications de l'utilisateur connecté.
 */
public class MesPublicationActivity extends AppCompatActivity {

    private RecyclerView rvMyPosts;
    private PostAdapter postAdapter;
    private final List<Post> myPostsList = new ArrayList<>();
    private PostRepository postRepository;
    private int currentUserId;
    private AlertDialog activeImageDialog;
    private AlertDialog activeVideoDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mes_publications);

        Toolbar toolbar = findViewById(R.id.toolbarMesPublications);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        postRepository = new PostRepository(this);
        SessionManager sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        rvMyPosts = findViewById(R.id.rvMyPosts);
        rvMyPosts.setLayoutManager(new LinearLayoutManager(this));

        postAdapter = new PostAdapter(myPostsList, currentUserId, new PostAdapter.OnPostClickListener() {
            @Override
            public void onLikeClick(Post post) {
                postRepository.toggleLikePost(post.getId(), currentUserId);
                loadMyPosts();
            }

            @Override
            public void onReactionClick(Post post, String reactionType) {
                postRepository.setPostReaction(post.getId(), currentUserId, reactionType);
                loadMyPosts();
            }

            @Override
            public void onCommentClick(Post post) {
                Intent intent = new Intent(MesPublicationActivity.this, CommenterActivity.class);
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
                loadMyPosts();
            }

            @Override
            public void onReportClick(Post post) {
                final android.widget.EditText etReason = new android.widget.EditText(MesPublicationActivity.this);
                etReason.setHint("Raison du signalement (ex: contenu inadapté)");
                new AlertDialog.Builder(MesPublicationActivity.this)
                        .setTitle("Signaler la publication 🚩")
                        .setMessage("Voulez-vous signaler cette publication aux modérateurs ?")
                        .setView(etReason)
                        .setPositiveButton("Signaler", (dialog, which) -> {
                            String reason = etReason.getText().toString().trim();
                            boolean reported = postRepository.reportPost(post.getId(), currentUserId, reason);
                            if (reported) {
                                Toasty.success(MesPublicationActivity.this, "Publication signalée aux modérateurs", Toasty.LENGTH_SHORT).show();
                            } else {
                                Toasty.error(MesPublicationActivity.this, "Erreur lors du signalement", Toasty.LENGTH_SHORT).show();
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
                if (post.getUserId() == currentUserId) {
                    postRepository.deletePost(post.getId(), currentUserId);
                    loadMyPosts();
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

        rvMyPosts.setAdapter(postAdapter);
        loadMyPosts();
    }

    private void loadMyPosts() {
        if (currentUserId != -1 && postRepository != null) {
            List<Post> posts = postRepository.getPostsByUserId(currentUserId, currentUserId);
            myPostsList.clear();
            myPostsList.addAll(posts);
            postAdapter.notifyDataSetChanged();
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
        if (post == null || post.getImagePath() == null || post.getImagePath().trim().isEmpty()) return;

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
                if (activeImageDialog != null && activeImageDialog.isShowing()) activeImageDialog.dismiss();
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
                if (activeVideoDialog != null && activeVideoDialog.isShowing()) activeVideoDialog.dismiss();
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
    protected void onResume() {
        super.onResume();
        loadMyPosts();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

