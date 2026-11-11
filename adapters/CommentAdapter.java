package td.teladoum.baobab.adapters;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.CommentViewHolder> {

    private final List<Comment> commentList;
    private final OnCommentClickListener listener;

    public interface OnCommentClickListener {
        void onReplyClick(Comment comment);
        void onLikeClick(Comment comment);
        void onReactionClick(Comment comment, String reactionType);
        void onUserClick(Comment comment);
    }

    public CommentAdapter(List<Comment> commentList, OnCommentClickListener listener) {
        this.commentList = commentList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
        return new CommentViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CommentViewHolder holder, int position) {
        Comment comment = commentList.get(position);

        holder.tvCommentAuthorName.setText(comment.getAuthorName() != null ? comment.getAuthorName() : "Utilisateur");
        holder.tvCommentContent.setText(comment.getContent());
        holder.tvCommentTime.setText(formatTime(comment.getCreatedAt()));

        ImageUtils.loadProfileImage(holder.itemView.getContext(), comment.getAuthorProfileImage(), holder.imgCommentAuthorAvatar);

        // Sub-comment indentation
        if (comment.getParentId() != null) {
            holder.layoutCommentContainer.setPadding(100, 8, 16, 8);
        } else {
            holder.layoutCommentContainer.setPadding(16, 8, 16, 8);
        }

        holder.tvCommentLikesCount.setText(String.valueOf(comment.getLikesCount()));

        String reaction = comment.getUserReactionType();
        if ("LOVE".equalsIgnoreCase(reaction)) {
            holder.tvLikeComment.setText("❤️ J'adore");
            holder.tvLikeComment.setTextColor(Color.RED);
        } else if ("HAHA".equalsIgnoreCase(reaction)) {
            holder.tvLikeComment.setText("😆 Haha");
            holder.tvLikeComment.setTextColor(Color.parseColor("#FF9800"));
        } else if ("WOW".equalsIgnoreCase(reaction)) {
            holder.tvLikeComment.setText("😮 Wouah");
            holder.tvLikeComment.setTextColor(Color.parseColor("#2196F3"));
        } else if ("SAD".equalsIgnoreCase(reaction)) {
            holder.tvLikeComment.setText("😢 Triste");
            holder.tvLikeComment.setTextColor(Color.parseColor("#9C27B0"));
        } else if ("ANGRY".equalsIgnoreCase(reaction)) {
            holder.tvLikeComment.setText("😡 En colère");
            holder.tvLikeComment.setTextColor(Color.RED);
        } else if ("LIKE".equalsIgnoreCase(reaction) || comment.isLikedByCurrentUser()) {
            holder.tvLikeComment.setText("👍 J'aime");
            holder.tvLikeComment.setTextColor(Color.parseColor("#2196F3"));
        } else {
            holder.tvLikeComment.setText("🤍 J'aime");
            holder.tvLikeComment.setTextColor(Color.GRAY);
        }

        holder.tvLikeComment.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLikeClick(comment);
            }
        });

        holder.tvLikeComment.setOnLongClickListener(v -> {
            showReactionPopup(holder.itemView.getContext(), comment);
            return true;
        });

        holder.tvReplyComment.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReplyClick(comment);
            }
        });

        View.OnClickListener userClickListener = v -> {
            if (listener != null) {
                listener.onUserClick(comment);
            } else {
                Intent intent = new Intent(v.getContext(), PageUtilisateurActivity.class);
                intent.putExtra("user_id", comment.getUserId());
                v.getContext().startActivity(intent);
            }
        };

        holder.imgCommentAuthorAvatar.setOnClickListener(userClickListener);
        holder.tvCommentAuthorName.setOnClickListener(userClickListener);
        holder.tvCommentLikesCount.setOnClickListener(userClickListener);
    }

    private void showReactionPopup(android.content.Context context, Comment comment) {
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
                .setTitle("Réagir au commentaire")
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
                        listener.onReactionClick(comment, selectedType);
                    }
                })
                .show();
    }

    @Override
    public int getItemCount() {
        return commentList.size();
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

    public static class CommentViewHolder extends RecyclerView.ViewHolder {

        LinearLayout layoutCommentContainer;
        ImageView imgCommentAuthorAvatar;
        TextView tvCommentAuthorName;
        TextView tvCommentContent;
        TextView tvCommentTime;
        TextView tvReplyComment;
        TextView tvLikeComment;
        TextView tvCommentLikesCount;

        public CommentViewHolder(@NonNull View itemView) {
            super(itemView);

            layoutCommentContainer = itemView.findViewById(R.id.layoutCommentContainer);
            imgCommentAuthorAvatar = itemView.findViewById(R.id.imgCommentAuthorAvatar);
            tvCommentAuthorName = itemView.findViewById(R.id.tvCommentAuthorName);
            tvCommentContent = itemView.findViewById(R.id.tvCommentContent);
            tvCommentTime = itemView.findViewById(R.id.tvCommentTime);
            tvReplyComment = itemView.findViewById(R.id.tvReplyComment);
            tvLikeComment = itemView.findViewById(R.id.tvLikeComment);
            tvCommentLikesCount = itemView.findViewById(R.id.tvCommentLikesCount);
        }
    }
}

