package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.PasswordUtils;
import td.teladoumbaobabtd.User;

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

        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();

        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put("name", name);
            values.put("phone", phone);
            values.put("email", normalizedEmail);
            values.put("password", password);
            values.put("profile_image", profileImage);

            long result = db.insert("users", null, values);

            return result != -1;
        } catch (SQLiteConstraintException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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

        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        String combinedName = ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();
        if (combinedName.isEmpty()) {
            combinedName = normalizedEmail;
        }

        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put("name", combinedName);
            values.put("first_name", firstName);
            values.put("last_name", lastName);
            values.put("email", normalizedEmail);
            values.put("password", password);
            values.put("dob", dob);
            values.put("neighborhood", neighborhood);
            values.put("city", city);
            values.put("country", country);
            values.put("profile_image", profileImage);

            long result = db.insert("users", null, values);

            return result != -1;
        } catch (SQLiteConstraintException e) {
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean emailExists(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT id FROM users WHERE LOWER(email)=LOWER(?)",
                    new String[]{email.trim()}
            );

            return cursor.moveToFirst();

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private User cursorToUser(Cursor cursor) {
        if (cursor == null) return null;

        String profileImg = null;
        int profileImgColIndex = cursor.getColumnIndex("profile_image");
        if (profileImgColIndex != -1) {
            profileImg = cursor.getString(profileImgColIndex);
        }

        User user = new User(
                cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("name")),
                cursor.getString(cursor.getColumnIndexOrThrow("email")),
                profileImg
        );

        int fnIdx = cursor.getColumnIndex("first_name");
        if (fnIdx != -1) user.setFirstName(cursor.getString(fnIdx));

        int lnIdx = cursor.getColumnIndex("last_name");
        if (lnIdx != -1) user.setLastName(cursor.getString(lnIdx));

        int dobIdx = cursor.getColumnIndex("dob");
        if (dobIdx != -1) user.setDob(cursor.getString(dobIdx));

        int neighIdx = cursor.getColumnIndex("neighborhood");
        if (neighIdx != -1) user.setNeighborhood(cursor.getString(neighIdx));

        int cityIdx = cursor.getColumnIndex("city");
        if (cityIdx != -1) user.setCity(cursor.getString(cityIdx));

        int countryIdx = cursor.getColumnIndex("country");
        if (countryIdx != -1) user.setCountry(cursor.getString(countryIdx));

        int passIdx = cursor.getColumnIndex("password");
        if (passIdx != -1) user.setPassword(cursor.getString(passIdx));

        int phoneIdx = cursor.getColumnIndex("phone");
        if (phoneIdx != -1) user.setPhone(cursor.getString(phoneIdx));

        int hideEmailIdx = cursor.getColumnIndex("hide_email");
        if (hideEmailIdx != -1) user.setHideEmail(cursor.getInt(hideEmailIdx) == 1);

        int hideDobIdx = cursor.getColumnIndex("hide_dob");
        if (hideDobIdx != -1) user.setHideDob(cursor.getInt(hideDobIdx) == 1);

        int hideLocIdx = cursor.getColumnIndex("hide_location");
        if (hideLocIdx != -1) user.setHideLocation(cursor.getInt(hideLocIdx) == 1);

        return user;
    }

    public User getUser(
            String email,
            String password) {

        if (email == null || password == null) {
            return null;
        }

        String normalizedEmail = email.trim();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT * FROM users WHERE LOWER(email)=LOWER(?) AND password=?",
                    new String[]{normalizedEmail, password}
            );

            if (cursor.moveToFirst()) {
                return cursorToUser(cursor);
            }

            return null;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public User getUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT * FROM users WHERE LOWER(email)=LOWER(?)",
                    new String[]{email.trim()}
            );

            if (cursor.moveToFirst()) {
                return cursorToUser(cursor);
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

        String dummyPassword = PasswordUtils.hashPassword("SOCIAL_LOGIN_" + System.currentTimeMillis());
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
                return cursorToUser(cursor);
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

        ContentValues values = new ContentValues();
        values.put("first_name", firstName);
        values.put("last_name", lastName);
        values.put("name", ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim());
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

    public boolean updateUserName(int userId, String name) {

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});

        return rows > 0;
    }

    public boolean updateUserEmail(int userId, String newEmail) {
        if (newEmail == null || newEmail.trim().isEmpty()) {
            return false;
        }

        String normalized = newEmail.trim().toLowerCase();
        if (emailExists(normalized)) {
            User existing = getUserByEmail(normalized);
            if (existing != null && existing.getId() != userId) {
                return false; // Already taken by another user
            }
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("email", normalized);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }

    public boolean updateUserPassword(int userId, String newHashedPassword) {
        if (newHashedPassword == null || newHashedPassword.isEmpty()) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("password", newHashedPassword);

        int rows = db.update("users", values, "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
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

    public boolean deleteUser(int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("users", "id=?", new String[]{String.valueOf(userId)});
        return rows > 0;
    }
}
