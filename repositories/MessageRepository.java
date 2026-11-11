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
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;





/**
 * Repository Cloud-Native pour la gestion des messages exclusivement via Realtime Database.
 */
public class MessageRepository {

    private static final Map<Integer, List<Message>> messageCache = new ConcurrentHashMap<>();
    private final Context context;

    private static final String PREF_OFFLINE_MESSAGES = "baobab_offline_messages_pref";
    private static final String KEY_OFFLINE_MESSAGES_JSON = "offline_messages_json";

    public MessageRepository(Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }

    public MessageRepository(Object unused) {
        this.context = null;
    }

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnected();
            }
        } catch (Exception e) {
            // ignore
        }
        return true;
    }

    public boolean sendMessage(int conversationId, int senderId, String message) {
        return sendMessage(conversationId, senderId, message, null, null, null);
    }

    public boolean sendMessage(int conversationId, int senderId, String message, String imagePath) {
        return sendMessage(conversationId, senderId, message, imagePath, null, null);
    }

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

        int msgId = Math.abs((conversationId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        Message msg = new Message(
                msgId,
                conversationId,
                senderId,
                hasText ? message.trim() : "",
                imagePath,
                createdAt,
                false,
                true,
                replyToMessageId,
                replyToText,
                null,
                false
        );

        List<Message> list = messageCache.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(msg);

        if (isNetworkAvailable(context)) {
            try {
                FirebaseHelper.getInstance().syncMessageToRealtimeDb(conversationId, msg);
            } catch (Exception e) {
                saveOfflineMessage(context, msg);
            }
        } else {
            saveOfflineMessage(context, msg);
        }

        return true;
    }

    public boolean sendVideoMessage(int conversationId, int senderId, String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) return false;
        int msgId = Math.abs((conversationId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        Message msg = new Message(msgId, conversationId, senderId, "", createdAt);
        msg.setVideoPath(videoPath);

        List<Message> list = messageCache.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(msg);

        if (isNetworkAvailable(context)) {
            try {
                FirebaseHelper.getInstance().syncMessageToRealtimeDb(conversationId, msg);
            } catch (Exception e) {
                saveOfflineMessage(context, msg);
            }
        } else {
            saveOfflineMessage(context, msg);
        }

        return true;
    }

    public boolean sendAudioMessage(int conversationId, int senderId, String audioPath) {
        if (audioPath == null || audioPath.trim().isEmpty()) return false;
        int msgId = Math.abs((conversationId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        Message msg = new Message(msgId, conversationId, senderId, "", createdAt);
        msg.setAudioPath(audioPath);

        List<Message> list = messageCache.computeIfAbsent(conversationId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(msg);

        if (isNetworkAvailable(context)) {
            try {
                FirebaseHelper.getInstance().syncMessageToRealtimeDb(conversationId, msg);
            } catch (Exception e) {
                saveOfflineMessage(context, msg);
            }
        } else {
            saveOfflineMessage(context, msg);
        }

        return true;
    }

    public static void saveOfflineMessage(Context context, Message msg) {
        if (context == null || msg == null) return;
        try {
            List<Message> list = getOfflinePendingMessages(context);
            list.add(msg);
            saveAllOfflineMessages(context, list);
        } catch (Exception ignored) {}
    }

    public static List<Message> getOfflinePendingMessages(Context context) {
        List<Message> list = new ArrayList<>();
        if (context == null) return list;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_OFFLINE_MESSAGES, Context.MODE_PRIVATE);
            String jsonStr = prefs.getString(KEY_OFFLINE_MESSAGES_JSON, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Message m = jsonToMessage(obj);
                if (m != null) {
                    list.add(m);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void saveAllOfflineMessages(Context context, List<Message> messages) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_OFFLINE_MESSAGES, Context.MODE_PRIVATE);
            JSONArray array = new JSONArray();
            if (messages != null) {
                for (Message m : messages) {
                    JSONObject obj = messageToJson(m);
                    if (obj != null) {
                        array.put(obj);
                    }
                }
            }
            prefs.edit().putString(KEY_OFFLINE_MESSAGES_JSON, array.toString()).commit();
        } catch (Exception ignored) {}
    }

    public static int syncPendingMessages(Context context) {
        if (context == null || !isNetworkAvailable(context)) return 0;
        List<Message> pending = getOfflinePendingMessages(context);
        if (pending.isEmpty()) return 0;

        int syncedCount = 0;
        List<Message> remaining = new ArrayList<>();
        for (Message msg : pending) {
            try {
                FirebaseHelper.getInstance().syncMessageToRealtimeDb(msg.getConversationId(), msg);

                List<Message> list = messageCache.computeIfAbsent(msg.getConversationId(), k -> Collections.synchronizedList(new ArrayList<>()));
                if (!list.contains(msg)) {
                    list.add(msg);
                }
                syncedCount++;
            } catch (Exception e) {
                remaining.add(msg);
            }
        }
        saveAllOfflineMessages(context, remaining);
        return syncedCount;
    }

    private static JSONObject messageToJson(Message m) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", m.getId());
            obj.put("conversationId", m.getConversationId());
            obj.put("senderId", m.getSenderId());
            obj.put("message", m.getMessage());
            obj.put("imagePath", m.getImagePath());
            obj.put("videoPath", m.getVideoPath());
            obj.put("audioPath", m.getAudioPath());
            obj.put("createdAt", m.getCreatedAt());
            obj.put("isRead", m.isRead());
            obj.put("isDelivered", m.isDelivered());
            obj.put("isDeleted", m.isDeleted());
            if (m.getReplyToMessageId() != null) {
                obj.put("replyToMessageId", m.getReplyToMessageId());
            }
            obj.put("replyToText", m.getReplyToText());
            obj.put("reaction", m.getReaction());
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static Message jsonToMessage(JSONObject obj) {
        try {
            Message m = new Message();
            m.setId(obj.optInt("id"));
            m.setConversationId(obj.optInt("conversationId"));
            m.setSenderId(obj.optInt("senderId"));
            m.setMessage(obj.optString("message", ""));
            m.setImagePath(obj.optString("imagePath", null));
            m.setVideoPath(obj.optString("videoPath", null));
            m.setAudioPath(obj.optString("audioPath", null));
            m.setCreatedAt(obj.optString("createdAt", ""));
            m.setRead(obj.optBoolean("isRead", false));
            m.setDelivered(obj.optBoolean("isDelivered", false));
            m.setDeleted(obj.optBoolean("isDeleted", false));
            if (obj.has("replyToMessageId") && !obj.isNull("replyToMessageId")) {
                m.setReplyToMessageId(obj.getInt("replyToMessageId"));
            }
            m.setReplyToText(obj.optString("replyToText", null));
            m.setReaction(obj.optString("reaction", null));
            return m;
        } catch (Exception e) {
            return null;
        }
    }

    private static final Map<Integer, com.google.firebase.database.ValueEventListener> messageListenersMap = new ConcurrentHashMap<>();

    public void loadMessagesFromCloud(int conversationId, Runnable onComplete) {
        if (conversationId <= 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        com.google.firebase.database.DatabaseReference ref = FirebaseHelper.getInstance().getRealtimeDb()
                .getReference("messages")
                .child(String.valueOf(conversationId));

        if (!messageListenersMap.containsKey(conversationId)) {
            com.google.firebase.database.ValueEventListener listener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    List<Message> fetched = Collections.synchronizedList(new ArrayList<>());
                    for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                        Message msg = snap.getValue(Message.class);
                        if (msg != null) {
                            fetched.add(msg);
                        }
                    }
                    messageCache.put(conversationId, fetched);
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                @Override
                public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            };
            messageListenersMap.put(conversationId, listener);
            ref.addValueEventListener(listener);
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public List<Message> getMessages(int conversationId) {
        List<Message> list = messageCache.get(conversationId);
        if (list == null) return new ArrayList<>();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }

    public boolean setMessageReaction(int messageId, int userId, String reaction) {
        for (List<Message> list : messageCache.values()) {
            synchronized (list) {
                for (Message msg : list) {
                    if (msg.getId() == messageId) {
                        msg.setReaction(reaction);
                        if (isNetworkAvailable(context)) {
                            FirebaseHelper.getInstance().syncMessageReactionToRealtimeDb(messageId, userId, reaction);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean setMessageReaction(int messageId, String reaction) {
        return setMessageReaction(messageId, 0, reaction);
    }

    public boolean deleteMessage(int messageId) {
        for (List<Message> list : messageCache.values()) {
            synchronized (list) {
                for (Message msg : list) {
                    if (msg.getId() == messageId) {
                        if (!TimeUtils.isWithinHours(msg.getCreatedAt(), 10)) {
                            return false;
                        }
                        msg.setDeleted(true);
                        msg.setMessage("Ce message a été supprimé 🚫");
                        msg.setImagePath(null);
                        msg.setVideoPath(null);
                        msg.setAudioPath(null);
                        if (isNetworkAvailable(context)) {
                            FirebaseHelper.getInstance().syncMessageToRealtimeDb(msg.getConversationId(), msg);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void markMessagesAsRead(int conversationId, int currentUserId) {
        List<Message> list = messageCache.get(conversationId);
        if (list != null) {
            synchronized (list) {
                for (Message msg : list) {
                    if (msg.getSenderId() != currentUserId) {
                        msg.setRead(true);
                    }
                }
            }
        }
    }

    public String getLastMessage(int conversationId) {
        List<Message> list = messageCache.get(conversationId);
        if (list == null || list.isEmpty()) return "Aucun message";
        synchronized (list) {
            Message last = list.get(list.size() - 1);
            if (last.isDeleted()) return "Ce message a été supprimé 🚫";
            if (last.getImagePath() != null && !last.getImagePath().isEmpty()) return "📷 Photo";
            return last.getMessage() != null ? last.getMessage() : "Aucun message";
        }
    }

    public boolean clearConversationMessages(int conversationId) {
        messageCache.remove(conversationId);
        return true;
    }

    public boolean updateMessage(int messageId, String newText) {
        if (newText == null || newText.trim().isEmpty()) return false;
        for (List<Message> list : messageCache.values()) {
            synchronized (list) {
                for (Message msg : list) {
                    if (msg.getId() == messageId) {
                        if (!TimeUtils.isWithinHours(msg.getCreatedAt(), 1)) {
                            return false;
                        }
                        msg.setMessage(newText.trim());
                        if (isNetworkAvailable(context)) {
                            FirebaseHelper.getInstance().syncMessageToRealtimeDb(msg.getConversationId(), msg);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public int getUnreadCount(int conversationId, int currentUserId) {
        List<Message> list = messageCache.get(conversationId);
        if (list == null) return 0;
        int unread = 0;
        synchronized (list) {
            for (Message msg : list) {
                if (msg.getSenderId() != currentUserId && !msg.isRead() && !msg.isDeleted()) {
                    unread++;
                }
            }
        }
        return unread;
    }
}

