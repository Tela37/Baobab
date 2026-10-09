package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.Post;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable des opérations sur les publications (posts),
 * leurs mentions j'aime, réactions émojis et notifications d'interactions.
 */
public class PostRepository {

    private final DatabaseHelper dbHelper;
    private final NotificationRepository notificationRepository;

    public PostRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.notificationRepository = new NotificationRepository(context);
    }

    public boolean createPost(int userId, String content, String imagePath) {
        return createPost(userId, content, imagePath, null);
    }

    public boolean createPost(int userId, String content, String imagePath, String videoPath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("content", content);
        values.put("image_path", imagePath);
        values.put("video_path", videoPath);

        long id = db.insert("posts", null, values);
        return id != -1;
    }

    public List<Post> getAllPosts(int currentUserId) {
        List<Post> posts = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT p.id, p.user_id, p.content, p.image_path, p.video_path, p.created_at, " +
                "u.name AS author_name, u.profile_image AS author_profile_image, " +
                "(SELECT COUNT(*) FROM post_likes WHERE post_id = p.id) AS likes_count, " +
                "(SELECT COUNT(*) FROM post_likes WHERE post_id = p.id AND user_id = ?) AS is_liked, " +
                "(SELECT reaction_type FROM post_reactions WHERE post_id = p.id AND user_id = ?) AS user_reaction, " +
                "(SELECT COUNT(*) FROM comments WHERE post_id = p.id) AS comments_count, " +
                "(SELECT COUNT(*) FROM post_shares WHERE post_id = p.id) AS shares_count " +
                "FROM posts p " +
                "JOIN users u ON p.user_id = u.id " +
                "ORDER BY p.created_at DESC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{String.valueOf(currentUserId), String.valueOf(currentUserId)});
            while (cursor.moveToNext()) {
                Post post = new Post();
                post.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                post.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow("user_id")));
                post.setAuthorName(cursor.getString(cursor.getColumnIndexOrThrow("author_name")));
                post.setAuthorProfileImage(cursor.getString(cursor.getColumnIndexOrThrow("author_profile_image")));
                post.setContent(cursor.getString(cursor.getColumnIndexOrThrow("content")));
                post.setImagePath(cursor.getString(cursor.getColumnIndexOrThrow("image_path")));

                int videoColIndex = cursor.getColumnIndex("video_path");
                if (videoColIndex != -1) {
                    post.setVideoPath(cursor.getString(videoColIndex));
                }

                post.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
                post.setLikesCount(cursor.getInt(cursor.getColumnIndexOrThrow("likes_count")));
                post.setLikedByCurrentUser(cursor.getInt(cursor.getColumnIndexOrThrow("is_liked")) > 0);
                post.setUserReactionType(cursor.getString(cursor.getColumnIndexOrThrow("user_reaction")));
                post.setCommentsCount(cursor.getInt(cursor.getColumnIndexOrThrow("comments_count")));
                post.setSharesCount(cursor.getInt(cursor.getColumnIndexOrThrow("shares_count")));

                posts.add(post);
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return posts;
    }

    public boolean updatePost(int postId, int userId, String newText, String imagePath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("content", newText);
        if (imagePath != null) {
            values.put("image_path", imagePath);
        }

        int rows = db.update("posts", values, "id = ? AND user_id = ?", new String[]{String.valueOf(postId), String.valueOf(userId)});
        return rows > 0;
    }

    public boolean deletePost(int postId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("posts", "id = ? AND user_id = ?", new String[]{String.valueOf(postId), String.valueOf(userId)});
        return rows > 0;
    }

    public boolean toggleLikePost(int postId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT 1 FROM post_likes WHERE post_id = ? AND user_id = ?",
                    new String[]{String.valueOf(postId), String.valueOf(userId)});
            if (cursor.moveToFirst()) {
                db.delete("post_likes", "post_id = ? AND user_id = ?",
                        new String[]{String.valueOf(postId), String.valueOf(userId)});
                return false;
            } else {
                ContentValues values = new ContentValues();
                values.put("post_id", postId);
                values.put("user_id", userId);
                db.insert("post_likes", null, values);

                // Notification pour le propriétaire du post (si ce n'est pas lui-même)
                int ownerId = getPostOwnerId(postId);
                if (ownerId != -1 && ownerId != userId) {
                    String senderName = getUserName(userId);
                    notificationRepository.addNotification(ownerId, userId, "POST_LIKE", postId, senderName + " a aimé votre publication.");
                }

                return true;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public void setPostReaction(int postId, int userId, String reactionType) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (reactionType == null) {
            // Suppression de la réaction lors du choix "Retirer ma réaction"
            db.delete("post_reactions", "post_id = ? AND user_id = ?", new String[]{String.valueOf(postId), String.valueOf(userId)});
            return;
        }

        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("user_id", userId);
        values.put("reaction_type", reactionType);

        db.insertWithOnConflict("post_reactions", null, values, SQLiteDatabase.CONFLICT_REPLACE);

        // Notification pour le propriétaire du post (si ce n'est pas lui-même)
        int ownerId = getPostOwnerId(postId);
        if (ownerId != -1 && ownerId != userId) {
            String senderName = getUserName(userId);
            notificationRepository.addNotification(ownerId, userId, "POST_REACTION", postId, senderName + " a réagi " + reactionType + " à votre publication.");
        }
    }

    public void recordPostShare(int postId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("user_id", userId);

        db.insert("post_shares", null, values);
    }

    private int getPostOwnerId(int postId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT user_id FROM posts WHERE id = ?", new String[]{String.valueOf(postId)});
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