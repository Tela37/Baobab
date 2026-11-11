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

/**
 * Modèle représentant une story éphémère (publiée pour 24 heures).
 */
public class Story {

    private int id;
    private int userId;
    private String userName;
    private String userAvatar;
    private String imagePath;
    private String caption;
    private String createdAt;

    // Musique de fond
    private String musicTitle;
    private String musicUrl;

    public Story() {
    }

    public Story(int id, int userId, String userName, String userAvatar, String imagePath, String caption, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.imagePath = imagePath;
        this.caption = caption;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserAvatar() {
        return userAvatar;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getCaption() {
        return caption;
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

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setUserAvatar(String userAvatar) {
        this.userAvatar = userAvatar;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Obtient le titre de la musique de fond associée à la story.
     * @return Le titre de la musique.
     */
    public String getMusicTitle() {
        return musicTitle;
    }

    /**
     * Définit le titre de la musique de fond.
     * @param musicTitle Le titre de la musique.
     */
    public void setMusicTitle(String musicTitle) {
        this.musicTitle = musicTitle;
    }

    /**
     * Obtient l'URL ou le chemin audio de la musique de fond.
     * @return L'URL ou le chemin audio.
     */
    public String getMusicUrl() {
        return musicUrl;
    }

    /**
     * Définit l'URL ou le chemin audio de la musique de fond.
     * @param musicUrl L'URL audio.
     */
    public void setMusicUrl(String musicUrl) {
        this.musicUrl = musicUrl;
    }
}
