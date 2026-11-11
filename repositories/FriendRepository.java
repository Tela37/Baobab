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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import androidx.annotation.NonNull;





/**
 * Repository Cloud-Native pour la gestion des amis et demandes d'amis exclusivement via Realtime Database,
 * avec persistance locale complète et synchronisation des profils.
 */
public class FriendRepository {

    private static com.google.firebase.database.ValueEventListener liveFriendRequestsListener;

    public static class FriendRequestItem {
        public int requestId;
        public int senderId;
        public int receiverId;
        public String senderName;
        public String senderProfileImage;
        public String createdAt;
    }

    public static class UserSearchItem {
        public int userId;
        public String name;
        public String profileImage;
        public boolean isFriend;
        public boolean requestSent;
    }

    private static final List<FriendRequestItem> pendingRequestsCache = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, Boolean> friendsMap = new ConcurrentHashMap<>();
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private static Context contextStatic;

    private static final String PREF_FRIENDS = "baobab_friends_pref";
    private static final String KEY_PENDING_REQUESTS_JSON = "pending_requests_json";
    private static final String KEY_FRIENDS_MAP_JSON = "friends_map_json";

    public FriendRepository(Context context) {
        this.notificationRepository = new NotificationRepository(context);
        this.userRepository = new UserRepository(context);
        if (context != null) {
            contextStatic = context.getApplicationContext();
            loadCachedFriendData(contextStatic);
        }
    }

    public static void saveCachedFriendData(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_FRIENDS, Context.MODE_PRIVATE);
            JSONArray reqArray = new JSONArray();
            synchronized (pendingRequestsCache) {
                for (FriendRequestItem req : pendingRequestsCache) {
                    JSONObject obj = new JSONObject();
                    obj.put("requestId", req.requestId);
                    obj.put("senderId", req.senderId);
                    obj.put("receiverId", req.receiverId);
                    obj.put("senderName", req.senderName);
                    obj.put("senderProfileImage", req.senderProfileImage);
                    obj.put("createdAt", req.createdAt);
                    reqArray.put(obj);
                }
            }

            JSONObject friendsObj = new JSONObject();
            for (Map.Entry<String, Boolean> entry : friendsMap.entrySet()) {
                friendsObj.put(entry.getKey(), entry.getValue());
            }

