package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.PasswordUtils;
import td.teladoumbaobabtd.User;

import java.util.ArrayList;
import java.util.List;

public class UserRepository {

    private final DatabaseHelper dbHelper;

    public UserRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public UserRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public boolean insertUser(
            String name,
            String phone,
            String email,
            String password) {
        return insertUser(name, phone, email, password, null);
    }

    public boolean insertUser(
            String name,
            String phone,
            String email,
            String password,
            String profileImage) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("phone", phone);
        values.put("email", email);
        values.put("password", password);
        values.put("profile_image", profileImage);

        long result = db.insert("users", null, values);

        return result != -1;
    }

    public boolean insertFullUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String fullName = ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();

        ContentValues values = new ContentValues();
        values.put("name", fullName);
        values.put("first_name", firstName);
        values.put("last_name", lastName);
        values.put("email", email);
        values.put("password", password);
        values.put("dob", dob);
        values.put("neighborhood", neighborhood);
        values.put("city", city);
        values.put("country", country);
        values.put("profile_image", profileImage);

        long result = db.insert("users", null, values);
        return result != -1;
    }

    public boolean emailExists(String email) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT id FROM users WHERE email=?",
                    new String[]{email}
            );

            return cursor.moveToFirst();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private User mapCursorToUser(Cursor cursor) {
        User user = new User();
        user.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
        user.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
        user.setEmail(cursor.getString(cursor.getColumnIndexOrThrow("email")));

        int colIdx = cursor.getColumnIndex("first_name");
        if (colIdx != -1) user.setFirstName(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("last_name");
        if (colIdx != -1) user.setLastName(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("phone");
        if (colIdx != -1) user.setPhone(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("password");
        if (colIdx != -1) user.setPassword(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("profile_image");
        if (colIdx != -1) user.setProfileImage(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("dob");
        if (colIdx != -1) user.setDob(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("neighborhood");
        if (colIdx != -1) user.setNeighborhood(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("city");
        if (colIdx != -1) user.setCity(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("country");
        if (colIdx != -1) user.setCountry(cursor.getString(colIdx));

        colIdx = cursor.getColumnIndex("hide_email");
        if (colIdx != -1) user.setHideEmail(cursor.getInt(colIdx) == 1);

        colIdx = cursor.getColumnIndex("hide_dob");
        if (colIdx != -1) user.setHideDob(cursor.getInt(colIdx) == 1);

        colIdx = cursor.getColumnIndex("hide_location");
        if (colIdx != -1) user.setHideLocation(cursor.getInt(colIdx) == 1);

        colIdx = cursor.getColumnIndex("is_online");
        if (colIdx != -1) user.setOnline(cursor.getInt(colIdx) == 1);

        colIdx = cursor.getColumnIndex("last_seen");
        if (colIdx != -1) user.setLastSeen(cursor.getString(colIdx));

        return user;
    }

    public User getUser(String email, String rawPasswordOrHash) {
        User user = getUserByEmail(email);
        if (user == null || rawPasswordOrHash == null) {
            return null;
        }

        String storedHash = user.getPassword();
        if (storedHash == null) {
            return null;
        }

        String pbkdf2Hash = PasswordUtils.hashPassword(rawPasswordOrHash);
        String saltedSha256 = PasswordUtils.hashPasswordSha256(rawPasswordOrHash);
        String unsaltedSha256 = PasswordUtils.hashPasswordUnsalted(rawPasswordOrHash);

        // 1. Vérification PBKDF2 (Hachage fort standard)
        if (storedHash.equals(pbkdf2Hash) || storedHash.equals(rawPasswordOrHash)) {
            return user;
        }

        // 2. Vérification Rétrocompatible SHA-256 (avec ou sans sel)
        if (storedHash.equals(saltedSha256) || storedHash.equals(unsaltedSha256)) {
            // Migration automatique du mot de passe vers le hachage fort PBKDF2
            updateUserPassword(user.getId(), pbkdf2Hash);
            user.setPassword(pbkdf2Hash);
            return user;
        }

        return null;
    }

    public User getUserByEmail(String email) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT * FROM users WHERE email=?",
                    new String[]{email}
            );

            if (cursor.moveToFirst()) {
                return mapCursorToUser(cursor);
            }

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public User findOrCreateSocialUser(String email, String name, String profileImage) {
        User existingUser = getUserByEmail(email);
        if (existingUser != null) {
            if (profileImage != null && !profileImage.isEmpty() && existingUser.getProfileImage() == null) {
                updateUserProfileImage(existingUser.getId(), profileImage);
                return getUserByEmail(email);
            }
            return existingUser;
        }

        String dummyPassword = "SOCIAL_LOGIN_NOPASS_" + System.currentTimeMillis();
        boolean inserted = insertUser(name, "", email, dummyPassword, profileImage);
        if (inserted) {
            return getUserByEmail(email);
        }
        return null;
    }

    public User getUserById(int userId) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT * FROM users WHERE id=?",
                    new String[]{String.valueOf(userId)}
            );

            if (cursor.moveToFirst()) {
                return mapCursorToUser(cursor);
            }

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getUserName(int userId) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT name FROM users WHERE id=?",
                    new String[]{String.valueOf(userId)}
            );

            if (cursor.moveToFirst()) {
                return cursor.getString(0);
            }

            return "";

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public String getUserProfileImage(int userId) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT profile_image FROM users WHERE id=?",
                    new String[]{String.valueOf(userId)}
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

    public boolean updateUserProfileImage(int userId, String imagePath) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("profile_image", imagePath);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});

        return rows > 0;
    }

    public boolean updateUserName(int userId, String name) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});

        return rows > 0;
    }

    public boolean updateUserProfile(
            int userId,
            String firstName,
            String lastName,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String fullName = ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();

        ContentValues values = new ContentValues();
        if (!fullName.isEmpty()) values.put("name", fullName);
        values.put("first_name", firstName);
        values.put("last_name", lastName);
        values.put("dob", dob);
        values.put("neighborhood", neighborhood);
        values.put("city", city);
        values.put("country", country);
        if (profileImage != null) {
            values.put("profile_image", profileImage);
        }

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean updateUserPassword(int userId, String newHashedPassword) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("password", newHashedPassword);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean updateUserOnlineStatus(int userId, boolean isOnline) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_online", isOnline ? 1 : 0);
        values.put("last_seen", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean updateUserPrivacy(int userId, boolean hideEmail, boolean hideDob, boolean hideLocation) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("hide_email", hideEmail ? 1 : 0);
        values.put("hide_dob", hideDob ? 1 : 0);
        values.put("hide_location", hideLocation ? 1 : 0);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean updateUserEmail(int userId, String newEmail) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("email", newEmail);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean deleteUser(int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("users", "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean blockUser(int blockerId, int blockedId) {
        if (blockerId == blockedId) return false;
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("blocker_id", blockerId);
        values.put("blocked_id", blockedId);
        long result = db.insertWithOnConflict("blocks", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    public boolean unblockUser(int blockerId, int blockedId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("blocks", "blocker_id=? AND blocked_id=?", new String[]{String.valueOf(blockerId), String.valueOf(blockedId)});
        return rows > 0;
    }

    public boolean isUserBlocked(int blockerId, int blockedId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT 1 FROM blocks WHERE blocker_id=? AND blocked_id=?", new String[]{String.valueOf(blockerId), String.valueOf(blockedId)});
            return cursor.moveToFirst();
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public List<Integer> getBlockedUserIds(int blockerId) {
        List<Integer> blockedIds = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT blocked_id FROM blocks WHERE blocker_id=?", new String[]{String.valueOf(blockerId)});
            while (cursor.moveToNext()) {
                blockedIds.add(cursor.getInt(0));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return blockedIds;
    }

    public List<User> getAllUsersExcept(int currentUserId) {

        List<User> users = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT id, name, email, profile_image FROM users WHERE id!=?",
                    new String[]{String.valueOf(currentUserId)}
            );

            while (cursor.moveToNext()) {
                String profileImg = null;
                int colIndex = cursor.getColumnIndex("profile_image");
                if (colIndex != -1) {
                    profileImg = cursor.getString(colIndex);
                }

                users.add(
                        new User(
                                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                                cursor.getString(cursor.getColumnIndexOrThrow("email")),
                                profileImg
                        )
                );
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return users;
    }
}
