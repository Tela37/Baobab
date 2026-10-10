package td.teladoumbaobabtd;

/**
 * Modèle représentant un commentaire ou une réponse à un commentaire sur une publication.
 * Prend en charge la hiérarchie parent-enfant (parentId) pour les réponses ciblées.
 */
public class Comment {

    private int id;
    private int postId;
    private Integer parentId;
    private int userId;
    private String authorName;
    private String authorProfileImage;
    private String content;
    private int likesCount;
    private boolean isLikedByCurrentUser;
    private String createdAt;

    public Comment() {
    }

    public Comment(int id,
                   int postId,
                   Integer parentId,
                   int userId,
                   String authorName,
                   String authorProfileImage,
                   String content,
                   int likesCount,
                   boolean isLikedByCurrentUser,
                   String createdAt) {
        this.id = id;
        this.postId = postId;
        this.parentId = parentId;
        this.userId = userId;
        this.authorName = authorName;
        this.authorProfileImage = authorProfileImage;
        this.content = content;
        this.likesCount = likesCount;
        this.isLikedByCurrentUser = isLikedByCurrentUser;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getPostId() {
        return postId;
    }

    public Integer getParentId() {
        return parentId;
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

    public int getLikesCount() {
        return likesCount;
    }

    public boolean isLikedByCurrentUser() {
        return isLikedByCurrentUser;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setPostId(int postId) {
        this.postId = postId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
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

    public void setLikesCount(int likesCount) {
        this.likesCount = likesCount;
    }

    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        isLikedByCurrentUser = likedByCurrentUser;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
