package td.teladoumbaobabtd;

/**
 * Modèle représentant une publication sur le fil d'actualité.
 * Contient l'auteur, le texte, l'image ou la vidéo éventuelle, les compteurs de j'aime, commentaires et partages.
 */
public class Post {

    private int id;
    private int userId;
    private String authorName;
    private String authorProfileImage;
    private String content;
    private String imagePath;
    private String videoPath;
    private int likesCount;
    private boolean isLikedByCurrentUser;
    private String userReactionType;
    private int commentsCount;
    private int sharesCount;
    private String createdAt;

    public Post() {
    }

    public Post(int id,
                int userId,
                String authorName,
                String authorProfileImage,
                String content,
                String imagePath,
                String videoPath,
                int likesCount,
                boolean isLikedByCurrentUser,
                String createdAt) {
        this.id = id;
        this.userId = userId;
        this.authorName = authorName;
        this.authorProfileImage = authorProfileImage;
        this.content = content;
        this.imagePath = imagePath;
        this.videoPath = videoPath;
        this.likesCount = likesCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorProfileImage() {
        return authorProfileImage;
    }

    public String getContent() {
        return content;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public boolean isLikedByCurrentUser() {
        return isLikedByCurrentUser;
    }

    public String getUserReactionType() {
        return userReactionType;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public int getSharesCount() {
        return sharesCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public void setAuthorProfileImage(String authorProfileImage) {
        this.authorProfileImage = authorProfileImage;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        isLikedByCurrentUser = likedByCurrentUser;
    }

    public void setUserReactionType(String userReactionType) {
        this.userReactionType = userReactionType;
    }

    public void setCommentsCount(int commentsCount) {
        this.commentsCount = commentsCount;
    }

    public void setSharesCount(int sharesCount) {
        this.sharesCount = sharesCount;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}