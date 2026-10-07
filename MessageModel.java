package td.teladoumbaobabtd;

public class MessageModel {

    private int id;
    private int conversationId;
    private int senderId;
    private String message;
    private String imagePath;
    private String audioPath;
    private String pdfPath;
    private boolean isRead;
    private boolean isDelivered;
    private boolean isEdited;
    private boolean isStarred;
    private String readAt;
    private String createdAt;

    public MessageModel() {
    }

    public MessageModel(int id, int conversationId, int senderId, String message, String createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public MessageModel(int id, int conversationId, int senderId, String message,
                        String imagePath, String audioPath, String pdfPath,
                        boolean isRead, boolean isDelivered, boolean isEdited,
                        boolean isStarred, String readAt, String createdAt) {
        this.id = id;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.message = message;
        this.imagePath = imagePath;
        this.audioPath = audioPath;
        this.pdfPath = pdfPath;
        this.isRead = isRead;
        this.isDelivered = isDelivered;
        this.isEdited = isEdited;
        this.isStarred = isStarred;
        this.readAt = readAt;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getConversationId() {
        return conversationId;
    }

    public void setConversationId(int conversationId) {
        this.conversationId = conversationId;
    }

    public int getSenderId() {
        return senderId;
    }

    public void setSenderId(int senderId) {
        this.senderId = senderId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getAudioPath() {
        return audioPath;
    }

    public void setAudioPath(String audioPath) {
        this.audioPath = audioPath;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public void setPdfPath(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public boolean isDelivered() {
        return isDelivered;
    }

    public void setDelivered(boolean delivered) {
        isDelivered = delivered;
    }

    public boolean isEdited() {
        return isEdited;
    }

    public void setEdited(boolean edited) {
        isEdited = edited;
    }

    public boolean isStarred() {
        return isStarred;
    }

    public void setStarred(boolean starred) {
        isStarred = starred;
    }

    public String getReadAt() {
        return readAt;
    }

    public void setReadAt(String readAt) {
        this.readAt = readAt;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
