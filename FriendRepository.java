package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable de la gestion des amis, demandes d'amis
 * et de l'envoi de notifications lors de l'envoi ou de l'acceptation d'une demande.
 */
public class FriendRepository {

    public static class FriendRequestItem {
        public int requestId;
        public int senderId;
        public String senderName;
        public String senderProfileImage;
        public String createdAt;
    }

    public static class UserSearchItem {
        public int userId;
        public String name;
        public String profileImage;
        public boolean isFriend;
        public boolean requestSent;
    }

    private final DatabaseHelper dbHelper;
    private final NotificationRepository notificationRepository;

    public FriendRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.notificationRepository = new NotificationRepository(context);
    }

    public List<FriendRequestItem> getPendingRequests(int receiverId) {
        List<FriendRequestItem> requests = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT fr.id, fr.sender_id, fr.created_at, u.name AS sender_name, u.profile_image AS sender_profile_image " +
                "FROM friend_requests fr " +
                "JOIN users u ON fr.sender_id = u.id " +
                "WHERE fr.receiver_id = ? AND fr.status = 'PENDING' " +
                "ORDER BY fr.created_at DESC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{String.valueOf(receiverId)});
            while (cursor.moveToNext()) {
                FriendRequestItem item = new FriendRequestItem();
                item.requestId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                item.senderId = cursor.getInt(cursor.getColumnIndexOrThrow("sender_id"));
                item.senderName = cursor.getString(cursor.getColumnIndexOrThrow("sender_name"));
                item.senderProfileImage = cursor.getString(cursor.getColumnIndexOrThrow("sender_profile_image"));
                item.createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));
                requests.add(item);
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return requests;
    }

    /**
     * Envoie une demande d'ami et génère une notification pour le destinataire.
     */
    public boolean sendFriendRequest(int senderId, int receiverId) {
        if (senderId == receiverId) return false;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("sender_id", senderId);
        values.put("receiver_id", receiverId);
        values.put("status", "PENDING");

        long id = db.insertWithOnConflict("friend_requests", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) {
            String senderName = getUserName(senderId);
            notificationRepository.addNotification(
                    receiverId,
                    senderId,
                    "FRIEND_REQUEST",
                    "USER",
                    receiverId,
                    senderName + " vous a envoyé une demande d'ami."
            );
            return true;
        }
        return false;
    }

    /**
     * Accepte une demande d'ami et génère une notification pour l'expéditeur de la demande.
     */
    public boolean acceptFriendRequest(int requestId, int senderId, int receiverId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("friend_requests", "id = ?", new String[]{String.valueOf(requestId)});

            ContentValues values = new ContentValues();
            values.put("user1_id", Math.min(senderId, receiverId));
            values.put("user2_id", Math.max(senderId, receiverId));

            long id = db.insertWithOnConflict("friends", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            db.setTransactionSuccessful();

            if (id != -1 && senderId != receiverId) {
                String receiverName = getUserName(receiverId);
                notificationRepository.addNotification(
                        senderId,
                        receiverId,
                        "FRIEND_ACCEPT",
                        "USER",
                        senderId,
                        receiverName + " a accepté votre demande d'ami."
                );
            }

            return id != -1;
        } finally {
            db.endTransaction();
        }
    }

    public boolean declineFriendRequest(int requestId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("friend_requests", "id = ?", new String[]{String.valueOf(requestId)});
        return rows > 0;
    }

    public boolean removeFriend(int userId1, int userId2) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int u1 = Math.min(userId1, userId2);
        int u2 = Math.max(userId1, userId2);
        int rows = db.delete("friends", "user1_id = ? AND user2_id = ?", new String[]{String.valueOf(u1), String.valueOf(u2)});
        return rows > 0;
    }

    public List<User> getFriendsList(int userId, String query) {
        List<User> friends = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT u.id, u.name, u.email, u.profile_image FROM users u " +
                "JOIN friends f ON (u.id = f.user1_id AND f.user2_id = ?) OR (u.id = f.user2_id AND f.user1_id = ?) " +
                "WHERE u.id != ?";

        List<String> selectionArgs = new ArrayList<>();
        selectionArgs.add(String.valueOf(userId));
        selectionArgs.add(String.valueOf(userId));
        selectionArgs.add(String.valueOf(userId));

        if (query != null && !query.trim().isEmpty()) {
            sql += " AND (u.name LIKE ? OR u.email LIKE ?)";
            selectionArgs.add("%" + query.trim() + "%");
            selectionArgs.add("%" + query.trim() + "%");
        }

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, selectionArgs.toArray(new String[0]));
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
                String profileImage = cursor.getString(cursor.getColumnIndexOrThrow("profile_image"));

                friends.add(new User(id, name, email, profileImage));
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return friends;
    }

    public List<UserSearchItem> searchUsersToBefriend(int currentUserId, String query) {
        List<UserSearchItem> results = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT u.id, u.name, u.profile_image, " +
                "(SELECT COUNT(*) FROM friends WHERE (user1_id = u.id AND user2_id = ?) OR (user1_id = ? AND user2_id = u.id)) AS is_friend, " +
                "(SELECT COUNT(*) FROM friend_requests WHERE sender_id = ? AND receiver_id = u.id AND status = 'PENDING') AS request_sent " +
                "FROM users u " +
                "WHERE u.id != ?";

        List<String> selectionArgs = new ArrayList<>();
        selectionArgs.add(String.valueOf(currentUserId));
        selectionArgs.add(String.valueOf(currentUserId));
        selectionArgs.add(String.valueOf(currentUserId));
        selectionArgs.add(String.valueOf(currentUserId));

        if (query != null && !query.trim().isEmpty()) {
            sql += " AND (u.name LIKE ? OR u.email LIKE ?)";
            selectionArgs.add("%" + query.trim() + "%");
            selectionArgs.add("%" + query.trim() + "%");
        }

        sql += " ORDER BY u.name ASC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, selectionArgs.toArray(new String[0]));
            while (cursor.moveToNext()) {
                UserSearchItem item = new UserSearchItem();
                item.userId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                item.name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                item.profileImage = cursor.getString(cursor.getColumnIndexOrThrow("profile_image"));
                item.isFriend = cursor.getInt(cursor.getColumnIndexOrThrow("is_friend")) > 0;
                item.requestSent = cursor.getInt(cursor.getColumnIndexOrThrow("request_sent")) > 0;

                results.add(item);
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return results;
    }

    public boolean cancelFriendRequest(int senderId, int receiverId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("friend_requests", "sender_id = ? AND receiver_id = ? AND status = 'PENDING'",
                new String[]{String.valueOf(senderId), String.valueOf(receiverId)});
        return rows > 0;
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