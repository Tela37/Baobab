package td.teladoumbaobabtd;

import java.util.ArrayList;
import java.util.List;

/**
 * Modèle regroupant l'ensemble des stories publiées par un même utilisateur dans les dernières 24 heures.
 * Permet l'affichage sous forme d'une carte unique par auteur et la lecture séquentielle des stories.
 */
public class UserStoryGroup {

    private int userId;
    private String userName;
    private String userAvatar;
    private List<Story> stories;

    public UserStoryGroup() {
        this.stories = new ArrayList<>();
    }

    public UserStoryGroup(int userId, String userName, String userAvatar, List<Story> stories) {
        this.userId = userId;
        this.userName = userName;
        this.userAvatar = userAvatar;
        this.stories = stories != null ? stories : new ArrayList<>();
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

    public List<Story> getStories() {
        return stories;
    }

    public int getStoriesCount() {
        return stories != null ? stories.size() : 0;
    }

    /**
     * Retourne le chemin de l'image de couverture (la plus récente).
     */
    public String getCoverImage() {
        if (stories != null && !stories.isEmpty()) {
            return stories.get(0).getImagePath();
        }
        return userAvatar;
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

    public void setStories(List<Story> stories) {
        this.stories = stories;
    }
}