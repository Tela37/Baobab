package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.Post;

public class PostRepository {

    private final DatabaseHelper dbHelper;

    public PostRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public PostRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public boolean createPost(int userId, String content, String imagePath) {
        boolean hasText = content != null && !content.trim().isEmpty();
        boolean hasImage = imagePath != null && !imagePath.trim().isEmpty();

        if (!hasText && !hasImage) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("content", hasText ? content.trim() : "");
        values.put("image_path", imagePath);

        long result = db.insert("posts", null, values);
        return result != -1;
    }

    public boolean updatePost(int postId, int userId, String newContent, String newImagePath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("content", newContent != null ? newContent.trim() : "");
        values.put("image_path", newImagePath);

        int rows = db.update("posts", values, "id=? AND user_id=?", new String[]{String.valueOf(postId), String.valueOf(userId)});
        return rows > 0;
    }

    public List<Post> getAllPosts(int currentUserId) {
        List<Post> posts = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT " +
                "p.id AS post_id, " +
                "p.user_id AS user_id, " +
                "p.content AS content, " +
                "p.image_path AS image_path, " +
                "p.created_at AS created_at, " +
                "u.name AS author_name, " +
                "u.profile_image AS author_profile_image, " +
                "(SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS likes_count, " +
                "(SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id AND pl.user_id = ?) AS user_liked, " +
                "(SELECT pr.reaction_type FROM post_reactions pr WHERE pr.post_id = p.id AND pr.user_id = ?) AS user_reaction, " +
                "(SELECT COUNT(*) FROM comments c WHERE c.post_id = p.id) AS comments_count, " +
                "(SELECT COUNT(*) FROM post_shares ps WHERE ps.post_id = p.id) AS shares_count " +
                "FROM posts p " +
                "JOIN users u ON u.id = p.user_id " +
                "ORDER BY p.id DESC";

        Cursor cursor = null;
        try {
            String uidStr = String.valueOf(currentUserId);
            cursor = db.rawQuery(sql, new String[]{uidStr, uidStr});

            while (cursor.moveToNext()) {
                int postId = cursor.getInt(cursor.getColumnIndexOrThrow("post_id"));
                int userId = cursor.getInt(cursor.getColumnIndexOrThrow("user_id"));
                String content = cursor.getString(cursor.getColumnIndexOrThrow("content"));
                String imagePath = cursor.getString(cursor.getColumnIndexOrThrow("image_path"));
                String createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
                String authorName = cursor.getString(cursor.getColumnIndexOrThrow("author_name"));
                String authorProfileImage = cursor.getString(cursor.getColumnIndexOrThrow("author_profile_image"));
                int likesCount = cursor.getInt(cursor.getColumnIndexOrThrow("likes_count"));
                boolean isLiked = cursor.getInt(cursor.getColumnIndexOrThrow("user_liked")) > 0;

                int reactIdx = cursor.getColumnIndex("user_reaction");
                String userReaction = (reactIdx != -1 && !cursor.isNull(reactIdx)) ? cursor.getString(reactIdx) : null;

                int commentsCount = cursor.getInt(cursor.getColumnIndexOrThrow("comments_count"));
                int sharesCount = cursor.getInt(cursor.getColumnIndexOrThrow("shares_count"));

                Post post = new Post(
                        postId,
                        userId,
                        authorName,
                        authorProfileImage,
                        content,
                        imagePath,
                        likesCount,
                        isLiked,
                        createdAt
                );
                post.setUserReactionType(userReaction);
                post.setCommentsCount(commentsCount);
                post.setSharesCount(sharesCount);

                posts.add(post);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return posts;
    }

    public boolean setPostReaction(int postId, int userId, String reactionType) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (reactionType == null) {
            db.delete("post_reactions", "post_id=? AND user_id=?", new String[]{String.valueOf(postId), String.valueOf(userId)});
            db.delete("post_likes", "post_id=? AND user_id=?", new String[]{String.valueOf(postId), String.valueOf(userId)});
            return true;
        }

        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("user_id", userId);
        values.put("reaction_type", reactionType);

        long res = db.insertWithOnConflict("post_reactions", null, values, SQLiteDatabase.CONFLICT_REPLACE);

        ContentValues likeValues = new ContentValues();
        likeValues.put("post_id", postId);
        likeValues.put("user_id", userId);
        db.insertWithOnConflict("post_likes", null, likeValues, SQLiteDatabase.CONFLICT_IGNORE);

        return res != -1;
    }

    public boolean recordPostShare(int postId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("post_id", postId);
        values.put("user_id", userId);
        long res = db.insert("post_shares", null, values);
        return res != -1;
    }

    public boolean toggleLikePost(int postId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = null;
        boolean alreadyLiked = false;
        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM post_likes WHERE post_id=? AND user_id=?",
                    new String[]{String.valueOf(postId), String.valueOf(userId)}
            );
            alreadyLiked = cursor.moveToFirst();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (alreadyLiked) {
            db.delete("post_reactions", "post_id=? AND user_id=?", new String[]{String.valueOf(postId), String.valueOf(userId)});
            int rows = db.delete("post_likes", "post_id=? AND user_id=?", new String[]{String.valueOf(postId), String.valueOf(userId)});
            return rows > 0;
        } else {
            return setPostReaction(postId, userId, "LIKE");
        }
    }

    public boolean deletePost(int postId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            String pId = String.valueOf(postId);
            db.delete("comment_likes", "comment_id IN (SELECT id FROM comments WHERE post_id=?)", new String[]{pId});
            db.delete("comments", "post_id=?", new String[]{pId});
            db.delete("post_likes", "post_id=?", new String[]{pId});
            db.delete("post_reactions", "post_id=?", new String[]{pId});
            db.delete("post_shares", "post_id=?", new String[]{pId});
            db.delete("reports", "post_id=?", new String[]{pId});
            int rows = db.delete("posts", "id=? AND user_id=?", new String[]{pId, String.valueOf(userId)});
            db.setTransactionSuccessful();
            return rows > 0;
        } finally {
            db.endTransaction();
        }
    }
}
