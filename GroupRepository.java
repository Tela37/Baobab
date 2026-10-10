package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.Group;
import td.teladoumbaobabtd.GroupMessage;
import td.teladoumbaobabtd.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable de la gestion des groupes de discussion.
 * Gère la création sous contrainte d'amis, la gestion des membres,
 * le rôle d'administrateur, le verrouillage/réactivation et l'envoi de messages de groupe.
 */
public class GroupRepository {

    private final DatabaseHelper dbHelper;

    public GroupRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public GroupRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long createGroup(String name, String icon, int creatorId, List<Integer> selectedFriendIds) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues groupValues = new ContentValues();
            groupValues.put("name", name);
            groupValues.put("icon", icon);
            groupValues.put("creator_id", creatorId);
            groupValues.put("is_locked", 0);

            long groupId = db.insert("`groups`", null, groupValues);
            if (groupId == -1) {
                return -1;
            }

            // Ajouter le créateur en tant qu'ADMIN
            ContentValues creatorMember = new ContentValues();
            creatorMember.put("group_id", groupId);
            creatorMember.put("user_id", creatorId);
            creatorMember.put("role", "ADMIN");
            db.insert("group_members", null, creatorMember);

            // Ajouter les amis sélectionnés en tant que MEMBRES
            if (selectedFriendIds != null) {
                for (int friendId : selectedFriendIds) {
                    if (friendId != creatorId) {
                        ContentValues memberValues = new ContentValues();
                        memberValues.put("group_id", groupId);
                        memberValues.put("user_id", friendId);
                        memberValues.put("role", "MEMBER");
                        db.insert("group_members", null, memberValues);
                    }
                }
            }

