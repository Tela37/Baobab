package td.teladoumbaobabtd;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.MessageViewHolder> {

    private static final int TYPE_SENT = 1;
    private static final int TYPE_RECEIVED = 2;

    private final List<MessageModel> messages;
    private final int currentUserId;
    private final OnMessageLongClickListener longClickListener;

    public interface OnMessageLongClickListener {
        void OnMessageLongClick(MessageModel message);
    }

    public ChatAdapter(List<MessageModel> messages, int currentUserId, OnMessageLongClickListener longClickListener) {
        this.messages = messages;
        this.currentUserId = currentUserId;
        this.longClickListener = longClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        MessageModel message = messages.get(position);
        if (message.getSenderId() == currentUserId) {
            return TYPE_SENT;
        } else {
            return TYPE_RECEIVED;
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_SENT) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_sent, parent, false);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_received, parent, false);
        }
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageModel message = messages.get(position);

        holder.txtMessage.setText(message.getMessage() != null ? message.getMessage() : "");

        if (holder.txtTime != null) {
            holder.txtTime.setText(message.getCreatedAt() != null ? message.getCreatedAt() : "");
        }

        if (holder.tvEdited != null) {
            holder.tvEdited.setVisibility(message.isEdited() ? View.VISIBLE : View.GONE);
        }

        if (holder.tvStarred != null) {
            holder.tvStarred.setVisibility(message.isStarred() ? View.VISIBLE : View.GONE);
        }

        if (holder.imgMessageImage != null) {
            if (message.getImagePath() != null && !message.getImagePath().isEmpty()) {
                holder.imgMessageImage.setVisibility(View.VISIBLE);
                ImageUtils.loadFullImage(holder.itemView.getContext(), message.getImagePath(), holder.imgMessageImage);
            } else {
                holder.imgMessageImage.setVisibility(View.GONE);
            }
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.OnMessageLongClick(message);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return messages != null ? messages.size() : 0;
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView txtMessage;
        TextView txtTime;
        TextView tvEdited;
        TextView tvStarred;
        ImageView imgMessageImage;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            txtMessage = itemView.findViewById(R.id.txtMessage);
            txtTime = itemView.findViewById(R.id.txtTime);
            tvEdited = itemView.findViewById(R.id.tvEdited);
            tvStarred = itemView.findViewById(R.id.tvStarred);
            imgMessageImage = itemView.findViewById(R.id.imgMessageImage);
        }
    }
}
