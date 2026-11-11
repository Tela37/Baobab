package td.teladoum.baobab.notifications;

import td.teladoum.baobab.R;

import td.teladoum.baobab.models.*;
import td.teladoum.baobab.adapters.*;
import td.teladoum.baobab.repositories.*;
import td.teladoum.baobab.services.*;
import td.teladoum.baobab.firebase.*;
import td.teladoum.baobab.helpers.*;
import td.teladoum.baobab.notifications.*;
import td.teladoum.baobab.utils.*;
import td.teladoum.baobab.activities.*;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    public static final String CHANNEL_ID = "chat_messages_channel";
    public static final String CHANNEL_NAME = "Messages de chat";
    private static final int BADGE_NOTIF_ID = 99991;

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications de nouveaux messages reçus");
            channel.setShowBadge(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void updateAppBadge(Context context, int totalUnreadCount) {
        if (context == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        createNotificationChannel(context);
        NotificationManagerCompat manager = NotificationManagerCompat.from(context);

        if (totalUnreadCount <= 0) {
            try {
                manager.cancel(BADGE_NOTIF_ID);
            } catch (Exception ignored) {}
            return;
        }

        Intent intent = new Intent(context, AcceuilActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.chaticon)
                .setContentTitle("Baobab TD")
                .setContentText(totalUnreadCount + " élément(s) non lu(s)")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setNumber(totalUnreadCount)
                .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            manager.notify(BADGE_NOTIF_ID, builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void showNotification(Context context,
                                        String title,
                                        String body,
                                        int conversationId,
                                        String receiverName) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        createNotificationChannel(context);

        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra("conversation_id", conversationId);
        intent.putExtra("receiver_name", receiverName);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                conversationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.chaticon)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        try {
            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            manager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

