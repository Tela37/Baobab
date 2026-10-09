package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.Comment;
import td.teladoumbaobabtd.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable des commentaires sur les publications et de l'envoi de notifications associées.
 */
public class CommentRepository {

    private final DatabaseHelper dbHelper;
    private final NotificationRepository notificationRepository;

    public CommentRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.notificationRepository = new NotificationRepository(context);
    }

    public List<Comment> getCommentsForPost(int postId, int currentUserId) {
        List<Comment> comments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT c.id, c.post_id, c.parent_id, c.user_id, c.content, c.created_at, " +
                "u.name AS author_name, u.profile_image AS author_profile_image, " +
                "(SELECT COUNT(*) FROM comment_likes WHERE comment_id = c.id) AS likes_count, " +
                "(SELECT COUNT(*) FROM comment_likes WHERE comment_id = c.id AND user_id = ?) AS is_liked " +
                "FROM comments c " +
                "JOIN users u ON c.user_id = u.id " +
                "WHERE c.post_id = ? " +
                "ORDER BY c.created_at ASC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{String.valueOf(currentUserId), String.valueOf(postId)});
            while (cursor.moveToNext()) {
                Comment comment = new Comment();
                comment.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                comment.setPostId(cursor.getInt(cursor.getColumnIndexOrThrow("post_id")));
                int parentIdIndex = cursor.getColumnIndexOrThrow("parent_id");
                if (cursor.isNull(parentIdIndex)) {
                    comment.setParentId(null);
                } else {
                    comment.setParentId(cursor.getInt(parentIdIndex));
                }
                comment.setUserId(cursor.getInt(cursor.getColumnIndexOrThrow("user_id")));
                comment.setAuthorName(cursor.getString(cursor.getColumnIndexOrThrow("author_name")));
                comment.setAuthorProfileImage(cursor.getString(cursor.getColumnIndexOrThrow("author_profile_image")));
                comment.setContent(cursor.getString(cursor.getColumnIndexOrThrow("content")));
                comment.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
                comment.setLikesCount(cursor.getInt(cursor.getColumnIndexOrThrow("likes_count")));
                comment.setLikedByCurrentUser(cursor.getInt(cursor.getColumnIndexOrThrow("is_liked")) > 0);

                comments.add(comment);
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return comments;
    }

    public boolean addComment(int postId, Integer parentId, int currentUserId, String content) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        if (parentId != null) {
            values.put("parent_id", parentId);
        } else {
            values.putNull("parent_id");
        }
        values.put("user_id", currentUserId);
        values.put("content", content);

        long id = db.insert("comments", null, values);
        if (id != -1) {
            // Notification pour le propriétaire du post (si ce n'est pas lui-même)
            int ownerId = getPostOwnerId(postId);
            if (ownerId != -1 && ownerId != currentUserId) {
                String senderName = getUserName(currentUserId);
                String snippet = content.length() > 35 ? content.substring(0, 35) + "..." : content;
                notificationRepository.addNotification(
                        ownerId,
                        currentUserId,
                        "POST_COMMENT",
                        postId,
                        senderName + " a commenté votre publication : \"" + snippet + "\""
                );
            }
            return true;
        }
        return false;
    }

    public boolean toggleLikeComment(int commentId, int currentUserId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT 1 FROM comment_likes WHERE comment_id = ? AND user_id = ?",
                    new String[]{String.valueOf(commentId), String.valueOf(currentUserId)});
            if (cursor.moveToFirst()) {
                db.delete("comment_likes", "comment_id = ? AND user_id = ?",
                        new String[]{String.valueOf(commentId), String.valueOf(currentUserId)});
                return false;
            } else {
                ContentValues values = new ContentValues();
                values.put("comment_id", commentId);
                values.put("user_id", currentUserId);
                db.insert("comment_likes", null, values);
                return true;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public boolean reportPost(int postId, int currentUserId, String reason) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("user_id", currentUserId);
        values.put("reason", reason);

        long id = db.insertWithOnConflict("reports", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return id != -1;
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