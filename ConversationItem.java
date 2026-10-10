package td.teladoumbaobabtd;

/**
 * Modèle pour l'affichage d'un élément dans la liste des conversations (Inbox/Messages).
 * Regroupe les informations de l'interlocuteur, le dernier message, le nombre de messages non lus
 * et les drapeaux d'état (privée, épinglée).
 */
public class ConversationItem {

    private int conversationId;
    private int otherUserId;
    private String userName;
    private String lastMessage;
    private String profileImage;
    private int unreadCount;
    private boolean isPrivate;
    private boolean isPinned;

    public ConversationItem(int conversationId,
                            String userName,
                            String lastMessage,
                            String profileImage,
                            int unreadCount) {
        this(conversationId, -1, userName, lastMessage, profileImage, unreadCount, false, false);
    }

    public ConversationItem(int conversationId,
                            int otherUserId,
                            String userName,
                            String lastMessage,
                            String profileImage,
                            int unreadCount,
                            boolean isPrivate,
                            boolean isPinned) {
        this.conversationId = conversationId;
        this.otherUserId = otherUserId;
        this.userName = userName;
        this.lastMessage = lastMessage;
        this.profileImage = profileImage;
        this.unreadCount = unreadCount;
        this.isPrivate = isPrivate;
        this.isPinned = isPinned;
    }

    public int getConversationId() {
        return conversationId;
    }

    public int getOtherUserId() {
        return otherUserId;
    }

    public String getUserName() {
        return userName == null ? "Utilisateur" : userName;
    }

    public String getLastMessage() {
        return lastMessage == null ? "Aucun message" : lastMessage;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }
}
