package td.teladoumbaobabtd;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
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

        if (comment.isLikedByCurrentUser()) {
            holder.tvLikeComment.setText("❤️ Aimé");
            holder.tvLikeComment.setTextColor(Color.RED);
        } else {
            holder.tvLikeComment.setText("🤍 J'aime");
            holder.tvLikeComment.setTextColor(Color.GRAY);
        }

        holder.tvLikeComment.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLikeClick(comment);
            }
        });

        holder.tvReplyComment.setOnClickListener(v -> {
            if (listener != null) {
                listener.onReplyClick(comment);
            }
        });
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
