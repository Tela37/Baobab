package td.teladoumbaobabtd;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;


import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter
        extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private final List<ConversationItem> conversationList;

    private final OnConversationClickListener listener;
    private OnConversationLongClickListener longClickListener;

    public ConversationAdapter(
            List<ConversationItem> conversationList,
            OnConversationClickListener listener) {

        this.conversationList =
                new ArrayList<>(conversationList);

        this.listener = listener;
    }

    public ConversationAdapter(
            List<ConversationItem> conversationList,
            OnConversationClickListener listener,
            OnConversationLongClickListener longClickListener) {

        this.conversationList =
                new ArrayList<>(conversationList);

        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(
                                R.layout.item_conversation,
                                parent,
                                false
                        );

        return new ViewHolder(view);
    }


    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position) {

        if (position < 0 ||
                position >= conversationList.size()) {
            return;
        }

        ConversationItem conversation =
                conversationList.get(position);

        String userName = conversation.getUserName() == null
                        ? "Utilisateur"
                        : conversation.getUserName();

        String lastMessage = conversation.getLastMessage() == null
                        ? "Aucun message"
                        : conversation.getLastMessage();

        holder.tvUserName.setText(userName);

        holder.tvLastMessage.setText(lastMessage);

        ImageUtils.loadProfileImage(
                holder.itemView.getContext(),
                conversation.getProfileImage(),
                holder.imgProfile
        );

        holder.itemView.setOnClickListener(v -> {

            if (listener != null) {

                listener.onConversationClick(
                        conversation
                );
            }
        });

        holder.itemView.setOnLongClickListener(v -> {

            if (longClickListener != null) {

                longClickListener.onConversationLongClick(
                        conversation
                );
                return true;
            }
            return false;
        });

        // message non lu
        if (conversation.getUnreadCount() > 0) {

            holder.tvUnreadCount.setVisibility(
                    View.VISIBLE
            );

            holder.tvUnreadCount.setText(
                    String.valueOf(
                            conversation.getUnreadCount()
                    )
            );

        } else {

            holder.tvUnreadCount.setVisibility(
                    View.GONE
            );
        }
    }

    @Override
    public int getItemCount() {

        return conversationList.size();
    }

    /**
     * Mise à jour via DiffUtil
     */
    public void updateData(
            List<ConversationItem> newList) {

        DiffUtil.DiffResult diffResult =
                DiffUtil.calculateDiff(
                        new ConversationDiffCallback(
                                conversationList,
                                newList
                        )
                );

        conversationList.clear();

        conversationList.addAll(newList);

        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * Ajout d'une conversation
     */
    public void addConversation(
            ConversationItem conversation) {

        conversationList.add(0, conversation);

        notifyItemInserted(0);
    }

    /**
     * Suppression d'une conversation
     */
    public void removeConversation(
            int position) {

        if (position >= 0 &&
                position < conversationList.size()) {

            conversationList.remove(position);

            notifyItemRemoved(position);
        }
    }

    /**
     * Retourne une conversation
     */
    public ConversationItem getConversation(
            int position) {

        return conversationList.get(position);
    }

    // =====================================
    // VIEW HOLDER
    // =====================================

    public static class ViewHolder
            extends RecyclerView.ViewHolder {

        final ImageView imgProfile;
        final TextView tvUserName;
        final TextView tvLastMessage;
        TextView tvUnreadCount;

        public ViewHolder(
                @NonNull View itemView) {

            super(itemView);

            imgProfile = itemView.findViewById(R.id.imgProfile);

            tvUserName = itemView.findViewById(R.id.tvUserName);

            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
        }
    }

    // =====================================
    // LISTENER
    // =====================================

    public interface OnConversationClickListener {

        void onConversationClick(
                ConversationItem conversation
        );
    }

    public interface OnConversationLongClickListener {

        void onConversationLongClick(
                ConversationItem conversation
        );
    }
}