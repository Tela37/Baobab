package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.Story;
import td.teladoumbaobabtd.UserStoryGroup;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable de la gestion des stories éphémères (24h),
 * de leur regroupement par auteur et des notifications de réactions.
 */
public class StoryRepository {

    private final DatabaseHelper dbHelper;
    private final NotificationRepository notificationRepository;

    public StoryRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.notificationRepository = new NotificationRepository(context);
    }

    public boolean createStory(int userId, String imagePath, String caption) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("image_path", imagePath);
        values.put("caption", caption != null ? caption.trim() : "");

        long id = db.insert("stories", null, values);
        return id != -1;
    }

    /**
     * Récupère toutes les stories publiées dans les dernières 24 heures.
     */
    public List<Story> getRecentStories() {
        List<Story> stories = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT s.id, s.user_id, u.name, u.profile_image, s.image_path, s.caption, s.created_at " +
                            "FROM stories s " +
                            "JOIN users u ON s.user_id = u.id " +
                            "WHERE s.created_at >= datetime('now', '-1 day') " +
                            "ORDER BY s.id DESC",
                    null
            );

            while (cursor.moveToNext()) {
                Story story = new Story(
                        cursor.getInt(0),
                        cursor.getInt(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.getString(6)
                );
                stories.add(story);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return stories;
    }

    /**
     * Récupère les stories des dernières 24h regroupées par utilisateur.
     */
    public List<UserStoryGroup> getGroupedRecentStories() {
        List<Story> allStories = getRecentStories();
        List<UserStoryGroup> groups = new ArrayList<>();

        for (Story story : allStories) {
            UserStoryGroup existingGroup = null;
            for (UserStoryGroup group : groups) {
                if (group.getUserId() == story.getUserId()) {
                    existingGroup = group;
                    break;
                }
            }

            if (existingGroup != null) {
                existingGroup.getStories().add(story);
            } else {
                List<Story> userStories = new ArrayList<>();
                userStories.add(story);
                UserStoryGroup newGroup = new UserStoryGroup(
                        story.getUserId(),
                        story.getUserName(),
                        story.getUserAvatar(),
                        userStories
                );
                groups.add(newGroup);
            }
        }

        return groups;
    }

    /**
     * Enregistre ou met à jour la réaction émoji d'un utilisateur sur une story.
     */
    public boolean setStoryReaction(int storyId, int userId, String reactionType) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("story_id", storyId);
        values.put("user_id", userId);
        values.put("reaction_type", reactionType);

        long id = db.insertWithOnConflict("story_reactions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        if (id != -1) {
            // Notification pour l'auteur de la story (si ce n'est pas lui-même)
            int ownerId = getStoryOwnerId(storyId);
            if (ownerId != -1 && ownerId != userId) {
                String senderName = getUserName(userId);
                notificationRepository.addNotification(
                        ownerId,
                        userId,
                        "STORY_REACTION",
                        storyId,
                        senderName + " a réagi " + reactionType + " à votre story."
                );
            }
            return true;
        }
        return false;
    }

    /**
     * Récupère la réaction actuelle de l'utilisateur sur la story.
     */
    public String getStoryReaction(int storyId, int userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT reaction_type FROM story_reactions WHERE story_id=? AND user_id=?",
                    new String[]{String.valueOf(storyId), String.valueOf(userId)}
            );
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private int getStoryOwnerId(int storyId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT user_id FROM stories WHERE id = ?", new String[]{String.valueOf(storyId)});
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return -1;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private String getUserName(int userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT name FROM users WHERE id = ?", new String[]{String.valueOf(userId)});
            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }
            return "Un utilisateur";
        } finally {
            if (cursor != null) cursor.close();
        }
    }
}