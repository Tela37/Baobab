package td.teladoumbaobabtd;

public class ConversationItem {

    private int conversationId;
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
        this(conversationId, userName, lastMessage, profileImage, unreadCount, false, false);
    }

    public ConversationItem(int conversationId,
                            String userName,
                            String lastMessage,
                            String profileImage,
                            int unreadCount,
                            boolean isPrivate,
                            boolean isPinned) {
        this.conversationId = conversationId;
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
