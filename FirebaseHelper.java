package td.teladoumbaobabtd;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Gestionnaire centralisé pour l'ensemble des services Firebase Cloud :
 * Authentification, Firestore Temps Réel, Firebase Storage et Push Messaging (FCM).
 */
public class FirebaseHelper {

    private static FirebaseHelper instance;
    private final FirebaseAuth mAuth;
    private final FirebaseFirestore mFirestore;
    private final FirebaseStorage mStorage;

    public interface OnUploadCompleteListener {
        void onSuccess(String downloadUrl);
        void onFailure(Exception e);
    }

    private FirebaseHelper() {
        mAuth = FirebaseAuth.getInstance();
        mFirestore = FirebaseFirestore.getInstance();
        mStorage = FirebaseStorage.getInstance();
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    public FirebaseAuth getAuth() {
        return mAuth;
    }

    public FirebaseFirestore getFirestore() {
        return mFirestore;
    }

    public FirebaseStorage getStorage() {
        return mStorage;
    }

    public FirebaseUser getCurrentUser() {
        return mAuth.getCurrentUser();
    }

    /**
     * Inscription dans Firebase Authentication.
     */
    public void registerUser(String email, String password, OnCompleteListener<AuthResult> listener) {
        mAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(listener);
    }

    /**
     * Connexion dans Firebase Authentication.
     */
    public void loginUser(String email, String password, OnCompleteListener<AuthResult> listener) {
        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(listener);
    }

    /**
     * Téléversement d'un fichier média (image/vidéo) vers Firebase Storage et récupération de l'URL publique.
     */
    public void uploadMedia(String folder, Uri fileUri, OnUploadCompleteListener listener) {
        if (fileUri == null) {
            if (listener != null) listener.onFailure(new IllegalArgumentException("URI de fichier nul"));
            return;
        }

        String fileName = System.currentTimeMillis() + "_" + fileUri.getLastPathSegment();
        StorageReference ref = mStorage.getReference().child(folder).child(fileName);

        try {
            ref.putFile(fileUri)
                    .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                            .addOnSuccessListener(uri -> {
                                if (listener != null) listener.onSuccess(uri.toString());
                            })
                            .addOnFailureListener(e -> {
                                if (listener != null) listener.onFailure(e);
                            }))
                    .addOnFailureListener(e -> {
                        if (listener != null) listener.onFailure(e);
                    });
        } catch (Exception e) {
            if (listener != null) listener.onFailure(e);
        }
    }

    /**
     * Écoute en temps réel des messages Firestore pour une conversation donnée (`addSnapshotListener`).
     */
    public ListenerRegistration listenToMessages(int conversationId, EventListener<QuerySnapshot> listener) {
        return mFirestore.collection("conversations")
                .document(String.valueOf(conversationId))
                .collection("messages")
                .orderBy("created_at", Query.Direction.ASCENDING)
                .addSnapshotListener(listener);
    }

    /**
     * Envoie d'un message dans Firestore.
     */
    public void sendFirestoreMessage(int conversationId, Message message, OnCompleteListener<DocumentReference> listener) {
        Map<String, Object> msgMap = new HashMap<>();
        msgMap.put("id", message.getId());
        msgMap.put("conversation_id", conversationId);
        msgMap.put("sender_id", message.getSenderId());
        msgMap.put("message", message.getMessage());
        msgMap.put("image_path", message.getImagePath());
        msgMap.put("is_read", message.isRead());
        msgMap.put("created_at", message.getCreatedAt());

        mFirestore.collection("conversations")
                .document(String.valueOf(conversationId))
                .collection("messages")
                .add(msgMap)
                .addOnCompleteListener(listener);
    }

    /**
     * Publication d'un post dans Cloud Firestore.
     */
    public void syncPostToFirestore(Post post) {
        Map<String, Object> postMap = new HashMap<>();
        postMap.put("id", post.getId());
        postMap.put("user_id", post.getUserId());
        postMap.put("author_name", post.getAuthorName());
        postMap.put("content", post.getContent());
        postMap.put("image_path", post.getImagePath());
        postMap.put("video_path", post.getVideoPath());
        postMap.put("created_at", post.getCreatedAt());

        mFirestore.collection("posts")
                .document(String.valueOf(post.getId()))
                .set(postMap);
    }
}