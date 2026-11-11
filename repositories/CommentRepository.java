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
 * Repository Cloud-Native responsable des commentaires sur les publications via Realtime Database
 * avec persistance locale complète hors-ligne.
 */
public class CommentRepository {

    private static final Map<Integer, List<Comment>> commentsCache = new ConcurrentHashMap<>();
    private static final Map<Integer, com.google.firebase.database.ValueEventListener> commentListenersMap = new ConcurrentHashMap<>();
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Context context;
    private static Context contextStatic;

    private static final String PREF_CACHED_COMMENTS = "baobab_cached_comments_pref";

    public CommentRepository(Context context) {
        this.notificationRepository = new NotificationRepository(context);
        this.userRepository = new UserRepository(context);
        this.context = context != null ? context.getApplicationContext() : null;
        if (this.context != null) {
            contextStatic = this.context;
        }
    }

    public CommentRepository(Object unused) {
        this.notificationRepository = new NotificationRepository(null);
        this.userRepository = new UserRepository();
        this.context = null;
    }

    public static int getCommentsCountForPost(int postId) {
        List<Comment> list = commentsCache.get(postId);
        if (list == null || list.isEmpty()) {
            if (contextStatic != null) {
                List<Comment> cached = getCachedComments(contextStatic, postId);
                if (!cached.isEmpty()) {
                    commentsCache.put(postId, cached);
                    return cached.size();
                }
            }
        }
        return list != null ? list.size() : 0;
    }

    public List<Comment> getCommentsForPost(int postId, int currentUserId) {
        List<Comment> list = commentsCache.get(postId);
        if (list == null || list.isEmpty()) {
            Context ctx = context != null ? context : contextStatic;
            if (ctx != null) {
                List<Comment> cached = getCachedComments(ctx, postId);
                if (!cached.isEmpty()) {
                    commentsCache.put(postId, cached);
                    list = cached;
                }
            }
        }
        if (list == null) return new ArrayList<>();
        List<Comment> result = new ArrayList<>();
        synchronized (list) {
            for (Comment c : list) {
                enrichCommentAuthor(c);
                result.add(c);
            }
        }
        return result;
    }

