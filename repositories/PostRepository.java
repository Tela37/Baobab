package td.teladoum.baobab.repositories;

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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;



import androidx.annotation.NonNull;

import td.teladoum.baobab.R;


/**
 * Repository Cloud-Native pour la gestion des publications (posts) exclusivement via Realtime Database,
 * avec persistance locale complète (cache de flux persistant hors-ligne).
 */
public class PostRepository {

    private static final Map<Integer, Post> postsCache = new ConcurrentHashMap<>();
    private static final Map<String, String> userReactionsMap = new ConcurrentHashMap<>();
    private static final List<ReportItem> reportsCache = Collections.synchronizedList(new ArrayList<>());
    private static com.google.firebase.database.ValueEventListener livePostsListener;
    private static com.google.firebase.database.ValueEventListener liveReactionsListener;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Context context;
    private static Context contextStatic;

    private static final String PREF_OFFLINE_POSTS = "baobab_offline_posts_pref";
    private static final String KEY_OFFLINE_POSTS_JSON = "offline_posts_json";

    private static final String PREF_CACHED_FEED = "baobab_cached_feed_pref";
    private static final String KEY_CACHED_FEED_JSON = "cached_feed_json";

    public PostRepository(Context context) {
        this.notificationRepository = new NotificationRepository(context);
        this.userRepository = new UserRepository(context);
        this.context = context != null ? context.getApplicationContext() : null;
        if (this.context != null) {
            contextStatic = this.context;
        }
    }

    public PostRepository(Object unused) {
        this.notificationRepository = new NotificationRepository(null);
        this.userRepository = new UserRepository();
        this.context = null;
    }

    public boolean createPost(int userId, String content, String imagePath) {
        return createPost(userId, content, imagePath, null);
    }

    public boolean createPost(int userId, String content, String imagePath, String videoPath) {
        return createPost(userId, content, imagePath, videoPath, false);
    }

    public boolean createPost(int userId, String content, String imagePath, String videoPath, boolean isSensitive) {
        if (isUserRestricted(userId)) {
            return false;
        }

        int id = Math.abs((userId + "_" + System.currentTimeMillis() + "_" + Math.random()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();
        String authorName = getUserName(userId);
        String authorProfileImage = getUserProfileImage(userId);

        Post post = new Post(id, userId, authorName, authorProfileImage, content, imagePath, videoPath, 0, false, createdAt);
        post.setSensitive(isSensitive);

        postsCache.put(id, post);
        saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));

        if (isNetworkAvailable(context)) {
            try {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
            } catch (Exception e) {
                saveOfflinePost(context, post);
                if (context != null) {
                    es.dmoral.toasty.Toasty.info(context, context.getString(R.string.toast_post_saved_offline), es.dmoral.toasty.Toasty.LENGTH_LONG).show();
                }
            }
        } else {
            saveOfflinePost(context, post);
            if (context != null) {
                es.dmoral.toasty.Toasty.info(context, context.getString(R.string.toast_post_saved_offline), es.dmoral.toasty.Toasty.LENGTH_LONG).show();
            }
        }

        return true;
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

    public static void saveOfflinePost(Context context, Post post) {
        if (context == null || post == null) return;
        try {
            List<Post> list = getOfflinePendingPosts(context);
            boolean exists = false;
            for (Post p : list) {
                if (p.getId() == post.getId()) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                list.add(post);
                saveAllOfflinePosts(context, list);
            }
        } catch (Exception ignored) {}
    }

    public static List<Post> getOfflinePendingPosts(Context context) {
        List<Post> list = new ArrayList<>();
        if (context == null) return list;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_OFFLINE_POSTS, Context.MODE_PRIVATE);
            String jsonStr = prefs.getString(KEY_OFFLINE_POSTS_JSON, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Post p = jsonToPost(obj);
                if (p != null) {
                    list.add(p);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static void saveAllOfflinePosts(Context context, List<Post> posts) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_OFFLINE_POSTS, Context.MODE_PRIVATE);
            JSONArray array = new JSONArray();
            if (posts != null) {
                for (Post p : posts) {
                    JSONObject obj = postToJson(p);
                    if (obj != null) {
                        array.put(obj);
                    }
                }
            }
            prefs.edit().putString(KEY_OFFLINE_POSTS_JSON, array.toString()).commit();
        } catch (Exception ignored) {}
    }

