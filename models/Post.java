package td.teladoum.baobab.models;

import td.teladoum.baobab.R;

import td.teladoum.baobab.models.*;
import td.teladoum.baobab.adapters.*;
import td.teladoum.baobab.repositories.*;
import td.teladoum.baobab.services.*;
import td.teladoum.baobab.firebase.*;
import td.teladoum.baobab.helpers.*;
import td.teladoum.baobab.notifications.*;
import td.teladoum.baobab.utils.*;
import td.teladoum.baobab.activities.*;

import com.google.firebase.database.Exclude;

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
    private boolean isSensitive;
    private boolean isSavedByCurrentUser;
    private boolean isPinned;

    // Sondages (Polls) interactifs
    private String pollQuestion;
    private String pollOptions;
    private String pollVotes;
    private int userVotedOption = -1;

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

    public String getAuthor_name() {
        return authorName;
    }

    public String getAuthorProfileImage() {
        return authorProfileImage;
    }

    public String getAuthor_profile_image() {
        return authorProfileImage;
    }

    public String getContent() {
        return content;
    }

    public String getImagePath() {
        return imagePath;
    }

    public java.util.List<String> getImageList() {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (imagePath != null && !imagePath.trim().isEmpty()) {
            if (imagePath.contains("||")) {
                String[] parts = imagePath.split("\\|\\|");
                for (String p : parts) {
                    if (!p.trim().isEmpty()) list.add(p.trim());
                }
            } else if (imagePath.contains(",")) {
                String[] parts = imagePath.split(",");
                for (String p : parts) {
                    if (!p.trim().isEmpty()) list.add(p.trim());
                }
            } else {
                list.add(imagePath.trim());
            }
        }
        return list;
    }

    public String getVideoPath() {
        return videoPath;
    }

    public int getLikesCount() {
        return likesCount;
    }

    @Exclude
    public boolean isLikedByCurrentUser() {
        return isLikedByCurrentUser;
    }

    @Exclude
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

    public void setAuthor_name(String author_name) {
        if (this.authorName == null) this.authorName = author_name;
    }

    public void setAuthorProfileImage(String authorProfileImage) {
        this.authorProfileImage = authorProfileImage;
    }

    public void setAuthor_profile_image(String author_profile_image) {
        if (this.authorProfileImage == null) this.authorProfileImage = author_profile_image;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void setImage_path(String image_path) {
        if (this.imagePath == null) this.imagePath = image_path;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public void setVideo_path(String video_path) {
        if (this.videoPath == null) this.videoPath = video_path;
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

    public boolean isSensitive() {
        return isSensitive;
    }

    public void setSensitive(boolean sensitive) {
        isSensitive = sensitive;
    }

    @Exclude
    public boolean isSavedByCurrentUser() {
        return isSavedByCurrentUser;
    }

    public void setSavedByCurrentUser(boolean savedByCurrentUser) {
        isSavedByCurrentUser = savedByCurrentUser;
    }

    public boolean isPinned() {
        return isPinned;
    }

    public void setPinned(boolean pinned) {
        isPinned = pinned;
    }

    /**
     * Obtient la question du sondage associé à la publication.
     * @return La question du sondage ou null.
     */
    public String getPollQuestion() {
        return pollQuestion;
    }

    /**
     * Définit la question du sondage.
     * @param pollQuestion La question du sondage.
     */
    public void setPollQuestion(String pollQuestion) {
        this.pollQuestion = pollQuestion;
    }

    /**
     * Obtient la liste des options du sondage sous forme de chaîne séparée par des virgules.
     * @return Les options séparées par des virgules.
     */
    public String getPollOptions() {
        return pollOptions;
    }

    /**
     * Définit les options du sondage.
     * @param pollOptions Les options séparées par des virgules.
     */
    public void setPollOptions(String pollOptions) {
        this.pollOptions = pollOptions;
    }

    /**
     * Obtient le nombre de votes pour chaque option du sondage.
     * @return La chaîne représentant le décompte des votes.
     */
    public String getPollVotes() {
        return pollVotes;
    }

    /**
     * Définit la chaîne décompte des votes du sondage.
     * @param pollVotes La chaîne des votes.
     */
    public void setPollVotes(String pollVotes) {
        this.pollVotes = pollVotes;
    }

    /**
     * Obtient l'index de l'option choisie par l'utilisateur connecté (-1 si pas de vote).
     * @return L'index de l'option sélectionnée.
     */
    public int getUserVotedOption() {
        return userVotedOption;
    }

    /**
     * Définit l'index de l'option votée par l'utilisateur.
     * @param userVotedOption L'index de l'option.
     */
    public void setUserVotedOption(int userVotedOption) {
        this.userVotedOption = userVotedOption;
    }
}