    public void loadCommentsFromCloud(int postId, Runnable onComplete) {
        Context ctx = context != null ? context : contextStatic;
        com.google.firebase.database.DatabaseReference ref = FirebaseHelper.getInstance().getRealtimeDb().getReference("post_comments").child(String.valueOf(postId));

        if (!commentListenersMap.containsKey(postId)) {
            com.google.firebase.database.ValueEventListener listener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    List<Comment> localList = commentsCache.get(postId);
                    Map<Integer, Comment> localMap = new java.util.HashMap<>();
                    if (localList != null) {
                        synchronized (localList) {
                            for (Comment c : localList) {
                                localMap.put(c.getId(), c);
                            }
                        }
                    }

                    List<Comment> fetched = Collections.synchronizedList(new ArrayList<>());
                    for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                        Comment comment = snap.getValue(Comment.class);
                        if (comment != null) {
                            Comment localComment = localMap.get(comment.getId());
                            if (localComment != null) {
                                comment.setLikedByCurrentUser(localComment.isLikedByCurrentUser());
                                comment.setUserReactionType(localComment.getUserReactionType());
                                if (localComment.getLikesCount() > comment.getLikesCount()) {
                                    comment.setLikesCount(localComment.getLikesCount());
                                }
                            }
                            fetched.add(comment);
                        }
                    }
                    if (!fetched.isEmpty()) {
                        commentsCache.put(postId, fetched);
                        if (ctx != null) {
                            saveCachedComments(ctx, postId, fetched);
                        }
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
            commentListenersMap.put(postId, listener);
            ref.addValueEventListener(listener);
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public static void saveCachedComments(Context context, int postId, List<Comment> comments) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_CACHED_COMMENTS, Context.MODE_PRIVATE);
            JSONArray array = new JSONArray();
            if (comments != null) {
                synchronized (comments) {
                    for (Comment c : comments) {
                        JSONObject obj = commentToJson(c);
                        if (obj != null) {
                            array.put(obj);
                        }
                    }
                }
            }
            prefs.edit().putString("comments_post_" + postId, array.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static List<Comment> getCachedComments(Context context, int postId) {
        List<Comment> list = Collections.synchronizedList(new ArrayList<>());
        if (context == null) return list;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_CACHED_COMMENTS, Context.MODE_PRIVATE);
            String jsonStr = prefs.getString("comments_post_" + postId, "[]");
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Comment c = jsonToComment(obj);
                if (c != null) {
                    list.add(c);
                }
            }
        } catch (Exception ignored) {}
        return list;
    }

    private static JSONObject commentToJson(Comment c) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", c.getId());
            obj.put("postId", c.getPostId());
            if (c.getParentId() != null) {
                obj.put("parentId", c.getParentId().intValue());
            }
            obj.put("userId", c.getUserId());
            obj.put("authorName", c.getAuthorName());
            obj.put("authorProfileImage", c.getAuthorProfileImage());
            obj.put("content", c.getContent());
            obj.put("likesCount", c.getLikesCount());
            obj.put("likedByCurrentUser", c.isLikedByCurrentUser());
            obj.put("userReactionType", c.getUserReactionType());
            obj.put("createdAt", c.getCreatedAt());
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    private static Comment jsonToComment(JSONObject obj) {
        try {
            Integer parentId = obj.has("parentId") && !obj.isNull("parentId") ? obj.getInt("parentId") : null;
            Comment c = new Comment(
                    obj.optInt("id"),
                    obj.optInt("postId"),
                    parentId,
                    obj.optInt("userId"),
                    obj.optString("authorName", null),
                    obj.optString("authorProfileImage", null),
                    obj.optString("content", null),
                    obj.optInt("likesCount", 0),
                    obj.optBoolean("likedByCurrentUser", false),
                    obj.optString("createdAt", null)
            );
            c.setUserReactionType(obj.optString("userReactionType", null));
            return c;
        } catch (Exception e) {
            return null;
        }
    }

    private void enrichCommentAuthor(Comment comment) {
        if (comment == null) return;
        if (comment.getAuthorName() == null || comment.getAuthorName().isEmpty() || "Utilisateur".equals(comment.getAuthorName()) || comment.getAuthorProfileImage() == null) {
            td.teladoum.baobab.models.User user = userRepository.getUserById(comment.getUserId());
            if (user != null) {
                if (user.getName() != null && !user.getName().trim().isEmpty()) {
                    comment.setAuthorName(user.getName());
                }
                if (user.getProfileImage() != null && !user.getProfileImage().trim().isEmpty()) {
                    comment.setAuthorProfileImage(user.getProfileImage());
                }
            }
        }
    }

    public boolean addComment(int postId, Integer parentId, int currentUserId, String content) {
        if (content == null || content.trim().isEmpty()) return false;

        int commentId = Math.abs((postId + "_" + currentUserId + "_" + System.currentTimeMillis()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        td.teladoum.baobab.models.User user = userRepository.getUserById(currentUserId);
        String authorName = (user != null && user.getName() != null && !user.getName().trim().isEmpty()) ? user.getName() : "Utilisateur";
        String authorProfileImage = user != null ? user.getProfileImage() : null;

        Comment commentObj = new Comment();
        commentObj.setId(commentId);
        commentObj.setPostId(postId);
        commentObj.setParentId(parentId);
        commentObj.setUserId(currentUserId);
        commentObj.setAuthorName(authorName);
        commentObj.setAuthorProfileImage(authorProfileImage);
        commentObj.setContent(content);
        commentObj.setCreatedAt(createdAt);

        List<Comment> list = commentsCache.computeIfAbsent(postId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(commentObj);

        Context ctx = context != null ? context : contextStatic;
        if (ctx != null) {
            saveCachedComments(ctx, postId, list);
        }

        PostRepository.incrementPostCommentsCount(postId);

        FirebaseHelper.getInstance().syncCommentToRealtimeDb(postId, commentObj);

        String snippet = content.length() > 35 ? content.substring(0, 35) + "..." : content;
        notificationRepository.addNotification(currentUserId, currentUserId, "POST_COMMENT", "POST", postId, "Un commentaire a été ajouté : \"" + snippet + "\"");

        return true;
    }

    public boolean toggleLikeComment(int commentId, int currentUserId) {
        for (Map.Entry<Integer, List<Comment>> entry : commentsCache.entrySet()) {
            int postId = entry.getKey();
            List<Comment> list = entry.getValue();
            synchronized (list) {
                for (Comment c : list) {
                    if (c.getId() == commentId) {
                        boolean wasLiked = c.isLikedByCurrentUser() || c.getUserReactionType() != null;
                        boolean nowLiked = !wasLiked;
                        c.setLikedByCurrentUser(nowLiked);
                        c.setUserReactionType(nowLiked ? "LIKE" : null);
                        c.setLikesCount(Math.max(0, c.getLikesCount() + (nowLiked ? 1 : -1)));

                        Context ctx = context != null ? context : contextStatic;
                        if (ctx != null) {
                            saveCachedComments(ctx, postId, list);
                        }

                        FirebaseHelper.getInstance().syncCommentToRealtimeDb(postId, c);
                        FirebaseHelper.getInstance().syncCommentLikeToRealtimeDb(commentId, currentUserId);
                        return nowLiked;
                    }
                }
            }
        }
        return false;
    }

    public void setCommentReaction(int commentId, int currentUserId, String reactionType) {
        for (Map.Entry<Integer, List<Comment>> entry : commentsCache.entrySet()) {
            int postId = entry.getKey();
            List<Comment> list = entry.getValue();
            synchronized (list) {
                for (Comment c : list) {
                    if (c.getId() == commentId) {
                        boolean hadReaction = c.isLikedByCurrentUser() || c.getUserReactionType() != null;
                        c.setUserReactionType(reactionType);

                        if (reactionType != null && !reactionType.trim().isEmpty() && !"UNLIKE".equalsIgnoreCase(reactionType)) {
                            c.setLikedByCurrentUser(true);
                            if (!hadReaction) {
                                c.setLikesCount(c.getLikesCount() + 1);
                            }
                        } else {
                            c.setLikedByCurrentUser(false);
                            c.setUserReactionType(null);
                            if (hadReaction) {
                                c.setLikesCount(Math.max(0, c.getLikesCount() - 1));
                            }
                        }

                        Context ctx = context != null ? context : contextStatic;
                        if (ctx != null) {
                            saveCachedComments(ctx, postId, list);
                        }

                        FirebaseHelper.getInstance().syncCommentToRealtimeDb(postId, c);
                        FirebaseHelper.getInstance().syncCommentLikeToRealtimeDb(commentId, currentUserId);
                        return;
                    }
                }
            }
        }
    }

    public boolean reportPost(int postId, int currentUserId, String reason) {
        return true;
    }
}
