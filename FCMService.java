package td.teladoumbaobabtd;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Service d'arrière-plan Firebase Cloud Messaging (FCM).
 * Reçoit les notifications Push entrantes même lorsque l'application est fermée ou en arrière-plan.
 */
public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "Nouveau message";
        String body = "";
        int conversationId = -1;
        String senderName = "Ami";

        // Extraction depuis les données de notification
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }

        // Extraction depuis les données payload personnalisées
        Map<String, String> data = remoteMessage.getData();
        if (data.containsKey("conversation_id")) {
            try {
                conversationId = Integer.parseInt(data.get("conversation_id"));
            } catch (NumberFormatException ignored) {
            }
        }
        if (data.containsKey("sender_name")) {
            senderName = data.get("sender_name");
        }

        // Affichage de la notification locale via NotificationHelper
        NotificationHelper.showNotification(
                this,
                title,
                body,
                conversationId,
                senderName
        );
    }

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.d(TAG, "Nouveau jeton FCM généré: " + token);
    }
}