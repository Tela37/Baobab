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
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.CommentRepository;
import td.teladoum.baobab.repositories.PostRepository;

/**
 * Activité d'affichage et d'ajout de commentaires sur une publication.
 * Affiche la publication cible, la liste des commentaires et permet de répondre spécifiquement à un commentaire.
 */
public class CommenterActivity extends AppCompatActivity {

    private ImageView imgPostAuthorAvatar, imgPostImage;
    private TextView tvPostAuthorName, tvPostTime, tvPostContent;
    private Button btnLikePost, btnSharePost, btnReportPost;

    private RecyclerView rvComments;
    private LinearLayout layoutReplyingTo;
    private TextView tvReplyingToUser;
    private ImageButton ibCancelReply;

    private EditText etCommentInput;
    private ImageButton btnSendComment;

    private PostRepository postRepository;
    private CommentRepository commentRepository;
    private SessionManager sessionManager;

    private CommentAdapter commentAdapter;
    private final List<Comment> commentList = new ArrayList<>();

    private int postId;
    private int currentUserId;
    private Post currentPost;
    private Integer replyingToCommentId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_commenter);

        Toolbar toolbar = findViewById(R.id.toolbarCommenter);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        postId = getIntent().getIntExtra("post_id", -1);
        if (postId == -1) {
            finish();
            return;
        }

        postRepository = new PostRepository(this);
        commentRepository = new CommentRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        setupRecyclerView();

        loadPostDetails();
        loadComments();

        btnLikePost.setOnClickListener(v -> showReactionPopup());
        btnLikePost.setOnLongClickListener(v -> {
            showReactionPopup();
            return true;
        });
        btnSharePost.setOnClickListener(v -> sharePost());
        btnReportPost.setOnClickListener(v -> showReportDialog());
        btnSendComment.setOnClickListener(v -> submitComment());
        ibCancelReply.setOnClickListener(v -> cancelReplyMode());
    }

    private void initViews() {
        imgPostAuthorAvatar = findViewById(R.id.imgPostAuthorAvatar);
        tvPostAuthorName = findViewById(R.id.tvPostAuthorName);
        tvPostTime = findViewById(R.id.tvPostTime);
        tvPostContent = findViewById(R.id.tvPostContent);
        imgPostImage = findViewById(R.id.imgPostImage);
        btnLikePost = findViewById(R.id.btnLikePost);
        btnSharePost = findViewById(R.id.btnSharePost);
        btnReportPost = findViewById(R.id.btnReportPost);

        rvComments = findViewById(R.id.rvComments);
        layoutReplyingTo = findViewById(R.id.layoutReplyingTo);
        tvReplyingToUser = findViewById(R.id.tvReplyingToUser);
        ibCancelReply = findViewById(R.id.ibCancelReply);

        etCommentInput = findViewById(R.id.etCommentInput);
        btnSendComment = findViewById(R.id.btnSendComment);
    }

    private void setupRecyclerView() {
        rvComments.setLayoutManager(new LinearLayoutManager(this));
        commentAdapter = new CommentAdapter(commentList, new CommentAdapter.OnCommentClickListener() {
            @Override
            public void onReplyClick(Comment comment) {
                setReplyMode(comment);
            }

            @Override
            public void onLikeClick(Comment comment) {
                commentRepository.toggleLikeComment(comment.getId(), currentUserId);
                loadComments();
            }

            @Override
            public void onReactionClick(Comment comment, String reactionType) {
                commentRepository.setCommentReaction(comment.getId(), currentUserId, reactionType);
                loadComments();
            }

            @Override
            public void onUserClick(Comment comment) {
                Intent intent = new Intent(CommenterActivity.this, PageUtilisateurActivity.class);
                intent.putExtra("user_id", comment.getUserId());
                startActivity(intent);
            }
        });
        rvComments.setAdapter(commentAdapter);
    }

    private void loadPostDetails() {
        List<Post> posts = postRepository.getAllPosts(currentUserId);
        for (Post p : posts) {
            if (p.getId() == postId) {
                currentPost = p;
                break;
            }
        }

        if (currentPost == null) {
            Toasty.error(this, getString(R.string.toast_post_not_found), Toasty.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvPostAuthorName.setText(currentPost.getAuthorName() != null ? currentPost.getAuthorName() : "Utilisateur");
        tvPostTime.setText(formatTime(currentPost.getCreatedAt()));

        ImageUtils.loadProfileImage(this, currentPost.getAuthorProfileImage(), imgPostAuthorAvatar);

        View.OnClickListener postAuthorClickListener = v -> {
            Intent intent = new Intent(CommenterActivity.this, PageUtilisateurActivity.class);
            intent.putExtra("user_id", currentPost.getUserId());
            startActivity(intent);
        };
        imgPostAuthorAvatar.setOnClickListener(postAuthorClickListener);
        tvPostAuthorName.setOnClickListener(postAuthorClickListener);

        if (currentPost.getContent() != null && !currentPost.getContent().trim().isEmpty()) {
            tvPostContent.setVisibility(View.VISIBLE);
            tvPostContent.setText(currentPost.getContent());
        } else {
            tvPostContent.setVisibility(View.GONE);
        }

        if (currentPost.getImagePath() != null && !currentPost.getImagePath().trim().isEmpty()) {
            imgPostImage.setVisibility(View.VISIBLE);
            ImageUtils.loadFullImage(this, currentPost.getImagePath(), imgPostImage);
            imgPostImage.setOnClickListener(v -> showImagePreviewDialog(currentPost.getImagePath()));
        } else {
            imgPostImage.setVisibility(View.GONE);
            imgPostImage.setOnClickListener(null);
        }

        updateLikeButtonState();
    }

    private void updateLikeButtonState() {
        if (currentPost != null) {
            String reaction = currentPost.getUserReactionType();
            if ("LOVE".equalsIgnoreCase(reaction)) {
                btnLikePost.setText("❤️ J'adore (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.RED);
            } else if ("HAHA".equalsIgnoreCase(reaction)) {
                btnLikePost.setText("😆 Haha (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.parseColor("#FF9800"));
            } else if ("WOW".equalsIgnoreCase(reaction)) {
                btnLikePost.setText("😮 Wouah (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.parseColor("#2196F3"));
            } else if ("SAD".equalsIgnoreCase(reaction)) {
                btnLikePost.setText("😢 Triste (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.parseColor("#9C27B0"));
            } else if ("ANGRY".equalsIgnoreCase(reaction)) {
                btnLikePost.setText("😡 En colère (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.RED);
            } else if ("LIKE".equalsIgnoreCase(reaction) || currentPost.isLikedByCurrentUser()) {
                btnLikePost.setText("👍 J'aime (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.parseColor("#2196F3"));
            } else {
                btnLikePost.setText("🤍 J'aime (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.GRAY);
            }
        }
    }

    private void showReactionPopup() {
        if (currentPost == null) return;

        String[] reactions = new String[]{
                "👍 J'aime",
                "❤️ J'adore",
                "😆 Haha",
                "😮 Wouah",
                "😢 Triste",
                "😡 En colère",
                "❌ Retirer ma réaction"
        };

        new AlertDialog.Builder(this)
                .setTitle("Réagir à la publication")
                .setItems(reactions, (dialog, which) -> {
                    String selectedType = null;
                    switch (which) {
                        case 0: selectedType = "LIKE"; break;
                        case 1: selectedType = "LOVE"; break;
                        case 2: selectedType = "HAHA"; break;
                        case 3: selectedType = "WOW"; break;
                        case 4: selectedType = "SAD"; break;
                        case 5: selectedType = "ANGRY"; break;
                        case 6: selectedType = null; break;
                    }
                    applyReaction(selectedType);
                })
                .show();
    }

    private void applyReaction(String reactionType) {
        if (currentPost == null) return;
        if (reactionType == null) {
            if (currentPost.isLikedByCurrentUser()) {
                postRepository.toggleLikePost(postId, currentUserId);
            }
            currentPost.setUserReactionType(null);
        } else {
            if (!currentPost.isLikedByCurrentUser()) {
                postRepository.toggleLikePost(postId, currentUserId);
            }
            postRepository.setPostReaction(postId, currentUserId, reactionType);
            currentPost.setUserReactionType(reactionType);
        }
        loadPostDetails();
    }

    private void showImagePreviewDialog(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        View dialogView = android.view.LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
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

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void loadComments() {
        List<Comment> newComments = commentRepository.getCommentsForPost(postId, currentUserId);
        commentList.clear();
        commentList.addAll(newComments);
        commentAdapter.notifyDataSetChanged();

        commentRepository.loadCommentsFromCloud(postId, () -> {
            List<Comment> cloudComments = commentRepository.getCommentsForPost(postId, currentUserId);
            commentList.clear();
            commentList.addAll(cloudComments);
            commentAdapter.notifyDataSetChanged();
        });
    }

    private void setReplyMode(Comment comment) {
        replyingToCommentId = comment.getId();
        tvReplyingToUser.setText("En réponse à " + comment.getAuthorName() + "...");
        layoutReplyingTo.setVisibility(View.VISIBLE);
        etCommentInput.requestFocus();
    }

    private void cancelReplyMode() {
        replyingToCommentId = null;
        layoutReplyingTo.setVisibility(View.GONE);
    }

    private void submitComment() {
        String content = etCommentInput.getText().toString().trim();
        if (content.isEmpty()) {
            Toasty.warning(this, getString(R.string.toast_enter_comment), Toasty.LENGTH_SHORT).show();
            return;
        }

        boolean success = commentRepository.addComment(postId, replyingToCommentId, currentUserId, content);
        if (success) {
            if (!td.teladoum.baobab.repositories.PostRepository.isNetworkAvailable(this)) {
                Toasty.warning(this, "⚠️ Mode Hors-Ligne : Commentaire enregistré localement !", Toasty.LENGTH_SHORT).show();
            } else {
                Toasty.success(this, getString(R.string.toast_comment_added), Toasty.LENGTH_SHORT).show();
            }
            etCommentInput.setText("");
            cancelReplyMode();
            loadComments();
        } else {
            Toasty.error(this, getString(R.string.toast_comment_add_error), Toasty.LENGTH_SHORT).show();
        }
    }

    private void sharePost() {
        if (currentPost == null) return;

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        String shareText = "Publication de " + currentPost.getAuthorName() + " sur BaobabTD :\n" +
                (currentPost.getContent() != null ? currentPost.getContent() : "");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Partager via"));
    }

    private void showReportDialog() {
        EditText etReason = new EditText(this);
        etReason.setHint("Raison du signalement (spam, contenu inapproprié...)");

        new AlertDialog.Builder(this)
                .setTitle("Signaler la publication")
                .setView(etReason)
                .setPositiveButton("Signaler", (dialog, which) -> {
                    String reason = etReason.getText().toString().trim();
                    boolean reported = commentRepository.reportPost(postId, currentUserId, reason);
                    if (reported) {
                        Toasty.success(this, getString(R.string.toast_post_reported), Toasty.LENGTH_SHORT).show();
                    } else {
                        Toasty.warning(this, getString(R.string.toast_report_error), Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private String formatTime(String dateTime) {
        try {
            if (dateTime == null || dateTime.trim().isEmpty()) return "À l'instant";
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = inputFormat.parse(dateTime);
            if (date == null) return "À l'instant";

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM à HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return "À l'instant";
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

