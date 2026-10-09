package td.teladoumbaobabtd;

/**
 * Modèle représentant une notification d'interaction (commentaire, réaction sur un post ou une story).
 */
public class AppNotification {

    private int id;
    private int recipientId;
    private int senderId;
    private String senderName;
    private String senderAvatar;
    private String type; // "POST_LIKE", "POST_REACTION", "POST_COMMENT", "STORY_REACTION"
    private int targetId; // post_id ou story_id
    private String message;
    private boolean isRead;
    private String createdAt;

    public AppNotification() {
    }

    public AppNotification(int id, int recipientId, int senderId, String senderName, String senderAvatar, String type, int targetId, String message, boolean isRead, String createdAt) {
        this.id = id;
        this.recipientId = recipientId;
        this.senderId = senderId;
        this.senderName = senderName;
        this.senderAvatar = senderAvatar;
        this.type = type;
        this.targetId = targetId;
        this.message = message;
        this.isRead = isRead;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getRecipientId() {
        return recipientId;
    }

    public int getSenderId() {
        return senderId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderAvatar() {
        return senderAvatar;
    }

    public String getType() {
        return type;
    }

    public int getTargetId() {
        return targetId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRead() {
        return isRead;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setRecipientId(int recipientId) {
        this.recipientId = recipientId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public void setSenderAvatar(String senderAvatar) {
        this.senderAvatar = senderAvatar;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}