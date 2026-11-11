package td.teladoum.baobab.services;

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

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Service de gestion des notifications push Firebase Cloud Messaging (FCM).
 * Reçoit les messages en arrière-plan et déclenche l'affichage des notifications locales.
 */
public class FCMService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String notifTitle = "BaobabTD";
        String notifBody = "Nouveau message reçu";
        int conversationId = 0;
        String senderName = "Utilisateur";

        RemoteMessage.Notification notification = remoteMessage.getNotification();
        if (notification != null) {
            if (notification.getTitle() != null) {
                notifTitle = notification.getTitle();
            }
            if (notification.getBody() != null) {
                notifBody = notification.getBody();
            }
        }

        Map<String, String> data = remoteMessage.getData();
        if (!data.isEmpty()) {
            String titleVal = data.get("title");
            if (titleVal != null) {
                notifTitle = titleVal;
            }
            String bodyVal = data.get("body");
            if (bodyVal != null) {
                notifBody = bodyVal;
            }
            String convIdStr = data.get("conversation_id");
            if (convIdStr != null) {
                try {
                    conversationId = Integer.parseInt(convIdStr);
                } catch (NumberFormatException ignored) {}
            }
            String senderVal = data.get("sender_name");
            if (senderVal != null) {
                senderName = senderVal;
            }
        }

        NotificationHelper.showNotification(this, notifTitle, notifBody, conversationId, senderName);
    }
}

