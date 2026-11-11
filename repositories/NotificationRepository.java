package td.teladoum.baobab.repositories;

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

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;






/**
 * Repository Cloud-Native pour la gestion des notifications via Realtime Database.
 */
public class NotificationRepository {

    private static final Map<Integer, List<AppNotification>> notificationsCache = new ConcurrentHashMap<>();
    private static final Map<Integer, com.google.firebase.database.ValueEventListener> notifListenersMap = new ConcurrentHashMap<>();
    private final UserRepository userRepository;

    public NotificationRepository(Context context) {
        this.userRepository = new UserRepository(context);
    }

    public boolean addNotification(int recipientId, int senderId, String type, int targetId, String message) {
        return addNotification(recipientId, senderId, type, "POST", targetId, message);
    }

    public boolean addNotification(int recipientId, int senderId, String type, String targetType, int targetId, String message) {
        if (recipientId == senderId) {
            return false;
        }

        int notifId = Math.abs((recipientId + "_" + senderId + "_" + System.currentTimeMillis()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        User sender = userRepository.getUserById(senderId);
        String senderName = (sender != null && sender.getName() != null && !sender.getName().trim().isEmpty()) ? sender.getName() : "Utilisateur";
        String senderAvatar = sender != null ? sender.getProfileImage() : null;

        AppNotification notif = new AppNotification(notifId, recipientId, senderId, senderName, senderAvatar, type, targetType, targetId, message, false, createdAt);

        List<AppNotification> list = notificationsCache.computeIfAbsent(recipientId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(notif);

        FirebaseHelper.getInstance().syncNotificationToRealtimeDb(notif);

        return true;
    }

    public List<AppNotification> getNotificationsForUser(int recipientId) {
        List<AppNotification> list = notificationsCache.get(recipientId);
        if (list == null) return new ArrayList<>();
        List<AppNotification> result = new ArrayList<>();
        synchronized (list) {
            for (AppNotification notif : list) {
                enrichNotificationSender(notif);
                result.add(notif);
            }
        }
        return result;
    }

    public void loadNotificationsFromCloud(int recipientId, Runnable onComplete) {
        com.google.firebase.database.DatabaseReference ref = FirebaseHelper.getInstance().getRealtimeDb().getReference("notifications").child(String.valueOf(recipientId));

        if (!notifListenersMap.containsKey(recipientId)) {
            com.google.firebase.database.ValueEventListener listener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    List<AppNotification> fetched = Collections.synchronizedList(new ArrayList<>());
                    for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                        AppNotification notif = snap.getValue(AppNotification.class);
                        if (notif != null) {
                            enrichNotificationSender(notif);
                            fetched.add(notif);
                        }
                    }
                    if (!fetched.isEmpty()) {
                        notificationsCache.put(recipientId, fetched);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                @Override
                public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            };
            notifListenersMap.put(recipientId, listener);
            ref.addValueEventListener(listener);
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private void enrichNotificationSender(AppNotification notif) {
        if (notif == null) return;
        if (notif.getSenderAvatar() == null || notif.getSenderAvatar().trim().isEmpty() || notif.getSenderName() == null || "Utilisateur".equalsIgnoreCase(notif.getSenderName())) {
            User sender = userRepository.getUserById(notif.getSenderId());
            if (sender != null) {
                if (sender.getName() != null && !sender.getName().trim().isEmpty()) {
                    notif.setSenderName(sender.getName());
                }
                if (sender.getProfileImage() != null && !sender.getProfileImage().trim().isEmpty()) {
                    notif.setSenderAvatar(sender.getProfileImage());
                }
            }
        }
    }

    public void markAllAsRead(int recipientId) {
        List<AppNotification> list = notificationsCache.get(recipientId);
        if (list != null) {
            synchronized (list) {
                for (AppNotification notif : list) {
                    notif.setRead(true);
                }
            }
        }
    }

    public int getUnreadNotificationCount(int recipientId) {
        List<AppNotification> list = notificationsCache.get(recipientId);
        if (list == null) return 0;
        int count = 0;
        synchronized (list) {
            for (AppNotification notif : list) {
                if (!notif.isRead()) count++;
            }
        }
        return count;
    }

    public void clearNotificationsForOtherUsers(int activeUserId) {
        notificationsCache.keySet().removeIf(id -> id != activeUserId);
    }

    public void clearNotificationsForUser(int userId) {
        notificationsCache.remove(userId);
    }
}

