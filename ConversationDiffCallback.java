package td.teladoumbaobabtd;

import androidx.recyclerview.widget.DiffUtil;


import java.util.List;
import java.util.Objects;

public class ConversationDiffCallback
        extends DiffUtil.Callback {

    private final List<ConversationItem> oldList;
    private final List<ConversationItem> newList;

    public ConversationDiffCallback(
            List<ConversationItem> oldList,
            List<ConversationItem> newList) {

        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList.size();
    }

    @Override
    public boolean areItemsTheSame(
            int oldItemPosition,
            int newItemPosition) {

        return oldList.get(oldItemPosition)
                .getConversationId()
                ==
                newList.get(newItemPosition)
                        .getConversationId();
    }

    @Override
    public boolean areContentsTheSame(
            int oldItemPosition,
            int newItemPosition) {

        ConversationItem oldItem =
                oldList.get(oldItemPosition);

        ConversationItem newItem =
                newList.get(newItemPosition);

        return Objects.equals(
                oldItem.getUserName(),
                newItem.getUserName()
        )
                &&
                Objects.equals(
                        oldItem.getLastMessage(),
                        newItem.getLastMessage()
                )
                &&
                oldItem.getProfileImage()
                        ==
                        newItem.getProfileImage();
    }
}