    public static void saveCachedFeedPosts(Context context, List<Post> posts) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_CACHED_FEED, Context.MODE_PRIVATE);
            JSONArray array = new JSONArray();
            if (posts != null) {
                for (Post p : posts) {
                    JSONObject obj = postToJson(p);
                    if (obj != null) {
                        array.put(obj);
                    }
                }
            }
            prefs.edit().putString(KEY_CACHED_FEED_JSON, array.toString()).commit();
        } catch (Exception ignored) {}
    }

    public static List<Post> getCachedFeedPosts(Context context) {
        List<Post> list = new ArrayList<>();
        if (context == null) return list;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_CACHED_FEED, Context.MODE_PRIVATE);
            String jsonStr = prefs.getString(KEY_CACHED_FEED_JSON, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Post p = jsonToPost(obj);
                if (p != null) {
                    list.add(p);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    public static int syncPendingPosts(Context context) {
        if (context == null || !isNetworkAvailable(context)) return 0;
        List<Post> pending = getOfflinePendingPosts(context);
        if (pending.isEmpty()) return 0;

        int syncedCount = 0;
        List<Post> remaining = new ArrayList<>();
        for (Post post : pending) {
            try {
                if (!postsCache.containsKey(post.getId())) {
                    FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
                    postsCache.put(post.getId(), post);
                    syncedCount++;
                }
            } catch (Exception e) {
                remaining.add(post);
            }
        }
        saveAllOfflinePosts(context, remaining);
        if (!postsCache.isEmpty()) {
            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));
        }
        return syncedCount;
    }

    public static void loadAllPostsFromCloud(Runnable onComplete) {
        loadAllPostsFromCloud(contextStatic, onComplete);
    }

    public static void loadAllPostsFromCloud(Context context, Runnable onComplete) {
        // 1. Load local persistent feed cache immediately for offline viewing
        Context ctx = context != null ? context : contextStatic;
        if (ctx != null) {
            List<Post> cached = getCachedFeedPosts(ctx);
            for (Post p : cached) {
                postsCache.put(p.getId(), p);
            }
        }

        UserRepository userRepo = new UserRepository();
        userRepo.syncAllUsersFromCloud(() -> {
            if (liveReactionsListener == null) {
                liveReactionsListener = new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        for (com.google.firebase.database.DataSnapshot pSnap : snapshot.getChildren()) {
                            String pId = pSnap.getKey();
                            for (com.google.firebase.database.DataSnapshot uSnap : pSnap.getChildren()) {
                                String uId = uSnap.getKey();
                                String rType = uSnap.getValue(String.class);
                                if (pId != null && uId != null && rType != null) {
                                    userReactionsMap.put(pId + "_" + uId, rType);
                                }
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                };
                FirebaseHelper.getInstance().getRealtimeDb().getReference("post_reactions")
                        .addValueEventListener(liveReactionsListener);
            }

            if (livePostsListener == null) {
                livePostsListener = new com.google.firebase.database.ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                        List<Post> fetched = new ArrayList<>();
                        for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                            Post post = snap.getValue(Post.class);
                            if (post != null) {
                                postsCache.put(post.getId(), post);
                                fetched.add(post);
                            }
                        }
                        if (ctx != null && !fetched.isEmpty()) {
                            saveCachedFeedPosts(ctx, fetched);
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
                FirebaseHelper.getInstance().getRealtimeDb().getReference("posts")
                        .addValueEventListener(livePostsListener);
            } else {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private static JSONObject postToJson(Post p) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", p.getId());
            obj.put("userId", p.getUserId());
            obj.put("authorName", p.getAuthorName());
            obj.put("authorProfileImage", p.getAuthorProfileImage());
            obj.put("content", p.getContent());
            obj.put("imagePath", p.getImagePath());
            obj.put("videoPath", p.getVideoPath());
            obj.put("likesCount", p.getLikesCount());
            obj.put("likedByCurrentUser", p.isLikedByCurrentUser());
            obj.put("createdAt", p.getCreatedAt());
            obj.put("isSensitive", p.isSensitive());
            obj.put("userReactionType", p.getUserReactionType());
            obj.put("sharesCount", p.getSharesCount());
            obj.put("commentsCount", p.getCommentsCount());
            obj.put("isSavedByCurrentUser", p.isSavedByCurrentUser());
            obj.put("isPinned", p.isPinned());
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static Post jsonToPost(JSONObject obj) {
        try {
            Post p = new Post(
                    obj.optInt("id"),
                    obj.optInt("userId"),
                    obj.optString("authorName", null),
                    obj.optString("authorProfileImage", null),
                    obj.optString("content", null),
                    obj.optString("imagePath", null),
                    obj.optString("videoPath", null),
                    obj.optInt("likesCount", 0),
                    obj.optBoolean("likedByCurrentUser", false),
                    obj.optString("createdAt", null)
            );
            p.setSensitive(obj.optBoolean("isSensitive", false));
            p.setUserReactionType(obj.optString("userReactionType", null));
            p.setSharesCount(obj.optInt("sharesCount", 0));
            p.setCommentsCount(obj.optInt("commentsCount", 0));
            p.setSavedByCurrentUser(obj.optBoolean("isSavedByCurrentUser", false));
            p.setPinned(obj.optBoolean("isPinned", false));
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    public List<Post> getAllPosts(int currentUserId) {
        Map<Integer, Post> map = new LinkedHashMap<>();
        List<Post> list = new ArrayList<>(postsCache.values());
        for (Post post : list) {
            enrichPostAuthorData(post);
            if (currentUserId > 0) {
                String key = post.getId() + "_" + currentUserId;
                String reaction = userReactionsMap.get(key);
                if (reaction != null && !"UNLIKE".equalsIgnoreCase(reaction)) {
                    post.setLikedByCurrentUser(true);
                    post.setUserReactionType(reaction);
                } else if ("UNLIKE".equalsIgnoreCase(reaction)) {
                    post.setLikedByCurrentUser(false);
                    post.setUserReactionType(null);
                }
            }
            map.put(post.getId(), post);
        }
        List<Post> uniqueList = new ArrayList<>(map.values());
        Collections.sort(uniqueList, (p1, p2) -> {
            String t1 = p1.getCreatedAt() != null ? p1.getCreatedAt() : "";
            String t2 = p2.getCreatedAt() != null ? p2.getCreatedAt() : "";
            return -t1.compareTo(t2);
        });
        return uniqueList;
    }

    public List<Post> getPostsByUserId(int targetUserId, int currentUserId) {
        List<Post> result = new ArrayList<>();
        for (Post p : getAllPosts(currentUserId)) {
            if (p.getUserId() == targetUserId) {
                result.add(p);
            }
        }
        return result;
    }

    public static int getLikesCountForPost(int postId) {
        int count = 0;
        String prefix = postId + "_";
        for (Map.Entry<String, String> entry : userReactionsMap.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String val = entry.getValue();
                if (val != null && !"UNLIKE".equalsIgnoreCase(val)) {
                    count++;
                }
            }
        }
        return count;
    }

    private void enrichPostAuthorData(Post post) {
        if (post == null) return;
        td.teladoum.baobab.models.User user = userRepository.getUserById(post.getUserId());
        if (user != null) {
            if (user.getName() != null && !user.getName().trim().isEmpty()) {
                post.setAuthorName(user.getName());
            }
            if (user.getProfileImage() != null && !user.getProfileImage().trim().isEmpty()) {
                post.setAuthorProfileImage(user.getProfileImage());
            }
        }
        int commentCount = CommentRepository.getCommentsCountForPost(post.getId());
        if (commentCount > post.getCommentsCount()) {
            post.setCommentsCount(commentCount);
        }
        int realLikesCount = getLikesCountForPost(post.getId());
        if (realLikesCount > post.getLikesCount()) {
            post.setLikesCount(realLikesCount);
        }
    }

    public static void incrementPostCommentsCount(int postId) {
        Post post = postsCache.get(postId);
        if (post != null) {
            post.setCommentsCount(post.getCommentsCount() + 1);
            if (isNetworkAvailable(contextStatic)) {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
            }
        }
    }

    public boolean updatePost(int postId, int userId, String newText, String imagePath) {
        Post post = postsCache.get(postId);
        if (post != null && post.getUserId() == userId) {
            post.setContent(newText);
            if (imagePath != null) post.setImagePath(imagePath);
            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));
            if (isNetworkAvailable(context)) {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
            }
            return true;
        }
        return false;
    }

    public boolean deletePost(int postId, int userId) {
        Post post = postsCache.get(postId);
        if (post != null && post.getUserId() == userId) {
            postsCache.remove(postId);
            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));
            if (isNetworkAvailable(context)) {
                FirebaseHelper.getInstance().deletePostFromCloud(postId);
            }
            return true;
        }
        return false;
    }

    public boolean toggleLikePost(int postId, int userId) {
        Post post = postsCache.get(postId);
        if (post != null) {
            String key = postId + "_" + userId;
            String currentReaction = userReactionsMap.get(key);
            boolean wasLiked = (currentReaction != null && !"UNLIKE".equalsIgnoreCase(currentReaction)) || post.isLikedByCurrentUser();
            boolean nowLiked = !wasLiked;

            post.setLikedByCurrentUser(nowLiked);
            post.setUserReactionType(nowLiked ? "LIKE" : null);
            userReactionsMap.put(key, nowLiked ? "LIKE" : "UNLIKE");

            int realCount = getLikesCountForPost(postId);
            post.setLikesCount(realCount);

            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));

            if (isNetworkAvailable(context)) {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
                FirebaseHelper.getInstance().syncPostReactionToRealtimeDb(postId, userId, nowLiked ? "LIKE" : "UNLIKE");
            }

            if (nowLiked && post.getUserId() != userId) {
                notificationRepository.addNotification(post.getUserId(), userId, "POST_LIKE", "POST", postId, getUserName(userId) + " a aimé votre publication.");
            }
            return nowLiked;
        }
        return false;
    }

    public void setPostReaction(int postId, int userId, String reactionType) {
        Post post = postsCache.get(postId);
        if (post != null) {
            String key = postId + "_" + userId;
            boolean isPositiveReaction = reactionType != null && !reactionType.trim().isEmpty() && !"UNLIKE".equalsIgnoreCase(reactionType);

            if (isPositiveReaction) {
                post.setLikedByCurrentUser(true);
                post.setUserReactionType(reactionType);
                userReactionsMap.put(key, reactionType);
                if (isNetworkAvailable(context)) {
                    FirebaseHelper.getInstance().syncPostReactionToRealtimeDb(postId, userId, reactionType);
                }
                if (post.getUserId() != userId) {
                    notificationRepository.addNotification(post.getUserId(), userId, "POST_REACTION", "POST", postId, getUserName(userId) + " a réagi à votre publication.");
                }
            } else {
                post.setLikedByCurrentUser(false);
                post.setUserReactionType(null);
                userReactionsMap.put(key, "UNLIKE");
                if (isNetworkAvailable(context)) {
                    FirebaseHelper.getInstance().syncPostReactionToRealtimeDb(postId, userId, "UNLIKE");
                }
            }

            int realCount = getLikesCountForPost(postId);
            post.setLikesCount(realCount);

            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));

            if (isNetworkAvailable(context)) {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
            }
        }
    }

    public void recordPostShare(int postId, int userId) {
        Post post = postsCache.get(postId);
        if (post != null) {
            post.setSharesCount(post.getSharesCount() + 1);
        }
    }

    public boolean toggleSavePost(int postId, int userId) {
        Post post = postsCache.get(postId);
        if (post != null) {
            boolean wasSaved = post.isSavedByCurrentUser();
            post.setSavedByCurrentUser(!wasSaved);
            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));
            if (isNetworkAvailable(context)) {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
            }
            return !wasSaved;
        }
        return false;
    }

    public List<Post> getSavedPosts(int currentUserId) {
        List<Post> saved = new ArrayList<>();
        for (Post p : getAllPosts(currentUserId)) {
            if (p.isSavedByCurrentUser()) {
                saved.add(p);
            }
        }
        return saved;
    }

    public boolean togglePinPost(int postId, int userId) {
        Post post = postsCache.get(postId);
        if (post != null && post.getUserId() == userId) {
            boolean wasPinned = post.isPinned();
            post.setPinned(!wasPinned);
            saveCachedFeedPosts(context, new ArrayList<>(postsCache.values()));
            if (isNetworkAvailable(context)) {
                FirebaseHelper.getInstance().syncPostToRealtimeDb(post);
            }
            return !wasPinned;
        }
        return false;
    }

    public boolean reportPost(int postId, int reporterId, String reason) {
        ReportItem item = new ReportItem();
        item.reportId = Math.abs((postId + "_" + reporterId + "_" + System.currentTimeMillis()).hashCode());
        item.postId = postId;
        item.reporterId = reporterId;
        item.reporterName = getUserName(reporterId);
        item.reason = reason != null ? reason : "Contenu inapproprié";
        item.createdAt = TimeUtils.getCurrentLocalDateTime();

        Post p = postsCache.get(postId);
        if (p != null) {
            item.postContent = p.getContent();
            item.imagePath = p.getImagePath();
            item.videoPath = p.getVideoPath();
        }

        reportsCache.add(item);
        return true;
    }

    public static class ReportItem {
        public int reportId;
        public int postId;
        public int reporterId;
        public String reporterName;
        public String postContent;
        public String reason;
        public String createdAt;
        public String imagePath;
        public String videoPath;
    }

    public List<ReportItem> getReports() {
        return new ArrayList<>(reportsCache);
    }

    public boolean deleteReport(int reportId) {
        return reportsCache.removeIf(item -> item.reportId == reportId);
    }

    private String getUserName(int userId) {
        td.teladoum.baobab.models.User user = userRepository.getUserById(userId);
        return (user != null && user.getName() != null && !user.getName().trim().isEmpty()) ? user.getName() : "Utilisateur";
    }

    private String getUserProfileImage(int userId) {
        td.teladoum.baobab.models.User user = userRepository.getUserById(userId);
        return user != null ? user.getProfileImage() : null;
    }

    public boolean isUserRestricted(int userId) {
        return false;
    }
}

