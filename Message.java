package td.teladoumbaobabtd;

/**
 * Modèle représentant un message échangé dans une discussion.
 * Contient le texte, l'image éventuelle, le statut de lecture/livraison,
 * les informations de réponse (Quote Reply) et la réaction émoji.
 */
public class Message {

    private int id;
    private int conversationId;
    private int senderId;

    private String message;
    private String imagePath;
    private String createdAt;

    private boolean isRead;
    private boolean isDelivered;

    // Réponse à un message spécifique (Quote Reply)
    private Integer replyToMessageId;
    private String replyToText;

    // Réaction émoji (ex: ❤️, 😂, 👍)
    private String reaction;

    public Message() {
    }

    public Message(int id,
                   int conversationId,
                   int senderId,
                   String message,
                   String createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Message(int id,
                   int conversationId,
                   int senderId,
                   String message,
                   String imagePath,
                   String createdAt,
                   boolean isRead,
                   boolean isDelivered,
                   Integer replyToMessageId,
                   String replyToText,
                   String reaction) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.message = message;
        this.imagePath = imagePath;
        this.createdAt = createdAt;
        this.isRead = isRead;
        this.isDelivered = isDelivered;
        this.replyToMessageId = replyToMessageId;
        this.replyToText = replyToText;
        this.reaction = reaction;
    }

    public int getId() {
        return id;
    }

    public int getConversationId() {
        return conversationId;
    }

    public int getSenderId() {
        return senderId;
    }

    public String getMessage() {
        return message;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    public Integer getReplyToMessageId() {
        return replyToMessageId;
    }

    public String getReplyToText() {
        return replyToText;
    }

    public String getReaction() {
        return reaction;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public void setDelivered(boolean delivered) {
        isDelivered = delivered;
    }

    public void setReplyToMessageId(Integer replyToMessageId) {
        this.replyToMessageId = replyToMessageId;
    }

    public void setReplyToText(String replyToText) {
        this.replyToText = replyToText;
    }

    public void setReaction(String reaction) {
        this.reaction = reaction;
    }
}