            db.setTransactionSuccessful();
            return groupId;
        } finally {
            db.endTransaction();
        }
    }

    public List<Group> getUserGroups(int userId) {
        List<Group> groups = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT g.id, g.name, g.icon, g.creator_id, g.is_locked, g.created_at, " +
                "(SELECT COUNT(*) FROM group_members WHERE group_id = g.id) AS member_count, " +
                "(SELECT message FROM group_messages WHERE group_id = g.id ORDER BY id DESC LIMIT 1) AS last_message " +
                "FROM `groups` g " +
                "JOIN group_members gm ON g.id = gm.group_id " +
                "WHERE gm.user_id = ? " +
                "ORDER BY g.id DESC";

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, new String[]{String.valueOf(userId)});
            while (cursor.moveToNext()) {
                Group group = new Group();
                group.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                group.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                group.setIcon(cursor.getString(cursor.getColumnIndexOrThrow("icon")));
                group.setCreatorId(cursor.getInt(cursor.getColumnIndexOrThrow("creator_id")));
                group.setLocked(cursor.getInt(cursor.getColumnIndexOrThrow("is_locked")) == 1);
                group.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
                group.setMemberCount(cursor.getInt(cursor.getColumnIndexOrThrow("member_count")));

                String lastMsg = cursor.getString(cursor.getColumnIndexOrThrow("last_message"));
                group.setLastMessage(lastMsg != null ? lastMsg : "Aucun message");

                groups.add(group);
            }
        } finally {
            if (cursor != null) cursor.close();
        }

        return groups;
    }

    public Group getGroupById(int groupId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            String query = "SELECT g.id, g.name, g.icon, g.creator_id, g.is_locked, g.created_at, " +
                    "(SELECT COUNT(*) FROM group_members WHERE group_id = g.id) AS member_count " +
                    "FROM `groups` g WHERE g.id = ?";
            cursor = db.rawQuery(query, new String[]{String.valueOf(groupId)});
            if (cursor.moveToFirst()) {
                Group group = new Group();
                group.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                group.setName(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                group.setIcon(cursor.getString(cursor.getColumnIndexOrThrow("icon")));
                group.setCreatorId(cursor.getInt(cursor.getColumnIndexOrThrow("creator_id")));
                group.setLocked(cursor.getInt(cursor.getColumnIndexOrThrow("is_locked")) == 1);
                group.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
                group.setMemberCount(cursor.getInt(cursor.getColumnIndexOrThrow("member_count")));
                return group;
            }
            return null;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public boolean isUserAdmin(int groupId, int userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT role FROM group_members WHERE group_id = ? AND user_id = ?",
                    new String[]{String.valueOf(groupId), String.valueOf(userId)}
            );
            if (cursor.moveToFirst()) {
                String role = cursor.getString(0);
                return "ADMIN".equalsIgnoreCase(role);
            }
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public boolean updateGroupName(int groupId, String newName) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("name", newName);
        int rows = db.update("`groups`", values, "id = ?", new String[]{String.valueOf(groupId)});
        return rows > 0;
    }

    public boolean setGroupLocked(int groupId, boolean isLocked) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_locked", isLocked ? 1 : 0);
        int rows = db.update("`groups`", values, "id = ?", new String[]{String.valueOf(groupId)});
        return rows > 0;
    }

    public boolean addMemberToGroup(int groupId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("group_id", groupId);
        values.put("user_id", userId);
        values.put("role", "MEMBER");
        long result = db.insertWithOnConflict("group_members", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    public boolean removeMemberFromGroup(int groupId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("group_members", "group_id = ? AND user_id = ?", new String[]{String.valueOf(groupId), String.valueOf(userId)});
        return rows > 0;
    }

    public List<User> getGroupMembers(int groupId) {
        List<User> members = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            String query = "SELECT u.id, u.name, u.email, u.profile_image FROM users u " +
                    "JOIN group_members gm ON u.id = gm.user_id " +
                    "WHERE gm.group_id = ? " +
                    "ORDER BY u.name ASC";
            cursor = db.rawQuery(query, new String[]{String.valueOf(groupId)});
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String email = cursor.getString(2);
                String profileImage = cursor.getString(3);
                members.add(new User(id, name, email, profileImage));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return members;
    }

    public boolean sendGroupMessage(int groupId, int senderId, String message, String imagePath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("group_id", groupId);
        values.put("sender_id", senderId);
        values.put("message", message);
        values.put("image_path", imagePath);

        long result = db.insert("group_messages", null, values);
        return result != -1;
    }

    public boolean sendGroupVideoMessage(int groupId, int senderId, String videoPath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("group_id", groupId);
        values.put("sender_id", senderId);
        values.put("message", "");
        values.put("video_path", videoPath);

        long result = db.insert("group_messages", null, values);
        return result != -1;
    }

    public boolean toggleLikeGroupMessage(int groupMessageId, int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT 1 FROM group_message_likes WHERE group_message_id = ? AND user_id = ?",
                    new String[]{String.valueOf(groupMessageId), String.valueOf(userId)}
            );
            if (cursor.moveToFirst()) {
                db.delete("group_message_likes", "group_message_id = ? AND user_id = ?", new String[]{String.valueOf(groupMessageId), String.valueOf(userId)});
                return false;
            } else {
                ContentValues values = new ContentValues();
                values.put("group_message_id", groupMessageId);
                values.put("user_id", userId);
                db.insert("group_message_likes", null, values);
                return true;
            }
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    public List<GroupMessage> getGroupMessages(int groupId, int currentUserId) {
        List<GroupMessage> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            String query = "SELECT gm.id, gm.group_id, gm.sender_id, gm.message, gm.image_path, gm.video_path, gm.created_at, " +
                    "u.name AS sender_name, u.profile_image AS sender_avatar, " +
                    "(SELECT COUNT(*) FROM group_message_likes WHERE group_message_id = gm.id) AS likes_count, " +
                    "(SELECT COUNT(*) FROM group_message_likes WHERE group_message_id = gm.id AND user_id = ?) AS is_liked " +
                    "FROM group_messages gm " +
                    "JOIN users u ON gm.sender_id = u.id " +
                    "WHERE gm.group_id = ? " +
                    "ORDER BY gm.id ASC";
            cursor = db.rawQuery(query, new String[]{String.valueOf(currentUserId), String.valueOf(groupId)});
            while (cursor.moveToNext()) {
                GroupMessage msg = new GroupMessage();
                msg.setId(cursor.getInt(cursor.getColumnIndexOrThrow("id")));
                msg.setGroupId(cursor.getInt(cursor.getColumnIndexOrThrow("group_id")));
                msg.setSenderId(cursor.getInt(cursor.getColumnIndexOrThrow("sender_id")));
                msg.setSenderName(cursor.getString(cursor.getColumnIndexOrThrow("sender_name")));
                msg.setSenderAvatar(cursor.getString(cursor.getColumnIndexOrThrow("sender_avatar")));
                msg.setMessage(cursor.getString(cursor.getColumnIndexOrThrow("message")));
                msg.setImagePath(cursor.getString(cursor.getColumnIndexOrThrow("image_path")));

                int videoColIndex = cursor.getColumnIndex("video_path");
                if (videoColIndex != -1) {
                    msg.setVideoPath(cursor.getString(videoColIndex));
                }

                msg.setCreatedAt(cursor.getString(cursor.getColumnIndexOrThrow("created_at")));
                msg.setLikesCount(cursor.getInt(cursor.getColumnIndexOrThrow("likes_count")));
                msg.setLikedByCurrentUser(cursor.getInt(cursor.getColumnIndexOrThrow("is_liked")) > 0);

                messages.add(msg);
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return messages;
    }
}
