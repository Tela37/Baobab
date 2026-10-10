package td.teladoumbaobabtd.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import td.teladoumbaobabtd.AppNotification;
import td.teladoumbaobabtd.DatabaseHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository responsable de la gestion des notifications d'interactions (réactions, commentaires, demandes d'amis).
 * Filtre automatiquement les auto-interactions (pas de notification lorsque l'utilisateur réagit à son propre contenu).
 */
public class NotificationRepository {

    private final DatabaseHelper dbHelper;

    public NotificationRepository(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public boolean addNotification(int recipientId, int senderId, String type, int targetId, String message) {
        return addNotification(recipientId, senderId, type, "POST", targetId, message);
    }

    /**
     * Enregistre une nouvelle notification si l'expéditeur n'est pas le destinataire.
     */
    public boolean addNotification(int recipientId, int senderId, String type, String targetType, int targetId, String message) {
        // Règle essentielle : Aucune notification pour sa propre action sur son propre contenu
        if (recipientId == senderId) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("recipient_id", recipientId);
        values.put("sender_id", senderId);
        values.put("type", type);
        values.put("target_type", targetType != null ? targetType : "POST");
        values.put("target_id", targetId);
        values.put("message", message);
        values.put("is_read", 0);

        long id = db.insert("notifications", null, values);
        return id != -1;
    }

    /**
     * Récupère la liste des notifications reçues par l'utilisateur connecté.
     */
    public List<AppNotification> getNotificationsForUser(int recipientId) {
        List<AppNotification> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;

        try {
            cursor = db.rawQuery(
                    "SELECT n.id, n.recipient_id, n.sender_id, u.name AS sender_name, u.profile_image AS sender_avatar, " +
                            "n.type, n.target_type, n.target_id, n.message, n.is_read, n.created_at " +
                            "FROM notifications n " +
                            "JOIN users u ON n.sender_id = u.id " +
                            "WHERE n.recipient_id = ? " +
                            "ORDER BY n.id DESC",
                    new String[]{String.valueOf(recipientId)}
            );

            while (cursor.moveToNext()) {
                String targetType = "POST";
                int ttCol = cursor.getColumnIndex("target_type");
                if (ttCol != -1 && !cursor.isNull(ttCol)) {
                    targetType = cursor.getString(ttCol);
                }

                AppNotification notification = new AppNotification(
                        cursor.getInt(0),
                        cursor.getInt(1),
                        cursor.getInt(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        targetType,
                        cursor.getInt(7),
                        cursor.getString(8),
                        cursor.getInt(9) == 1,
                        cursor.getString(10)
                );
                list.add(notification);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return list;
    }

    /**
     * Marque toutes les notifications de l'utilisateur comme lues.
     */
    public void markAllAsRead(int recipientId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("is_read", 1);

        db.update("notifications", values, "recipient_id = ?", new String[]{String.valueOf(recipientId)});
    }

    /**
     * Reçois le nombre de notifications non lues.
     */
    public int getUnreadNotificationCount(int recipientId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND is_read = 0",
                    new String[]{String.valueOf(recipientId)}
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