            prefs.edit()
                    .putString(KEY_PENDING_REQUESTS_JSON, reqArray.toString())
                    .putString(KEY_FRIENDS_MAP_JSON, friendsObj.toString())
                    .commit();
        } catch (Exception ignored) {}
    }

    public static void loadCachedFriendData(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_FRIENDS, Context.MODE_PRIVATE);
            String reqJson = prefs.getString(KEY_PENDING_REQUESTS_JSON, "[]");
            JSONArray reqArray = new JSONArray(reqJson);
            synchronized (pendingRequestsCache) {
                pendingRequestsCache.clear();
                for (int i = 0; i < reqArray.length(); i++) {
                    JSONObject obj = reqArray.getJSONObject(i);
                    FriendRequestItem item = new FriendRequestItem();
                    item.requestId = obj.optInt("requestId");
                    item.senderId = obj.optInt("senderId");
                    item.receiverId = obj.optInt("receiverId");
                    item.senderName = obj.optString("senderName", null);
                    item.senderProfileImage = obj.optString("senderProfileImage", null);
                    item.createdAt = obj.optString("createdAt", null);
                    pendingRequestsCache.add(item);
                }
            }

            String friendsJson = prefs.getString(KEY_FRIENDS_MAP_JSON, "{}");
            JSONObject friendsObj = new JSONObject(friendsJson);
            friendsMap.clear();
            java.util.Iterator<String> keys = friendsObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                friendsMap.put(key, friendsObj.optBoolean(key, true));
            }
        } catch (Exception ignored) {}
    }

    public static void syncFriendDataFromCloud(Runnable onComplete) {
        if (contextStatic != null) {
            loadCachedFriendData(contextStatic);
        }
        if (liveFriendRequestsListener == null) {
            liveFriendRequestsListener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    synchronized (pendingRequestsCache) {
                        pendingRequestsCache.clear();
                        for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                            FriendRequestItem item = snap.getValue(FriendRequestItem.class);
                            if (item != null) {
                                pendingRequestsCache.add(item);
                            }
                        }
                    }
                    if (contextStatic != null) {
                        saveCachedFriendData(contextStatic);
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
            FirebaseHelper.getInstance().getRealtimeDb().getReference("friend_requests")
                    .addValueEventListener(liveFriendRequestsListener);
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public List<FriendRequestItem> getPendingRequests(int receiverId) {
        if (contextStatic != null) {
            loadCachedFriendData(contextStatic);
        }
        List<FriendRequestItem> result = new ArrayList<>();
        synchronized (pendingRequestsCache) {
            for (FriendRequestItem item : pendingRequestsCache) {
                if (item.receiverId == receiverId) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    public boolean sendFriendRequest(int senderId, int receiverId) {
        if (senderId == receiverId) return false;

        FriendRequestItem item = new FriendRequestItem();
        item.requestId = Math.abs((senderId + "_" + receiverId + "_" + System.currentTimeMillis()).hashCode());
        item.senderId = senderId;
        item.receiverId = receiverId;
        item.senderName = userRepository.getUserName(senderId);
        item.senderProfileImage = userRepository.getUserProfileImage(senderId);
        item.createdAt = TimeUtils.getCurrentLocalDateTime();

        pendingRequestsCache.add(item);
        saveCachedFriendData(contextStatic);

        FirebaseHelper.getInstance().syncFriendRequestToRealtimeDb(senderId, receiverId, item);
        notificationRepository.addNotification(receiverId, senderId, "FRIEND_REQUEST", "USER", receiverId, item.senderName + " vous a envoyé une demande d'ami.");
        return true;
    }

    public boolean acceptFriendRequest(int requestId, int senderId, int receiverId) {
        pendingRequestsCache.removeIf(req -> req.requestId == requestId);
        FirebaseHelper.getInstance().removeFriendRequestFromRealtimeDb(senderId, receiverId);

        int u1 = Math.min(senderId, receiverId);
        int u2 = Math.max(senderId, receiverId);
        friendsMap.put(u1 + "_" + u2, true);
        saveCachedFriendData(contextStatic);

        if (senderId != receiverId) {
            notificationRepository.addNotification(senderId, receiverId, "FRIEND_ACCEPT", "USER", senderId, userRepository.getUserName(receiverId) + " a accepté votre demande d'ami.");
        }
        return true;
    }

    public boolean declineFriendRequest(int requestId) {
        FriendRequestItem target = null;
        synchronized (pendingRequestsCache) {
            for (FriendRequestItem req : pendingRequestsCache) {
                if (req.requestId == requestId) {
                    target = req;
                    break;
                }
            }
        }
        if (target != null) {
            pendingRequestsCache.remove(target);
            saveCachedFriendData(contextStatic);
            FirebaseHelper.getInstance().removeFriendRequestFromRealtimeDb(target.senderId, target.receiverId);
            return true;
        }
        return false;
    }

    public boolean removeFriend(int userId1, int userId2) {
        int u1 = Math.min(userId1, userId2);
        int u2 = Math.max(userId1, userId2);
        friendsMap.remove(u1 + "_" + u2);
        saveCachedFriendData(contextStatic);
        return true;
    }

    public List<User> getFriendsList(int userId, String query) {
        if (contextStatic != null) {
            loadCachedFriendData(contextStatic);
        }
        List<User> list = new ArrayList<>();
        for (User u : userRepository.getAllUsersExcept(userId)) {
            int u1 = Math.min(userId, u.getId());
            int u2 = Math.max(userId, u.getId());
            if (friendsMap.containsKey(u1 + "_" + u2)) {
                if (query == null || query.trim().isEmpty() || u.getName().toLowerCase().contains(query.trim().toLowerCase())) {
                    list.add(u);
                }
            }
        }
        return list;
    }

    public List<UserSearchItem> searchUsersToBefriend(int currentUserId, String query) {
        if (contextStatic != null) {
            loadCachedFriendData(contextStatic);
        }
        List<UserSearchItem> results = new ArrayList<>();
        for (User u : userRepository.getAllUsersExcept(currentUserId)) {
            if (query == null || query.trim().isEmpty() || u.getName().toLowerCase().contains(query.trim().toLowerCase())) {
                UserSearchItem item = new UserSearchItem();
                item.userId = u.getId();
                item.name = u.getName();
                item.profileImage = u.getProfileImage();

                int u1 = Math.min(currentUserId, u.getId());
                int u2 = Math.max(currentUserId, u.getId());
                item.isFriend = friendsMap.containsKey(u1 + "_" + u2);

                boolean requestSent = false;
                synchronized (pendingRequestsCache) {
                    for (FriendRequestItem req : pendingRequestsCache) {
                        if (req.senderId == currentUserId && req.receiverId == u.getId()) {
                            requestSent = true;
                            break;
                        }
                    }
                }
                item.requestSent = requestSent;

                results.add(item);
            }
        }
        return results;
    }

    public List<UserSearchItem> getRandomSuggestions(int currentUserId, int limit) {
        List<UserSearchItem> all = searchUsersToBefriend(currentUserId, null);
        List<UserSearchItem> notFriends = new ArrayList<>();
        for (UserSearchItem item : all) {
            if (!item.isFriend && !item.requestSent) {
                notFriends.add(item);
            }
        }
        Collections.shuffle(notFriends, new java.util.Random(System.currentTimeMillis() + System.nanoTime()));
        List<UserSearchItem> result = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, notFriends.size()); i++) {
            result.add(notFriends.get(i));
        }
        return result;
    }

    public boolean cancelFriendRequest(int senderId, int receiverId) {
        boolean removed = false;
        synchronized (pendingRequestsCache) {
            removed = pendingRequestsCache.removeIf(req -> req.senderId == senderId && req.receiverId == receiverId);
        }
        if (removed) {
            saveCachedFriendData(contextStatic);
            FirebaseHelper.getInstance().removeFriendRequestFromRealtimeDb(senderId, receiverId);
            return true;
        }
        return false;
    }
}

