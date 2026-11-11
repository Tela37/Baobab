package td.teladoum.baobab.firebase;

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

import android.net.Uri;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

/**
 * Assistant Firebase gérant l'authentification, le stockage, Firestore (exclusivement pour les comptes utilisateurs)
 * et Realtime Database (pour les comptes et toutes les autres données : posts, messages, notifications, stories, etc.).
 */
public class FirebaseHelper {

    public interface OnUploadCompleteListener {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    private static FirebaseHelper instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final FirebaseDatabase realtimeDb;
    private final FirebaseStorage storage;

    private FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        realtimeDb = FirebaseDatabase.getInstance();
        storage = FirebaseStorage.getInstance();
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public FirebaseDatabase getRealtimeDb() {
        return realtimeDb;
    }

    public FirebaseStorage getStorage() {
        return storage;
    }

    public void loginUser(String email, String password, OnCompleteListener<AuthResult> listener) {
        if (email == null || password == null) return;
        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener(listener);
    }

    public void registerUser(String email, String password, OnCompleteListener<AuthResult> listener) {
        if (email == null || password == null) return;
        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(listener);
    }

    public void uploadMedia(String folder, Uri uri, OnUploadCompleteListener listener) {
        if (uri == null) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("Uri is null"));
            return;
        }
        String fileName = folder + "/" + System.currentTimeMillis() + "_" + uri.getLastPathSegment();
        StorageReference ref = storage.getReference().child(fileName);
        ref.putFile(uri).continueWithTask(task -> {
            if (!task.isSuccessful() && task.getException() != null) {
                throw task.getException();
            }
            return ref.getDownloadUrl();
        }).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                if (listener != null) listener.onSuccess(task.getResult().toString());
            } else {
                if (listener != null) listener.onFailure(task.getException());
            }
        });
    }

    public ListenerRegistration listenToMessages(int conversationId, EventListener<QuerySnapshot> listener) {
        return firestore.collection("conversations")
                .document(String.valueOf(conversationId))
                .collection("messages")
                .orderBy("createdAt")
                .addSnapshotListener(listener);
    }

    public void saveRewardBadgeToFirestore(int userId, int activeBadges, long timestamp) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("activeBadges", activeBadges);
        map.put("timestamp", timestamp);
        firestore.collection("rewards").document(userId + "_" + timestamp).set(map);
    }

    // --- USER ACCOUNTS (Firestore + Realtime DB copy) ---

    public void syncUserProfileToFirestore(User user) {
        if (user == null) return;
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getName());
        map.put("first_name", user.getFirstName());
        map.put("last_name", user.getLastName());
        map.put("email", user.getEmail());
        map.put("password", user.getPassword());
        map.put("phone", user.getPhone());
        map.put("dob", user.getDob());
        map.put("neighborhood", user.getNeighborhood());
        map.put("city", user.getCity());
        map.put("country", user.getCountry());
        map.put("profile_image", user.getProfileImage());
        map.put("profession", user.getProfession());
        map.put("relationship_status", user.getRelationshipStatus());
        map.put("gender", user.getGender());
        map.put("bio", user.getBio());
        map.put("is_admin", user.isAdmin());

        if (user.getEmail() != null) {
            firestore.collection("users").document(String.valueOf(user.getId())).set(map)
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() && task.getException() != null) {
                            android.util.Log.e("FirebaseHelper", "Firestore user sync failed: " + task.getException().getMessage());
                        }
                    });
        }
    }

    public void syncUserToRealtimeDb(User user) {
        if (user == null) return;
        DatabaseReference ref = realtimeDb.getReference("users").child(String.valueOf(user.getId()));
        ref.setValue(user).addOnCompleteListener(task -> {
            if (!task.isSuccessful() && task.getException() != null) {
                android.util.Log.e("FirebaseHelper", "RealtimeDB user sync failed: " + task.getException().getMessage());
            }
        });
    }

    public void deleteUserDataFromCloud(int userId) {
        firestore.collection("users").document(String.valueOf(userId)).delete();
        realtimeDb.getReference("users").child(String.valueOf(userId)).removeValue();
    }

    public void syncBlockToRealtimeDb(int blockerId, int blockedId) {
        realtimeDb.getReference("blocks").child(String.valueOf(blockerId)).child(String.valueOf(blockedId)).setValue(true);
    }

    public void syncUserRewardToRealtimeDb(int userId, String rewardType) {
        realtimeDb.getReference("rewards").child(String.valueOf(userId)).push().setValue(rewardType);
    }

    // --- ALL OTHER DATA (Realtime Database exclusively) ---

    public void syncPostToRealtimeDb(Post post) {
        if (post == null) return;
        realtimeDb.getReference("posts").child(String.valueOf(post.getId())).setValue(post)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        android.util.Log.e("FirebaseHelper", "Post sync failed: " + task.getException().getMessage());
                    }
                });
    }

    public void deletePostFromCloud(int postId) {
        realtimeDb.getReference("posts").child(String.valueOf(postId)).removeValue();
    }

    public void syncPostReactionToRealtimeDb(int postId, int userId, String reactionType) {
        realtimeDb.getReference("post_reactions").child(String.valueOf(postId)).child(String.valueOf(userId)).setValue(reactionType);
    }

    public void syncMessageToRealtimeDb(int conversationId, Message msg) {
        if (msg == null) return;
        realtimeDb.getReference("messages").child(String.valueOf(conversationId)).child(String.valueOf(msg.getId())).setValue(msg)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        android.util.Log.e("FirebaseHelper", "Message sync failed: " + task.getException().getMessage());
                    }
                });
    }

    public void syncConversationToRealtimeDb(int convId, int user1Id, int user2Id) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", convId);
        map.put("user1Id", user1Id);
        map.put("user2Id", user2Id);
        realtimeDb.getReference("conversations").child(String.valueOf(convId)).setValue(map);
    }

    public void syncMessageReactionToRealtimeDb(int messageId, int userId, String reaction) {
        realtimeDb.getReference("message_reactions").child(String.valueOf(messageId)).child(String.valueOf(userId)).setValue(reaction);
    }

    public void syncCommentToRealtimeDb(int postId, Comment commentObj) {
        if (commentObj == null) return;
        realtimeDb.getReference("post_comments").child(String.valueOf(postId)).child(String.valueOf(commentObj.getId())).setValue(commentObj)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful() && task.getException() != null) {
                        android.util.Log.e("FirebaseHelper", "Comment sync failed: " + task.getException().getMessage());
                    }
                });
    }

    public void syncCommentLikeToRealtimeDb(int commentId, int currentUserId) {
        realtimeDb.getReference("comment_likes").child(String.valueOf(commentId)).child(String.valueOf(currentUserId)).setValue(true);
    }

    public void syncStoryToRealtimeDb(Story story) {
        if (story == null) return;
        realtimeDb.getReference("stories").child(String.valueOf(story.getId())).setValue(story);
    }

    public void syncStoryReactionToRealtimeDb(int storyId, int userId, String reactionType) {
        realtimeDb.getReference("story_reactions").child(String.valueOf(storyId)).child(String.valueOf(userId)).setValue(reactionType);
    }

    public void syncStoryCommentToRealtimeDb(int storyId, int userId, String comment) {
        realtimeDb.getReference("story_comments").child(String.valueOf(storyId)).child(String.valueOf(userId)).push().setValue(comment);
    }

    public void syncGroupToRealtimeDb(Group group) {
        if (group == null) return;
        realtimeDb.getReference("groups").child(String.valueOf(group.getId())).setValue(group);
    }

    public void syncGroupMessageToRealtimeDb(int groupId, GroupMessage gMsg) {
        if (gMsg == null) return;
        realtimeDb.getReference("group_messages").child(String.valueOf(groupId)).child(String.valueOf(gMsg.getId())).setValue(gMsg);
    }

    public void syncNotificationToRealtimeDb(AppNotification notif) {
        if (notif == null) return;
        realtimeDb.getReference("notifications").child(String.valueOf(notif.getRecipientId())).child(String.valueOf(notif.getId())).setValue(notif);
    }

    public void syncFriendRequestToRealtimeDb(int senderId, int receiverId, Object item) {
        realtimeDb.getReference("friend_requests").child(String.valueOf(senderId)).child(String.valueOf(receiverId)).setValue(item);
    }

    public void removeFriendRequestFromRealtimeDb(int senderId, int receiverId) {
        realtimeDb.getReference("friend_requests").child(String.valueOf(senderId)).child(String.valueOf(receiverId)).removeValue();
    }
}

