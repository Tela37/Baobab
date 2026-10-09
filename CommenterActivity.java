package td.teladoumbaobabtd;

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

import td.teladoumbaobabtd.repository.CommentRepository;
import td.teladoumbaobabtd.repository.PostRepository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;

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

        btnLikePost.setOnClickListener(v -> togglePostLike());
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
            Toasty.error(this, "Publication introuvable", Toasty.LENGTH_SHORT).show();
            finish();
            return;
        }

        tvPostAuthorName.setText(currentPost.getAuthorName() != null ? currentPost.getAuthorName() : "Utilisateur");
        tvPostTime.setText(formatTime(currentPost.getCreatedAt()));

        ImageUtils.loadProfileImage(this, currentPost.getAuthorProfileImage(), imgPostAuthorAvatar);

        if (currentPost.getContent() != null && !currentPost.getContent().trim().isEmpty()) {
            tvPostContent.setVisibility(View.VISIBLE);
            tvPostContent.setText(currentPost.getContent());
        } else {
            tvPostContent.setVisibility(View.GONE);
        }

        if (currentPost.getImagePath() != null && !currentPost.getImagePath().trim().isEmpty()) {
            imgPostImage.setVisibility(View.VISIBLE);
            ImageUtils.loadFullImage(this, currentPost.getImagePath(), imgPostImage);
        } else {
            imgPostImage.setVisibility(View.GONE);
        }

        updateLikeButtonState();
    }

    private void updateLikeButtonState() {
        if (currentPost != null) {
            if (currentPost.isLikedByCurrentUser()) {
                btnLikePost.setText("❤️ Aimé (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.RED);
            } else {
                btnLikePost.setText("🤍 J'aime (" + currentPost.getLikesCount() + ")");
                btnLikePost.setTextColor(Color.GRAY);
            }
        }
    }

    private void togglePostLike() {
        if (currentPost != null) {
            postRepository.toggleLikePost(postId, currentUserId);
            loadPostDetails();
        }
    }

    private void loadComments() {
        List<Comment> newComments = commentRepository.getCommentsForPost(postId, currentUserId);
        commentList.clear();
        commentList.addAll(newComments);
        commentAdapter.notifyDataSetChanged();
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
            Toasty.warning(this, "Écrivez un commentaire", Toasty.LENGTH_SHORT).show();
            return;
        }

        boolean success = commentRepository.addComment(postId, replyingToCommentId, currentUserId, content);
        if (success) {
            Toasty.success(this, "Commentaire ajouté", Toasty.LENGTH_SHORT).show();
            etCommentInput.setText("");
            cancelReplyMode();
            loadComments();
        } else {
            Toasty.error(this, "Erreur lors de l'ajout du commentaire", Toasty.LENGTH_SHORT).show();
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
                        Toasty.success(this, "Publication signalée aux modérateurs", Toasty.LENGTH_SHORT).show();
                    } else {
                        Toasty.warning(this, "Erreur lors du signalement", Toasty.LENGTH_SHORT).show();
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
