package td.teladoumbaobabtd;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptateur RecyclerView pour l'affichage des publications (posts) sur le fil d'actualité.
 * Gère les photos, vidéos, textes, j'aime, réactions émojis (❤️, 😂, 👍, etc.), commentaires, partages,
 * l'édition/suppression pour l'auteur, le zoom d'image et la lecture vidéo.
 */
public class PostAdapter extends RecyclerView.Adapter<PostAdapter.PostViewHolder> {

    private final List<Post> postList;
    private final int currentUserId;
    private final OnPostClickListener listener;

    public interface OnPostClickListener {
        void onLikeClick(Post post);
        void onReactionClick(Post post, String reactionType);
        void onCommentClick(Post post);
        void onShareClick(Post post);
        void onReportClick(Post post);
        void onEditClick(Post post);
        void onDeleteClick(Post post);
        void onImageClick(String imagePath);
        void onVideoClick(String videoPath);
    }

    public PostAdapter(List<Post> postList, int currentUserId, OnPostClickListener listener) {
        this.postList = postList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        return new PostViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.tvPostAuthorName.setText(post.getAuthorName() != null ? post.getAuthorName() : "Utilisateur");
        holder.tvPostTime.setText(formatTime(post.getCreatedAt()));

        ImageUtils.loadProfileImage(holder.itemView.getContext(), post.getAuthorProfileImage(), holder.imgPostAuthorAvatar);
        holder.imgPostAuthorAvatar.setOnClickListener(v -> {
            if (listener != null && post.getAuthorProfileImage() != null && !post.getAuthorProfileImage().isEmpty()) {
                listener.onImageClick(post.getAuthorProfileImage());
            }
        });

        if (post.getContent() != null && !post.getContent().trim().isEmpty()) {
            holder.tvPostContent.setVisibility(View.VISIBLE);
            holder.tvPostContent.setText(post.getContent());
        } else {
            holder.tvPostContent.setVisibility(View.GONE);
        }

        // Image du post
        if (post.getImagePath() != null && !post.getImagePath().trim().isEmpty()) {
            holder.imgPostImage.setVisibility(View.VISIBLE);
            ImageUtils.loadFullImage(holder.itemView.getContext(), post.getImagePath(), holder.imgPostImage);
            holder.imgPostImage.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onImageClick(post.getImagePath());
                }
            });
        } else {
            holder.imgPostImage.setVisibility(View.GONE);
            holder.imgPostImage.setOnClickListener(null);
        }

        // Vidéo du post
        if (post.getVideoPath() != null && !post.getVideoPath().trim().isEmpty()) {
            if (holder.layoutPostVideo != null) {
                holder.layoutPostVideo.setVisibility(View.VISIBLE);
                if (holder.imgPostVideoThumbnail != null) {
                    ImageUtils.loadVideoThumbnail(holder.itemView.getContext(), post.getVideoPath(), holder.imgPostVideoThumbnail);
                }
                View.OnClickListener videoClickListener = v -> {
                    if (listener != null) {
                        listener.onVideoClick(post.getVideoPath());
                    }
                };
                holder.layoutPostVideo.setOnClickListener(videoClickListener);
                if (holder.ibPlayPostVideo != null) {
                    holder.ibPlayPostVideo.setOnClickListener(videoClickListener);
                }
            }
        } else {
            if (holder.layoutPostVideo != null) {
                holder.layoutPostVideo.setVisibility(View.GONE);
            }
        }

        String stats = post.getLikesCount() + " j'aime  •  " + post.getCommentsCount() + " commentaires  •  " + post.getSharesCount() + " partages";
        holder.tvLikesCount.setText(stats);

        String reaction = post.getUserReactionType();
        if ("LOVE".equalsIgnoreCase(reaction)) {
            holder.btnLikePost.setText("❤️ J'adore");
            holder.btnLikePost.setTextColor(Color.RED);
        } else if ("HAHA".equalsIgnoreCase(reaction)) {
            holder.btnLikePost.setText("😆 Haha");
            holder.btnLikePost.setTextColor(Color.parseColor("#FF9800"));
        } else if ("WOW".equalsIgnoreCase(reaction)) {
            holder.btnLikePost.setText("😮 Wouah");
            holder.btnLikePost.setTextColor(Color.parseColor("#2196F3"));
        } else if ("SAD".equalsIgnoreCase(reaction)) {
            holder.btnLikePost.setText("😢 Triste");
            holder.btnLikePost.setTextColor(Color.parseColor("#9C27B0"));
        } else if ("ANGRY".equalsIgnoreCase(reaction)) {
            holder.btnLikePost.setText("😡 En colère");
            holder.btnLikePost.setTextColor(Color.RED);
        } else if ("LIKE".equalsIgnoreCase(reaction) || post.isLikedByCurrentUser()) {
            holder.btnLikePost.setText("👍 J'aime");
            holder.btnLikePost.setTextColor(Color.parseColor("#2196F3"));
        } else {
            holder.btnLikePost.setText("🤍 J'aime");
            holder.btnLikePost.setTextColor(Color.GRAY);
        }

        holder.btnLikePost.setOnClickListener(v -> showReactionPopup(holder.itemView.getContext(), post));
        holder.btnLikePost.setOnLongClickListener(v -> {
            showReactionPopup(holder.itemView.getContext(), post);
            return true;
        });

        if (holder.btnCommentPost != null) {
            holder.btnCommentPost.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onCommentClick(post);
                }
            });
        }

        if (holder.btnSharePost != null) {
            holder.btnSharePost.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShareClick(post);
                }
            });
        }

        if (holder.btnReportPost != null) {
            holder.btnReportPost.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReportClick(post);
                }
            });
        }

        if (post.getUserId() == currentUserId) {
            boolean isEditable = TimeUtils.isWithinHours(post.getCreatedAt(), 10);
            if (holder.ibEditPost != null) {
                holder.ibEditPost.setVisibility(isEditable ? View.VISIBLE : View.GONE);
                holder.ibEditPost.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onEditClick(post);
                    }
                });
            }
            holder.ibDeletePost.setVisibility(View.VISIBLE);
            holder.ibDeletePost.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(post);
                }
            });
        } else {
            if (holder.ibEditPost != null) {
                holder.ibEditPost.setVisibility(View.GONE);
            }
            holder.ibDeletePost.setVisibility(View.GONE);
        }
    }

    private void showReactionPopup(android.content.Context context, Post post) {
        String[] reactions = new String[]{
                "👍 J'aime",
                "❤️ J'adore",
                "😆 Haha",
                "😮 Wouah",
                "😢 Triste",
                "😡 En colère",
                "❌ Retirer ma réaction"
        };

        new AlertDialog.Builder(context)
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
                    if (listener != null) {
                        listener.onReactionClick(post, selectedType);
                    }
                })
                .show();
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    private String formatTime(String dateTime) {
        try {
            if (dateTime == null || dateTime.trim().isEmpty()) {
                return "À l'instant";
            }
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = inputFormat.parse(dateTime);
            if (date == null) return "À l'instant";

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM à HH:mm", Locale.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return "À l'instant";
        }
    }

    public static class PostViewHolder extends RecyclerView.ViewHolder {

        ImageView imgPostAuthorAvatar;
        TextView tvPostAuthorName;
        TextView tvPostTime;
        ImageButton ibEditPost;
        ImageButton ibDeletePost;
        TextView tvPostContent;
        ImageView imgPostImage;

        View layoutPostVideo;
        ImageView imgPostVideoThumbnail;
        ImageView ibPlayPostVideo;

        Button btnLikePost;
        Button btnCommentPost;
        Button btnSharePost;
        ImageButton btnReportPost;
        TextView tvLikesCount;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);

            imgPostAuthorAvatar = itemView.findViewById(R.id.imgPostAuthorAvatar);
            tvPostAuthorName = itemView.findViewById(R.id.tvPostAuthorName);
            tvPostTime = itemView.findViewById(R.id.tvPostTime);
            ibEditPost = itemView.findViewById(R.id.ibEditPost);
            ibDeletePost = itemView.findViewById(R.id.ibDeletePost);
            tvPostContent = itemView.findViewById(R.id.tvPostContent);
            imgPostImage = itemView.findViewById(R.id.imgPostImage);

            layoutPostVideo = itemView.findViewById(R.id.layoutPostVideo);
            imgPostVideoThumbnail = itemView.findViewById(R.id.imgPostVideoThumbnail);
            ibPlayPostVideo = itemView.findViewById(R.id.ibPlayPostVideo);

            btnLikePost = itemView.findViewById(R.id.btnLikePost);
            btnCommentPost = itemView.findViewById(R.id.btnCommentPost);
            btnSharePost = itemView.findViewById(R.id.btnSharePost);
            btnReportPost = itemView.findViewById(R.id.btnReportPost);
            tvLikesCount = itemView.findViewById(R.id.tvLikesCount);
        }
    }
}