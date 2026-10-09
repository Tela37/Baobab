package td.teladoumbaobabtd;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adaptateur RecyclerView pour l'affichage de la liste des conversations/discussions.
 * Prend en charge la mise à jour fluide (DiffUtil), le mode de sélection multiple,
 * les badges de messages non lus et les indicateurs de mode privé/épinglé (📌 🔒).
 */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    private final List<ConversationItem> conversationList;
    private final OnConversationClickListener listener;
    private OnConversationLongClickListener longClickListener;
    private OnSelectionChangedListener selectionChangedListener;

    private boolean selectionMode = false;
    private final Set<Integer> selectedIds = new HashSet<>();

    public ConversationAdapter(
            List<ConversationItem> conversationList,
            OnConversationClickListener listener) {
        this.conversationList = new ArrayList<>(conversationList);
        this.listener = listener;
    }

    public ConversationAdapter(
            List<ConversationItem> conversationList,
            OnConversationClickListener listener,
            OnConversationLongClickListener longClickListener) {
        this.conversationList = new ArrayList<>(conversationList);
        this.listener = listener;
        this.longClickListener = longClickListener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= conversationList.size()) {
            return;
        }

        ConversationItem conversation = conversationList.get(position);

        String userName = conversation.getUserName() == null ? "Utilisateur" : conversation.getUserName();
        String lastMessage = conversation.getLastMessage() == null ? "Aucun message" : conversation.getLastMessage();

        holder.tvUserName.setText(userName);
        holder.tvLastMessage.setText(lastMessage);

        ImageUtils.loadProfileImage(
                holder.itemView.getContext(),
                conversation.getProfileImage(),
                holder.imgProfile
        );

        if (holder.tvPinStatus != null) {
            holder.tvPinStatus.setVisibility(conversation.isPinned() ? View.VISIBLE : View.GONE);
        }

        if (holder.tvLockStatus != null) {
            holder.tvLockStatus.setVisibility(conversation.isPrivate() ? View.VISIBLE : View.GONE);
        }

        if (selectionMode) {
            if (holder.cbSelect != null) {
                holder.cbSelect.setVisibility(View.VISIBLE);
                boolean isSelected = selectedIds.contains(conversation.getConversationId());
                holder.cbSelect.setChecked(isSelected);
            }
            boolean isSelected = selectedIds.contains(conversation.getConversationId());
            holder.itemView.setBackgroundColor(isSelected ? Color.parseColor("#E3F2FD") : Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> toggleSelection(conversation.getConversationId()));
            holder.itemView.setOnLongClickListener(v -> {
                toggleSelection(conversation.getConversationId());
                return true;
            });
        } else {
            if (holder.cbSelect != null) {
                holder.cbSelect.setVisibility(View.GONE);
            }
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);

            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(conversation);
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onConversationLongClick(conversation);
                    return true;
                }
                return false;
            });
        }

        if (conversation.getUnreadCount() > 0) {
            holder.tvUnreadCount.setVisibility(View.VISIBLE);
            holder.tvUnreadCount.setText(String.valueOf(conversation.getUnreadCount()));
        } else {
            holder.tvUnreadCount.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return conversationList.size();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean active) {
        this.selectionMode = active;
        if (!active) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedIds.size());
        }
    }

    public void toggleSelection(int conversationId) {
        if (selectedIds.contains(conversationId)) {
            selectedIds.remove(conversationId);
        } else {
            selectedIds.add(conversationId);
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedIds.size());
        }
    }

    public void selectAll() {
        selectedIds.clear();
        for (ConversationItem item : conversationList) {
            selectedIds.add(item.getConversationId());
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedIds.size());
        }
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedIds.size());
        }
    }

    public Set<Integer> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    public void updateData(List<ConversationItem> newList) {
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new ConversationDiffCallback(conversationList, newList)
        );

        conversationList.clear();
        conversationList.addAll(newList);
        diffResult.dispatchUpdatesTo(this);
    }

    public void addConversation(ConversationItem conversation) {
        conversationList.add(0, conversation);
        notifyItemInserted(0);
    }

    public void removeConversation(int position) {
        if (position >= 0 && position < conversationList.size()) {
            conversationList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public ConversationItem getConversation(int position) {
        return conversationList.get(position);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgProfile;
        final TextView tvUserName;
        final TextView tvLastMessage;
        TextView tvUnreadCount;
        TextView tvPinStatus;
        TextView tvLockStatus;
        CheckBox cbSelect;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgProfile = itemView.findViewById(R.id.imgProfile);
            tvUserName = itemView.findViewById(R.id.tvUserName);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
            tvUnreadCount = itemView.findViewById(R.id.tvUnreadCount);
            tvPinStatus = itemView.findViewById(R.id.tvPinStatus);
            tvLockStatus = itemView.findViewById(R.id.tvLockStatus);
            cbSelect = itemView.findViewById(R.id.cbSelect);
        }
    }

    public interface OnConversationClickListener {
        void onConversationClick(ConversationItem conversation);
    }

    public interface OnConversationLongClickListener {
        void onConversationLongClick(ConversationItem conversation);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count);
    }
}
