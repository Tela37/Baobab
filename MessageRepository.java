package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.MessageModel;

public class MessageRepository {

    private final DatabaseHelper dbHelper;

    public MessageRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public MessageRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public long sendMessage(int conversationId, int senderId, String message) {
        return sendMessage(conversationId, senderId, message, null, null, null);
    }

    public long sendMessage(int conversationId, int senderId, String message,
                            String imagePath, String audioPath, String pdfPath) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("conversation_id", conversationId);
        values.put("sender_id", senderId);
        values.put("message", message);
        values.put("image_path", imagePath);
        values.put("audio_path", audioPath);
        values.put("pdf_path", pdfPath);
        values.put("is_read", 0);
        values.put("is_delivered", 1);

        return db.insert("messages", null, values);
    }

    public List<MessageModel> getMessages(int conversationId) {
        List<MessageModel> messagesList = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT id, conversation_id, sender_id, message, image_path, audio_path, pdf_path, " +
                            "is_read, is_delivered, is_edited, is_starred, read_at, created_at " +
                            "FROM messages WHERE conversation_id = ? ORDER BY id ASC",
                    new String[]{String.valueOf(conversationId)}
            );

            while (cursor.moveToNext()) {
                MessageModel message = extractMessageFromCursor(cursor);
                messagesList.add(message);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return messagesList;
    }

    public boolean updateMessage(int messageId, String newMessage) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("message", newMessage);
        values.put("is_edited", 1);

        int rows = db.update("messages", values, "id=?", new String[]{String.valueOf(messageId)});
        return rows > 0;
    }

    public boolean deleteMessage(int messageId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("messages", "id=?", new String[]{String.valueOf(messageId)});
        return rows > 0;
    }

    public void markConversationAsRead(int conversationId, int currentUserId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_read", 1);

        db.update(
                "messages",
                values,
                "conversation_id=? AND sender_id!=? AND is_read=0",
                new String[]{String.valueOf(conversationId), String.valueOf(currentUserId)}
        );
    }

    public boolean toggleStarredMessage(int messageId, boolean isStarred) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_starred", isStarred ? 1 : 0);

        int rows = db.update("messages", values, "id=?", new String[]{String.valueOf(messageId)});
        return rows > 0;
    }

    private MessageModel extractMessageFromCursor(Cursor cursor) {
        int id = cursor.getInt(cursor.getColumnIndexOrThrow("id"));
        int conversationId = cursor.getInt(cursor.getColumnIndexOrThrow("conversation_id"));
        int senderId = cursor.getInt(cursor.getColumnIndexOrThrow("sender_id"));
        String message = cursor.getString(cursor.getColumnIndexOrThrow("message"));

        int imgIdx = cursor.getColumnIndex("image_path");
        String imagePath = (imgIdx != -1 && !cursor.isNull(imgIdx)) ? cursor.getString(imgIdx) : null;

        int audioIdx = cursor.getColumnIndex("audio_path");
        String audioPath = (audioIdx != -1 && !cursor.isNull(audioIdx)) ? cursor.getString(audioIdx) : null;

        int pdfIdx = cursor.getColumnIndex("pdf_path");
        String pdfPath = (pdfIdx != -1 && !cursor.isNull(pdfIdx)) ? cursor.getString(pdfIdx) : null;

        boolean isRead = cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1;

        int delivIdx = cursor.getColumnIndex("is_delivered");
        boolean isDelivered = (delivIdx != -1) && (cursor.getInt(delivIdx) == 1);

        int editIdx = cursor.getColumnIndex("is_edited");
        boolean isEdited = (editIdx != -1) && (cursor.getInt(editIdx) == 1);

        int starIdx = cursor.getColumnIndex("is_starred");
        boolean isStarred = (starIdx != -1) && (cursor.getInt(starIdx) == 1);

        int readAtIdx = cursor.getColumnIndex("read_at");
        String readAt = (readAtIdx != -1 && !cursor.isNull(readAtIdx)) ? cursor.getString(readAtIdx) : null;

        String createdAt = cursor.getString(cursor.getColumnIndexOrThrow("created_at"));

        return new MessageModel(
                id, conversationId, senderId, message,
                imagePath, audioPath, pdfPath,
                isRead, isDelivered, isEdited,
                isStarred, readAt, createdAt
        );
    }
}
