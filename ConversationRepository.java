package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.ConversationItem;
import td.teladoumbaobabtd.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

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
        int u1 = Math.min(user1Id, user2Id);
        int u2 = Math.max(user1Id, user2Id);

        ContentValues values = new ContentValues();
        values.put("user1_id", u1);
        values.put("user2_id", u2);

        return db.insertWithOnConflict("conversations", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public int getConversationId(int user1Id, int user2Id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        int u1 = Math.min(user1Id, user2Id);
        int u2 = Math.max(user1Id, user2Id);

        try {
            cursor = db.rawQuery(
                    "SELECT id FROM conversations WHERE (user1_id=? AND user2_id=?) OR (user1_id=? AND user2_id=?)",
                    new String[]{
                            String.valueOf(u1),
                            String.valueOf(u2),
                            String.valueOf(u2),
                            String.valueOf(u1)
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
        return (int) createConversation(user1Id, user2Id);
    }

    public int getOtherUserId(int conversationId, int currentUserId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT user1_id, user2_id FROM conversations WHERE id=?",
                    new String[]{String.valueOf(conversationId)}
            );
            if (cursor.moveToFirst()) {
                int user1Id = cursor.getInt(0);
                int user2Id = cursor.getInt(1);
                return (user1Id == currentUserId) ? user2Id : user1Id;
            }
            return -1;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public List<ConversationItem> getConversations(int currentUserId) {
        List<ConversationItem> conversations = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT * FROM conversations ORDER BY is_pinned DESC, id DESC",
                    null
            );

            while (cursor.moveToNext()) {
                int conversationId = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
                int user1Id = cursor.getInt(cursor.getColumnIndexOrThrow("user1_id"));
                int user2Id = cursor.getInt(cursor.getColumnIndexOrThrow("user2_id"));

                if (user1Id != currentUserId && user2Id != currentUserId) {
                    continue;
                }

                int isPrivateCol = cursor.getColumnIndex("is_private");
                boolean isPrivate = isPrivateCol != -1 && cursor.getInt(isPrivateCol) == 1;

                int isPinnedCol = cursor.getColumnIndex("is_pinned");
                boolean isPinned = isPinnedCol != -1 && cursor.getInt(isPinnedCol) == 1;

                int otherUserId = (user1Id == currentUserId) ? user2Id : user1Id;

                conversations.add(
                        new ConversationItem(
                                conversationId,
                                otherUserId,
                                userRepository.getUserName(otherUserId),
                                messageRepository.getLastMessage(conversationId),
                                userRepository.getUserProfileImage(otherUserId),
                                messageRepository.getUnreadCount(conversationId, currentUserId),
                                isPrivate,
                                isPinned
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

    public boolean setConversationPrivate(int conversationId, boolean isPrivate) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_private", isPrivate ? 1 : 0);
        int rows = db.update("conversations", values, "id=?", new String[]{String.valueOf(conversationId)});
        return rows > 0;
    }

    public boolean setConversationPinned(int conversationId, boolean isPinned) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_pinned", isPinned ? 1 : 0);
        int rows = db.update("conversations", values, "id=?", new String[]{String.valueOf(conversationId)});
        return rows > 0;
    }

    public boolean isConversationPrivate(int conversationId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT is_private FROM conversations WHERE id=?", new String[]{String.valueOf(conversationId)});
            if (cursor.moveToFirst()) {
                return cursor.getInt(0) == 1;
            }
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
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
