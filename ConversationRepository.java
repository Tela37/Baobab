package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.ConversationItem;
import td.teladoumbaobabtd.DatabaseHelper;

public class ConversationRepository {

    private final DatabaseHelper dbHelper;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public ConversationRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.userRepository = new UserRepository(dbHelper);
        this.messageRepository = new MessageRepository(dbHelper);
    }

    public ConversationRepository(DatabaseHelper dbHelper, UserRepository userRepository, MessageRepository messageRepository) {
        this.dbHelper = dbHelper;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
    }

    public long createConversation(int user1Id, int user2Id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user1_id", user1Id);
        values.put("user2_id", user2Id);

        long id = db.insert("conversations", null, values);
        if (id == -1) {
            try {
                id = db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            } catch (Exception ignored) {
            }
        }
        return id;
    }

    public int getConversationId(int user1Id, int user2Id) {

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT id FROM conversations WHERE (user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?)",
                    new String[]{
                            String.valueOf(user1Id),
                            String.valueOf(user2Id),
                            String.valueOf(user2Id),
                            String.valueOf(user1Id)
                    }
            );

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }

            return -1;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int createConversationIfNotExists(int user1Id, int user2Id) {

        int conversationId = getConversationId(user1Id, user2Id);

        if (conversationId != -1) {
            return conversationId;
        }

        long createdId = createConversation(user1Id, user2Id);
        if (createdId != -1) {
            return (int) createdId;
        }

        return getConversationId(user1Id, user2Id);
    }

    public List<ConversationItem> getConversations(int currentUserId) {
        return getConversations(currentUserId, false);
    }

    public List<ConversationItem> getConversations(int currentUserId, boolean showArchived) {

        List<ConversationItem> conversations = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String sql = "SELECT " +
                "c.id AS conversation_id, " +
                "COALESCE(u.name, 'Utilisateur') AS user_name, " +
                "u.profile_image AS profile_image, " +
                "COALESCE(u.is_online, 0) AS is_online, " +
                "u.last_seen AS last_seen, " +
                "COALESCE(" +
                "  (SELECT CASE " +
                "            WHEN m.audio_path IS NOT NULL AND m.audio_path != '' THEN '🎤 Message vocal' " +
                "            WHEN m.pdf_path IS NOT NULL AND m.pdf_path != '' THEN '📄 Document PDF' " +
                "            WHEN m.image_path IS NOT NULL AND m.image_path != '' THEN " +
                "              CASE WHEN m.message IS NOT NULL AND m.message != '' THEN '📷 ' || m.message ELSE '📷 Photo' END " +
                "            ELSE m.message " +
                "          END " +
                "   FROM messages m " +
                "   WHERE m.conversation_id = c.id " +
                "   ORDER BY m.id DESC LIMIT 1), " +
                "  'Aucun message'" +
                ") AS last_message, " +
                "(SELECT COUNT(*) FROM messages m WHERE m.conversation_id = c.id AND m.sender_id != ? AND m.is_read = 0) AS unread_count, " +
                "COALESCE(" +
                "  (SELECT MAX(m.created_at) FROM messages m WHERE m.conversation_id = c.id), " +
                "  c.created_at" +
                ") AS last_activity " +
                "FROM conversations c " +
                "LEFT JOIN users u ON u.id = CASE WHEN c.user1_id = ? THEN c.user2_id ELSE c.user1_id END " +
                "WHERE (c.user1_id = ? OR c.user2_id = ?) AND COALESCE(c.is_archived, 0) = ? " +
                "ORDER BY last_activity DESC, c.id DESC";

        String userIdStr = String.valueOf(currentUserId);
        String archivedStr = showArchived ? "1" : "0";
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(sql, new String[]{userIdStr, userIdStr, userIdStr, userIdStr, archivedStr});

            while (cursor.moveToNext()) {
                int conversationId = cursor.getInt(cursor.getColumnIndexOrThrow("conversation_id"));
                String userName = cursor.getString(cursor.getColumnIndexOrThrow("user_name"));
                String profileImage = cursor.getString(cursor.getColumnIndexOrThrow("profile_image"));
                String lastMessage = cursor.getString(cursor.getColumnIndexOrThrow("last_message"));
                int unreadCount = cursor.getInt(cursor.getColumnIndexOrThrow("unread_count"));

                boolean isOnline = cursor.getInt(cursor.getColumnIndexOrThrow("is_online")) == 1;
                int lastSeenIdx = cursor.getColumnIndex("last_seen");
                String lastSeen = (lastSeenIdx != -1 && !cursor.isNull(lastSeenIdx)) ? cursor.getString(lastSeenIdx) : null;

                conversations.add(
                        new ConversationItem(
                                conversationId,
                                userName,
                                lastMessage,
                                profileImage,
                                unreadCount,
                                isOnline,
                                lastSeen
                        )
                );
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return conversations;
    }

    public boolean toggleArchiveConversation(int conversationId, boolean isArchived) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_archived", isArchived ? 1 : 0);

        int rows = db.update("conversations", values, "id=?", new String[]{String.valueOf(conversationId)});
        return rows > 0;
    }

    public boolean deleteConversation(int conversationId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("messages", "conversation_id=?", new String[]{String.valueOf(conversationId)});
            int rows = db.delete("conversations", "id=?", new String[]{String.valueOf(conversationId)});
            db.setTransactionSuccessful();
            return rows > 0;
        } finally {
            db.endTransaction();
        }
    }
}
