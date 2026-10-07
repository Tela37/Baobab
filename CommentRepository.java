package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.Comment;
import td.teladoumbaobabtd.DatabaseHelper;

public class CommentRepository {

    private final DatabaseHelper dbHelper;

    public CommentRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public CommentRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public boolean addComment(int postId, Integer parentId, int userId, String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        if (parentId != null) {
            values.put("parent_id", parentId);
        }
        values.put("user_id", userId);
        values.put("content", content.trim());

        long result = db.insert("comments", null, values);
        return result != -1;
    }

    public List<Comment> getCommentsForPost(int postId, int currentUserId) {
        List<Comment> comments = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT " +
                "c.id AS comment_id, " +
                "c.post_id AS post_id, " +
                "c.parent_id AS parent_id, " +
                "c.user_id AS user_id, " +
                "c.content AS content, " +
                "c.created_at AS created_at, " +
                "u.name AS author_name, " +
                "u.profile_image AS author_profile_image, " +
                "(SELECT COUNT(*) FROM comment_likes cl WHERE cl.comment_id = c.id) AS likes_count, " +
                "(SELECT COUNT(*) FROM comment_likes cl WHERE cl.comment_id = c.id AND cl.user_id = ?) AS user_liked " +
                "FROM comments c " +
                "JOIN users u ON u.id = c.user_id " +
                "WHERE c.post_id = ? " +
                "ORDER BY COALESCE(c.parent_id, c.id) ASC, c.id ASC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, new String[]{String.valueOf(currentUserId), String.valueOf(postId)});

            while (cursor.moveToNext()) {
                int commentId = cursor.getInt(cursor.getColumnIndexOrThrow("comment_id"));
                int pId = cursor.getInt(cursor.getColumnIndexOrThrow("post_id"));

                Integer parentId = null;
                int parentIdIdx = cursor.getColumnIndex("parent_id");
                if (parentIdIdx != -1 && !cursor.isNull(parentIdIdx)) {
                    parentId = cursor.getInt(parentIdIdx);
                }

                int userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"));
                String content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
                String createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
                String authorName = cursor.getString(cursor.getColumnIndexOrThrow("author_name"));
                String authorProfileImage = cursor.getString(cursor.getColumnIndexOrThrow("author_profile_image"));
                int likesCount = cursor.getInt(cursor.getColumnIndexOrThrow("likes_count"));
                boolean isLiked = cursor.getInt(cursor.getColumnIndexOrThrow("user_liked")) > 0;

                comments.add(new Comment(
                        commentId,
                        pId,
                        parentId,
                        userId,
                        authorName,
                        authorProfileImage,
                        content,
                        likesCount,
                        isLiked,
                        createdAt
                ));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return comments;
    }

    public boolean toggleLikeComment(int commentId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = null;
        boolean alreadyLiked = false;
        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM comment_likes WHERE comment_id=? AND user_id=?",
                    new String[]{String.valueOf(commentId), String.valueOf(userId)}
            );
            alreadyLiked = cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (alreadyLiked) {
            int rows = db.delete("comment_likes", "comment_id=? AND user_id=?", new String[]{String.valueOf(commentId), String.valueOf(userId)});
            return rows > 0;
        } else {
            ContentValues values = new ContentValues();
            values.put("comment_id", commentId);
            values.put("user_id", userId);
            long res = db.insert("comment_likes", null, values);
            return res != -1;
        }
    }

    public boolean reportPost(int postId, int userId, String reason) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("user_id", userId);
        values.put("reason", reason);

        long res = db.insertWithOnConflict("reports", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return res != -1;
    }
}
