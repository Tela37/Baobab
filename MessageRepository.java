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
 * Repository responsable des opérations CRUD sécurisées sur la table des messages.
 * Gère l'envoi de texte/images, les réponses ciblées (Quote Reply), les réactions émojis multi-utilisateurs,
 * la suppression douce (is_deleted = 1), la limitation des requêtes (LIMIT 200) et le traitement des exceptions SQLite.
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
     * Envoie un message dans une conversation avec option de réponse ciblée (Quote Reply) et gestion des exceptions SQLite.
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

        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            ContentValues values = new ContentValues();
            values.put("conversation_id", conversationId);
            values.put("sender_id", senderId);
            values.put("message", hasText ? message.trim() : "");
            values.put("image_path", imagePath);
            values.put("is_read", 0);
            values.put("is_delivered", 1);
            values.put("is_deleted", 0);

            if (replyToMessageId != null) {
                values.put("reply_to_message_id", replyToMessageId);
            }
            if (replyToText != null && !replyToText.trim().isEmpty()) {
                values.put("reply_to_text", replyToText.trim());
            }

            long result = db.insert("messages", null, values);
            return result != -1;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean sendVideoMessage(int conversationId, int senderId, String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) return false;
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("conversation_id", conversationId);
            values.put("sender_id", senderId);
            values.put("message", "");
            values.put("video_path", videoPath);
            values.put("is_read", 0);
            values.put("is_delivered", 1);
            values.put("is_deleted", 0);

            long result = db.insert("messages", null, values);
            return result != -1;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Récupère les 200 derniers messages d'une conversation par ordre chronologique.
     * Optimisation : Résolution unique des index de colonnes en dehors de la boucle.
     */
    public List<Message> getMessages(int conversationId) {
        List<Message> messages = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT id, conversation_id, sender_id, message, image_path, video_path, created_at, is_read, is_delivered, reply_to_message_id, reply_to_text, reaction, is_deleted " +
                            "FROM messages WHERE conversation_id=? ORDER BY id ASC LIMIT 200",
                    new String[]{String.valueOf(conversationId)}
            );

            int idCol = cursor.getColumnIndexOrThrow("id");
            int convIdCol = cursor.getColumnIndexOrThrow("conversation_id");
            int senderIdCol = cursor.getColumnIndexOrThrow("sender_id");
            int msgCol = cursor.getColumnIndexOrThrow("message");
            int imgPathCol = cursor.getColumnIndex("image_path");
            int videoPathCol = cursor.getColumnIndex("video_path");
            int createdAtCol = cursor.getColumnIndexOrThrow("created_at");
            int isReadCol = cursor.getColumnIndexOrThrow("is_read");
            int isDeliveredCol = cursor.getColumnIndexOrThrow("is_delivered");
            int replyIdCol = cursor.getColumnIndex("reply_to_message_id");
            int replyTextCol = cursor.getColumnIndex("reply_to_text");
            int reactionCol = cursor.getColumnIndex("reaction");
            int deletedCol = cursor.getColumnIndex("is_deleted");

            while (cursor.moveToNext()) {
                String imgPath = (imgPathCol != -1 && !cursor.isNull(imgPathCol)) ? cursor.getString(imgPathCol) : null;
                String videoPath = (videoPathCol != -1 && !cursor.isNull(videoPathCol)) ? cursor.getString(videoPathCol) : null;
                Integer replyId = (replyIdCol != -1 && !cursor.isNull(replyIdCol)) ? cursor.getInt(replyIdCol) : null;
                String replyText = (replyTextCol != -1 && !cursor.isNull(replyTextCol)) ? cursor.getString(replyTextCol) : null;
                String reaction = (reactionCol != -1 && !cursor.isNull(reactionCol)) ? cursor.getString(reactionCol) : null;
                boolean isDeleted = (deletedCol != -1) && (cursor.getInt(deletedCol) == 1);

                String msgText = cursor.getString(msgCol);
                if (isDeleted) {
                    msgText = "Ce message a été supprimé 🚫";
                    imgPath = null;
                    videoPath = null;
                }

                Message message = new Message(
                        cursor.getInt(idCol),
                        cursor.getInt(convIdCol),
                        cursor.getInt(senderIdCol),
                        msgText,
                        imgPath,
                        cursor.getString(createdAtCol),
                        cursor.getInt(isReadCol) == 1,
                        cursor.getInt(isDeliveredCol) == 1,
                        replyId,
                        replyText,
                        reaction,
                        isDeleted
                );
                message.setVideoPath(videoPath);

                messages.add(message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return messages;
    }

    /**
     * Ajoute ou met à jour la réaction émoji d'un utilisateur sur un message (Multi-utilisateurs).
     */
    public boolean setMessageReaction(int messageId, int userId, String reaction) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            if (reaction == null) {
                db.delete("message_reactions", "message_id=? AND user_id=?", new String[]{String.valueOf(messageId), String.valueOf(userId)});
            } else {
                ContentValues rValues = new ContentValues();
                rValues.put("message_id", messageId);
                rValues.put("user_id", userId);
                rValues.put("reaction_type", reaction);
                db.insertWithOnConflict("message_reactions", null, rValues, SQLiteDatabase.CONFLICT_REPLACE);
            }

            ContentValues values = new ContentValues();
            values.put("reaction", reaction);
            int rows = db.update("messages", values, "id=?", new String[]{String.valueOf(messageId)});
            return rows > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean setMessageReaction(int messageId, String reaction) {
        return setMessageReaction(messageId, 0, reaction);
    }

    /**
     * Effectue une suppression douce (Soft Delete) du message en conservant l'enregistrement "Ce message a été supprimé".
     */
    public boolean deleteMessage(int messageId) {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("is_deleted", 1);
            values.put("message", "Ce message a été supprimé 🚫");
            values.put("image_path", (String) null);

            int rows = db.update("messages", values, "id=?", new String[]{String.valueOf(messageId)});
            return rows > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Marque tous les messages reçus de la conversation comme lus (✓✓ bleu).
     */
    public void markMessagesAsRead(int conversationId, int currentUserId) {
        try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getLastMessage(int conversationId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT message, image_path, is_deleted FROM messages WHERE conversation_id=? ORDER BY id DESC LIMIT 1",
                    new String[]{String.valueOf(conversationId)}
            );

            if (cursor.moveToFirst()) {
                int isDeletedCol = cursor.getColumnIndex("is_deleted");
                if (isDeletedCol != -1 && cursor.getInt(isDeletedCol) == 1) {
                    return "Ce message a été supprimé 🚫";
                }

                String text = cursor.getString(0);
                String imgPath = cursor.getString(1);

                if (imgPath != null && !imgPath.trim().isEmpty()) {
                    if (text != null && !text.trim().isEmpty()) {
                        return "📷 " + text;
                    }
                    return "📷 Photo";
                }

                return text != null ? text : "";
            }

            return "Aucun message";

        } catch (Exception e) {
            e.printStackTrace();
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
                    "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND sender_id!=? AND is_read=0 AND is_deleted=0",
                    new String[]{
                            String.valueOf(conversationId),
                            String.valueOf(currentUserId)
                    }
            );

            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }

            return 0;

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}