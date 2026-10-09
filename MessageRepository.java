package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.DatabaseHelper;
import td.teladoumbaobabtd.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable des opérations CRUD sur la table des messages.
 * Gère l'envoi de texte/images, les réponses ciblées (Quote Reply), les réactions émojis,
 * la lecture et le statut des messages.
 */
public class MessageRepository {

    private final DatabaseHelper dbHelper;

    public MessageRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public MessageRepository(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public boolean sendMessage(int conversationId, int senderId, String message) {
        return sendMessage(conversationId, senderId, message, null, null, null);
    }

    public boolean sendMessage(int conversationId, int senderId, String message, String imagePath) {
        return sendMessage(conversationId, senderId, message, imagePath, null, null);
    }

    /**
     * Envoie un message dans une conversation avec option de réponse ciblée (Quote Reply).
     */
    public boolean sendMessage(
            int conversationId,
            int senderId,
            String message,
            String imagePath,
            Integer replyToMessageId,
            String replyToText) {

        boolean hasText = message != null && !message.trim().isEmpty();
        boolean hasImage = imagePath != null && !imagePath.trim().isEmpty();

        if (!hasText && !hasImage) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("conversation_id", conversationId);
        values.put("sender_id", senderId);
        values.put("message", hasText ? message.trim() : "");
        values.put("image_path", imagePath);
        values.put("is_read", 0);
        values.put("is_delivered", 1);

        if (replyToMessageId != null) {
            values.put("reply_to_message_id", replyToMessageId);
        }
        if (replyToText != null && !replyToText.trim().isEmpty()) {
            values.put("reply_to_text", replyToText.trim());
        }

        long result = db.insert("messages", null, values);
        return result != -1;
    }

    /**
     * Récupère l'ensemble des messages d'une conversation par ordre chronologique.
     */
    public List<Message> getMessages(int conversationId) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT * FROM messages WHERE conversation_id=? ORDER BY id ASC",
                    new String[]{String.valueOf(conversationId)}
            );

            while (cursor.moveToNext()) {
                String imgPath = null;
                int imgPathColIndex = cursor.getColumnIndex("image_path");
                if (imgPathColIndex != -1) {
                    imgPath = cursor.getString(imgPathColIndex);
                }

                Integer replyId = null;
                int replyIdCol = cursor.getColumnIndex("reply_to_message_id");
                if (replyIdCol != -1 && !cursor.isNull(replyIdCol)) {
                    replyId = cursor.getInt(replyIdCol);
                }

                String replyText = null;
                int replyTextCol = cursor.getColumnIndex("reply_to_text");
                if (replyTextCol != -1) {
                    replyText = cursor.getString(replyTextCol);
                }

                String reaction = null;
                int reactionCol = cursor.getColumnIndex("reaction");
                if (reactionCol != -1) {
                    reaction = cursor.getString(reactionCol);
                }

                Message message = new Message(
                        cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("conversation_id")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("sender_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("message")),
                        imgPath,
                        cursor.getString(cursor.getColumnIndexOrThrow("created_at")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 1,
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_delivered")) == 1,
                        replyId,
                        replyText,
                        reaction
                );

                messages.add(message);
            }

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return messages;
    }

    /**
     * Ajoute ou met à jour la réaction émoji sur un message.
     */
    public boolean setMessageReaction(int messageId, String reaction) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("reaction", reaction);

        int rows = db.update("messages", values, "id=?", new String[]{String.valueOf(messageId)});
        return rows > 0;
    }

    public boolean deleteMessage(int messageId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int rows = db.delete("messages", "id=?", new String[]{String.valueOf(messageId)});
        return rows > 0;
    }

    /**
     * Marque tous les messages reçus de la conversation comme lus (✓✓ bleu).
     */
    public void markMessagesAsRead(int conversationId, int currentUserId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("is_read", 1);

        db.update(
                "messages",
                values,
                "conversation_id=? AND sender_id!=? AND is_read=0",
                new String[]{
                        String.valueOf(conversationId),
                        String.valueOf(currentUserId)
                }
        );
    }

    public String getLastMessage(int conversationId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT message, image_path FROM messages WHERE conversation_id=? ORDER BY id DESC LIMIT 1",
                    new String[]{String.valueOf(conversationId)}
            );

            if (cursor.moveToFirst()) {
                String text = cursor.getString(0);
                String imgPath = cursor.getString(1);

                if (imgPath != null && !imgPath.trim().isEmpty()) {
                    if (text != null && !text.trim().isEmpty()) {
                        return "📷 " + text;
                    }
                    return "📷 Photo";
                }

                return text;
            }

            return "Aucun message";

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    public int getUnreadCount(int conversationId, int currentUserId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND sender_id!=? AND is_read=0",
                    new String[]{
                            String.valueOf(conversationId),
                            String.valueOf(currentUserId)
                    }
            );

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }

            return 0;

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}