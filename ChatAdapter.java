package td.teladoumbaobabtd;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adaptateur RecyclerView pour l'affichage des messages dans le Chat.
 * Gère les deux types de vues : messages envoyés (droite) et reçus (gauche),
 * les statuts de lecture (✓/✓✓ bleu), les réponses ciblées (Quote Reply) et les réactions émojis.
 */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;

    private final List<Message> messageList;
    private final int currentUserId;
    private OnMessageLongClickListener longClickListener;
    private OnImageClickListener imageClickListener;

    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message);
    }

    public interface OnImageClickListener {
        void onImageClick(String imagePath);
    }

    public ChatAdapter(List<Message> messageList,
                       int currentUserId) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
    }

    public ChatAdapter(List<Message> messageList,
                       int currentUserId,
                       OnMessageLongClickListener longClickListener) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.longClickListener = longClickListener;
    }

    public ChatAdapter(List<Message> messageList,
                       int currentUserId,
                       OnMessageLongClickListener longClickListener,
                       OnImageClickListener imageClickListener) {
        this.messageList = messageList;
        this.currentUserId = currentUserId;
        this.longClickListener = longClickListener;
        this.imageClickListener = imageClickListener;
    }

    public void setOnMessageLongClickListener(OnMessageLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        Message message = messageList.get(position);
        if (message.getSenderId() == currentUserId) {
            return VIEW_TYPE_SENT;
        }
        return VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_SENT) {
            View view = inflater.inflate(
                    R.layout.item_message_sent,
                    parent,
                    false
            );
            return new SentViewHolder(view);
        } else {
            View view = inflater.inflate(
                    R.layout.item_message_received,
                    parent,
                    false
            );
            return new ReceivedViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(
            @NonNull RecyclerView.ViewHolder holder,
            int position) {

        Message message = messageList.get(position);
        String time = formatTime(message.getCreatedAt());

        if (holder instanceof SentViewHolder) {
            SentViewHolder viewHolder = (SentViewHolder) holder;

            bindMessageContent(viewHolder.txtMessage, viewHolder.imgMessageImage, message);
            bindExtraViews(viewHolder.txtReplyQuote, viewHolder.txtReactionBadge, message);

            viewHolder.txtTime.setText(time);

            // Statut du message envoyé : ✓ (envoyé), ✓✓ gris (distribué), ✓✓ bleu (lu)
            if (message.isRead()) {
                viewHolder.txtStatus.setText("✓✓");
                viewHolder.txtStatus.setTextColor(Color.parseColor("#2196F3"));
            } else if (message.isDelivered()) {
                viewHolder.txtStatus.setText("✓✓");
                viewHolder.txtStatus.setTextColor(Color.LTGRAY);
            } else {
                viewHolder.txtStatus.setText("✓");
                viewHolder.txtStatus.setTextColor(Color.WHITE);
            }

        } else if (holder instanceof ReceivedViewHolder) {
            ReceivedViewHolder viewHolder = (ReceivedViewHolder) holder;

            bindMessageContent(viewHolder.txtMessage, viewHolder.imgMessageImage, message);
            bindExtraViews(viewHolder.txtReplyQuote, viewHolder.txtReactionBadge, message);

            viewHolder.txtTime.setText(time);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message);
                return true;
            }
            return false;
        });
    }

    private void bindMessageContent(TextView txtMessage, ImageView imgMessageImage, Message message) {
        if (message.getImagePath() != null && !message.getImagePath().trim().isEmpty()) {
            imgMessageImage.setVisibility(View.VISIBLE);
            ImageUtils.loadProfileImage(imgMessageImage.getContext(), message.getImagePath(), imgMessageImage);
            imgMessageImage.setOnClickListener(v -> {
                if (imageClickListener != null) {
                    imageClickListener.onImageClick(message.getImagePath());
                }
            });
        } else {
            imgMessageImage.setVisibility(View.GONE);
            imgMessageImage.setOnClickListener(null);
        }

        if (message.getMessage() != null && !message.getMessage().trim().isEmpty()) {
            txtMessage.setVisibility(View.VISIBLE);
            txtMessage.setText(message.getMessage());
        } else {
            txtMessage.setVisibility(View.GONE);
        }
    }

    private void bindExtraViews(TextView txtReplyQuote, TextView txtReactionBadge, Message message) {
        if (txtReplyQuote != null) {
            if (message.getReplyToText() != null && !message.getReplyToText().trim().isEmpty()) {
                txtReplyQuote.setVisibility(View.VISIBLE);
                txtReplyQuote.setText("↩ " + message.getReplyToText());
            } else {
                txtReplyQuote.setVisibility(View.GONE);
            }
        }

        if (txtReactionBadge != null) {
            if (message.getReaction() != null && !message.getReaction().trim().isEmpty()) {
                txtReactionBadge.setVisibility(View.VISIBLE);
                txtReactionBadge.setText(message.getReaction());
            } else {
                txtReactionBadge.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    private String formatTime(String dateTime) {
        try {
            if (dateTime == null || dateTime.trim().isEmpty()) {
                return "--:--";
            }

            SimpleDateFormat inputFormat = new SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
            );

            Date date = inputFormat.parse(dateTime);
            if (date == null) {
                return "--:--";
            }

            SimpleDateFormat outputFormat = new SimpleDateFormat(
                    "HH:mm",
                    Locale.getDefault()
            );

            return outputFormat.format(date);

        } catch (Exception e) {
            return "--:--";
        }
    }

    public static class SentViewHolder extends RecyclerView.ViewHolder {

        TextView txtMessage;
        ImageView imgMessageImage;
        TextView txtTime;
        TextView txtStatus;
        TextView txtReplyQuote;
        TextView txtReactionBadge;

        public SentViewHolder(@NonNull View itemView) {
            super(itemView);

            txtMessage = itemView.findViewById(R.id.txtMessage);
            imgMessageImage = itemView.findViewById(R.id.imgMessageImage);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtReplyQuote = itemView.findViewById(R.id.txtReplyQuote);
            txtReactionBadge = itemView.findViewById(R.id.txtReactionBadge);
        }
    }

    public static class ReceivedViewHolder extends RecyclerView.ViewHolder {

        TextView txtMessage;
        ImageView imgMessageImage;
        TextView txtTime;
        TextView txtReplyQuote;
        TextView txtReactionBadge;

        public ReceivedViewHolder(@NonNull View itemView) {
            super(itemView);

            txtMessage = itemView.findViewById(R.id.txtMessage);
            imgMessageImage = itemView.findViewById(R.id.imgMessageImage);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtReplyQuote = itemView.findViewById(R.id.txtReplyQuote);
            txtReactionBadge = itemView.findViewById(R.id.txtReactionBadge);
        }
    }
}