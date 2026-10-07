package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.User;

public class FriendRepository {

    private final DatabaseHelper dbHelper;

    public FriendRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public FriendRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public boolean sendFriendRequest(int senderId, int receiverId) {
        if (senderId == receiverId) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("sender_id", senderId);
        values.put("receiver_id", receiverId);
        values.put("status", "PENDING");

        long res = db.insert("friend_requests", null, values);
        return res != -1;
    }

    public boolean cancelFriendRequest(int senderId, int receiverId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("friend_requests", "sender_id=? AND receiver_id=? AND status='PENDING'", new String[]{String.valueOf(senderId), String.valueOf(receiverId)});
        return rows > 0;
    }

    public boolean removeFriend(int user1Id, int user2Id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("friends", "(user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?)", new String[]{
                    String.valueOf(user1Id), String.valueOf(user2Id),
                    String.valueOf(user2Id), String.valueOf(user1Id)
            });
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean acceptFriendRequest(int requestId, int senderId, int receiverId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues requestValues = new ContentValues();
            requestValues.put("status", "ACCEPTED");
            db.update("friend_requests", requestValues, "id=?", new String[]{String.valueOf(requestId)});

            ContentValues friendValues1 = new ContentValues();
            friendValues1.put("user1_id", senderId);
            friendValues1.put("user2_id", receiverId);
            db.insertWithOnConflict("friends", null, friendValues1, SQLiteDatabase.CONFLICT_IGNORE);

            ContentValues friendValues2 = new ContentValues();
            friendValues2.put("user1_id", receiverId);
            friendValues2.put("user2_id", senderId);
            db.insertWithOnConflict("friends", null, friendValues2, SQLiteDatabase.CONFLICT_IGNORE);

            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            db.endTransaction();
        }
    }

    public boolean declineFriendRequest(int requestId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("status", "DECLINED");

        int rows = db.update("friend_requests", values, "id=?", new String[]{String.valueOf(requestId)});
        return rows > 0;
    }

    public List<FriendRequestItem> getPendingRequests(int userId) {
        List<FriendRequestItem> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT fr.id AS request_id, fr.sender_id AS sender_id, u.name AS sender_name, u.email AS sender_email, u.profile_image AS sender_profile_image " +
                "FROM friend_requests fr " +
                "JOIN users u ON u.id = fr.sender_id " +
                "WHERE fr.receiver_id = ? AND fr.status = 'PENDING' " +
                "ORDER BY fr.id DESC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(sql, new String[]{String.valueOf(userId)});
            while (cursor.moveToNext()) {
                int reqId = cursor.getInt(cursor.getColumnIndexOrThrow("request_id"));
                int senderId = cursor.getInt(cursor.getColumnIndexOrThrow("sender_id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("sender_name"));
                String email = cursor.getString(cursor.getColumnIndexOrThrow("sender_email"));
                String profileImg = cursor.getString(cursor.getColumnIndexOrThrow("sender_profile_image"));

                list.add(new FriendRequestItem(reqId, senderId, name, email, profileImg));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return list;
    }

    public List<User> getFriendsList(int userId, String searchQuery) {
        List<User> friends = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT u.id, u.name, u.email, u.profile_image, u.city, u.country " +
                "FROM friends f " +
                "JOIN users u ON u.id = f.user2_id " +
                "WHERE f.user1_id = ? ";

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql += "AND (LOWER(u.name) LIKE LOWER(?) OR LOWER(u.email) LIKE LOWER(?)) ";
        }

        sql += "ORDER BY u.name ASC";

        Cursor cursor = null;
        try {
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String term = "%" + searchQuery.trim() + "%";
                cursor = db.rawQuery(sql, new String[]{String.valueOf(userId), term, term});
            } else {
                cursor = db.rawQuery(sql, new String[]{String.valueOf(userId)});
            }

            while (cursor.moveToNext()) {
                User user = new User(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("email")),
                        cursor.getString(cursor.getColumnIndexOrThrow("profile_image"))
                );
                int cityIdx = cursor.getColumnIndex("city");
                if (cityIdx != -1) user.setCity(cursor.getString(cityIdx));

                int countryIdx = cursor.getColumnIndex("country");
                if (countryIdx != -1) user.setCountry(cursor.getString(countryIdx));

                friends.add(user);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return friends;
    }

    public List<UserSearchItem> searchUsersToBefriend(int userId, String searchQuery) {
        List<UserSearchItem> users = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT u.id, u.name, u.email, u.profile_image, u.city, u.country, " +
                "EXISTS(SELECT 1 FROM friends f WHERE f.user1_id = ? AND f.user2_id = u.id) AS is_friend, " +
                "EXISTS(SELECT 1 FROM friend_requests fr WHERE fr.sender_id = ? AND fr.receiver_id = u.id AND fr.status = 'PENDING') AS request_sent " +
                "FROM users u " +
                "WHERE u.id != ? ";

        if (searchQuery != null && !searchQuery.trim().isEmpty()) {
            sql += "AND (LOWER(u.name) LIKE LOWER(?) OR LOWER(u.email) LIKE LOWER(?)) ";
        }

        sql += "ORDER BY u.name ASC";

        Cursor cursor = null;
        try {
            String uidStr = String.valueOf(userId);
            if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                String term = "%" + searchQuery.trim() + "%";
                cursor = db.rawQuery(sql, new String[]{uidStr, uidStr, uidStr, term, term});
            } else {
                cursor = db.rawQuery(sql, new String[]{uidStr, uidStr, uidStr});
            }

            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                String email = cursor.getString(cursor.getColumnIndexOrThrow("email"));
                String profileImg = cursor.getString(cursor.getColumnIndexOrThrow("profile_image"));
                boolean isFriend = cursor.getInt(cursor.getColumnIndexOrThrow("is_friend")) > 0;
                boolean requestSent = cursor.getInt(cursor.getColumnIndexOrThrow("request_sent")) > 0;

                users.add(new UserSearchItem(id, name, email, profileImg, isFriend, requestSent));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return users;
    }

    public static class FriendRequestItem {
        public final int requestId;
        public final int senderId;
        public final String senderName;
        public final String senderEmail;
        public final String senderProfileImage;

        public FriendRequestItem(int requestId, int senderId, String senderName, String senderEmail, String senderProfileImage) {
            this.requestId = requestId;
            this.senderId = senderId;
            this.senderName = senderName;
            this.senderEmail = senderEmail;
            this.senderProfileImage = senderProfileImage;
        }
    }

    public static class UserSearchItem {
        public final int userId;
        public final String name;
        public final String email;
        public final String profileImage;
        public final boolean isFriend;
        public boolean requestSent;

        public UserSearchItem(int userId, String name, String email, String profileImage, boolean isFriend, boolean requestSent) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.profileImage = profileImage;
            this.isFriend = isFriend;
            this.requestSent = requestSent;
        }
    }
}
