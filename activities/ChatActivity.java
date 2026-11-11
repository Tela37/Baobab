package td.teladoum.baobab.activities;

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
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.ConversationRepository;
import td.teladoum.baobab.repositories.FriendRepository;
import td.teladoum.baobab.repositories.MessageRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité principale de discussion en direct (Chat).
 * Gère l'envoi/réception de messages et photos, l'affichage de l'indicateur de saisie,
 * les statuts de lecture (✓/✓✓ bleu), les réactions émojis et les réponses ciblées (Quote Reply).
 */
public class ChatActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TextView tvChatName;
    private TextView tvUserStatus;
    private TextView tvTypingIndicator;
    private ImageView imgChatAvatar;
    private View viewOnlineIndicator;

    private RecyclerView rvMessages;

    private EditText etMessage;
    private ImageButton ibAttachImage;
    private Button btnSend;

    private TextView tvCounter;
    private ProgressBar progressChars;

    // Bandeau de réponse ciblée (Quote Reply)
    private LinearLayout layoutReplyBanner;
    private TextView tvReplyTextPreview;
    private ImageButton ibCancelReply;
    private Message replyingToMessage;

    private static final int MAX_CHARS = 500;

    private MessageRepository messageRepository;
    private UserRepository userRepository;
    private ConversationRepository conversationRepository;
    private SessionManager sessionManager;

    private ChatAdapter adapter;
    private List<Message> messageList;

    private int conversationId;
    private int receiverId = -1;
    private String receiverName;
    private String receiverProfileImage;

    private AlertDialog activeImageDialog;
    private boolean isFinishingAdShown = false;
    private com.google.firebase.firestore.ListenerRegistration firestoreListener;

    private final ActivityResultLauncher<CropImageContractOptions> imagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        sendImageMessage(uri);
                    }
                } else if (result.getError() != null) {
                    Toasty.error(this, "Erreur lors du recadrage", Toasty.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<Intent> multipleImagePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Intent data = result.getData();
                    List<Uri> uris = new ArrayList<>();
                    if (data.getClipData() != null) {
                        int count = data.getClipData().getItemCount();
                        for (int i = 0; i < count; i++) {
                            uris.add(data.getClipData().getItemAt(i).getUri());
                        }
                    } else if (data.getData() != null) {
                        uris.add(data.getData());
                    }

                    if (!uris.isEmpty()) {
                        int count = Math.min(uris.size(), 10);
                        if (uris.size() > 10) {
                            Toasty.info(this, "Seules les 10 premières photos ont été envoyées", Toast.LENGTH_SHORT).show();
                        }
                        String captionText = etMessage.getText().toString().trim();
                        Integer replyId = replyingToMessage != null ? replyingToMessage.getId() : null;
                        String replyText = replyingToMessage != null ?
                                (replyingToMessage.getMessage() != null && !replyingToMessage.getMessage().isEmpty() ? replyingToMessage.getMessage() : "Photo") : null;

                        boolean anySuccess = false;
                        for (int i = 0; i < count; i++) {
                            Uri uri = uris.get(i);
                            String savedPath = ImageUtils.saveImageToInternalStorage(this, uri);
                            if (savedPath != null) {
                                boolean success = messageRepository.sendMessage(
                                        conversationId,
                                        sessionManager.getUserId(),
                                        i == 0 ? captionText : "",
                                        savedPath,
                                        replyId,
                                        replyText
                                );
                                if (success) anySuccess = true;
                            }
                        }

                        if (anySuccess) {
                            etMessage.setText("");
                            tvCounter.setText("0/500");
                            progressChars.setProgress(0);
                            cancelQuoteReply();
                            hideKeyboard();
                            loadMessages();
                            Toasty.success(this, "Photos envoyées avec succès", Toast.LENGTH_SHORT).show();
                        } else {
                            Toasty.warning(this, "Erreur lors de l'envoi des photos", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), videoUri -> {
                if (videoUri != null) {
                    String savedPath = ImageUtils.saveVideoToInternalStorage(this, videoUri);
                    if (savedPath != null) {
                        sendVideoMessage(savedPath);
                    } else {
                        Toasty.error(this, "Erreur lors de l'enregistrement de la vidéo", Toasty.LENGTH_SHORT).show();
                    }
                }
            });

    private void launchImageCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.activityTitle = "Recadrer l'image";
        options.cropMenuCropButtonTitle = "Valider";
        options.activityMenuIconColor = android.graphics.Color.WHITE;
        options.toolbarColor = android.graphics.Color.parseColor("#2196F3");
        options.toolbarTitleColor = android.graphics.Color.WHITE;
        options.toolbarBackButtonColor = android.graphics.Color.WHITE;
        options.toolbarTintColor = android.graphics.Color.WHITE;
        imagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    private void showMediaSelectionOptions() {
        String[] options = {"📸 Prendre une photo (Recadrage)", "🖼️ Sélectionner plusieurs photos (Jusqu'à 10)", "🎥 Sélectionner et envoyer une vidéo"};
        new AlertDialog.Builder(this)
                .setTitle("Joindre un fichier 📎")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchImageCropper();
                    } else if (which == 1) {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        multipleImagePickerLauncher.launch(Intent.createChooser(intent, "Sélectionner des photos"));
                    } else if (which == 2) {
                        videoPickerLauncher.launch("video/*");
                    }
                })
                .show();
    }

    private void sendVideoMessage(String videoPath) {
        boolean sent = messageRepository.sendVideoMessage(conversationId, sessionManager.getUserId(), videoPath);
        if (sent) {
            loadMessages();
        }
    }

    private void showVideoPreviewDialog(String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) return;

        android.widget.VideoView videoView = new android.widget.VideoView(this);
        videoView.setVideoPath(videoPath);
        android.widget.MediaController mediaController = new android.widget.MediaController(this);
        mediaController.setAnchorView(videoView);
        videoView.setMediaController(mediaController);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(videoView)
                .create();

        dialog.setOnDismissListener(d -> videoView.stopPlayback());
        dialog.show();
        videoView.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_chat);

        initViews();

        messageRepository = new MessageRepository(this);
        userRepository = new UserRepository(this);
        conversationRepository = new ConversationRepository(this);
        sessionManager = new SessionManager(this);

        setupToolbar();
        setupRecyclerView();
        setupCharacterCounter();
        readIntentData();

        messageList = new ArrayList<>();

        adapter = new ChatAdapter(
                messageList,
                sessionManager.getUserId(),
                message -> showMessageOptionsDialog(message),
                imagePath -> showImagePreviewDialog(imagePath),
                videoPath -> showVideoPreviewDialog(videoPath),
                audioPath -> playAudioMessage(audioPath)
        );

        rvMessages.setAdapter(adapter);
        if (adapter != null && receiverProfileImage != null) {
            adapter.setReceiverProfileImage(receiverProfileImage);
        }

        loadMessages();

        btnSend.setOnClickListener(v -> sendMessage());
        ibAttachImage.setOnClickListener(v -> showMediaSelectionOptions());
        if (ibCancelReply != null) {
            ibCancelReply.setOnClickListener(v -> cancelQuoteReply());
        }
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvChatName = findViewById(R.id.tvChatName);
        tvUserStatus = findViewById(R.id.tvUserStatus);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        imgChatAvatar = findViewById(R.id.imgChatAvatar);
        viewOnlineIndicator = findViewById(R.id.viewOnlineIndicator);

        rvMessages = findViewById(R.id.rvMessages);

        etMessage = findViewById(R.id.etMessage);
        ibAttachImage = findViewById(R.id.ibAttachImage);
        ibRecordAudio = findViewById(R.id.ibRecordAudio);
        btnSend = findViewById(R.id.btnSend);

        tvCounter = findViewById(R.id.tvCounter);
        progressChars = findViewById(R.id.progressChars);

        layoutReplyBanner = findViewById(R.id.layoutReplyBanner);
        tvReplyTextPreview = findViewById(R.id.tvReplyTextPreview);
        ibCancelReply = findViewById(R.id.ibCancelReply);

        if (ibRecordAudio != null) {
            ibRecordAudio.setOnClickListener(v -> {
                if (!isRecording) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 102);
                    } else {
                        startRecordingAudio();
                    }
                } else {
                    stopAndSendAudioMessage();
                }
            });
        }
    }

    private ImageButton ibRecordAudio;
    private android.media.MediaRecorder mediaRecorder;
    private String currentAudioRecordingPath;
    private boolean isRecording = false;

    private void startRecordingAudio() {
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "voice_messages");
            if (!dir.exists()) dir.mkdirs();
            currentAudioRecordingPath = new java.io.File(dir, "audio_" + System.currentTimeMillis() + ".3gp").getAbsolutePath();

            mediaRecorder = new android.media.MediaRecorder();
            mediaRecorder.setAudioSource(android.media.MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(currentAudioRecordingPath);
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            if (ibRecordAudio != null) ibRecordAudio.setImageResource(android.R.drawable.ic_media_pause);
            Toasty.info(this, "Enregistrement vocal... Appuyez de nouveau pour envoyer 🎙️", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toasty.error(this, "Erreur démarrage micro", Toast.LENGTH_SHORT).show();
            isRecording = false;
        }
    }

    private void stopAndSendAudioMessage() {
        if (!isRecording) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            if (ibRecordAudio != null) ibRecordAudio.setImageResource(android.R.drawable.ic_btn_speak_now);

            if (currentAudioRecordingPath != null) {
                boolean sent = messageRepository.sendAudioMessage(conversationId, sessionManager.getUserId(), currentAudioRecordingPath);
                if (sent) {
                    loadMessages();
                    Toasty.success(this, "Message vocal envoyé", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            isRecording = false;
            if (ibRecordAudio != null) ibRecordAudio.setImageResource(android.R.drawable.ic_btn_speak_now);
            Toasty.error(this, "Erreur enregistrement audio", Toast.LENGTH_SHORT).show();
        }
    }

    private void playAudioMessage(String audioPath) {
        if (audioPath == null || audioPath.trim().isEmpty()) return;
        try {
            android.media.MediaPlayer player = new android.media.MediaPlayer();
            player.setDataSource(audioPath);
            player.prepare();
            player.start();
            Toasty.info(this, "Lecture audio 🔊", Toast.LENGTH_SHORT).show();
            player.setOnCompletionListener(mp -> mp.release());
        } catch (Exception e) {
            e.printStackTrace();
            Toasty.error(this, "Lecture audio impossible", Toast.LENGTH_SHORT).show();
        }
    }

    private void readIntentData() {
        conversationId = getIntent().getIntExtra("conversation_id", -1);
        receiverId = getIntent().getIntExtra("receiver_id", -1);
        receiverName = getIntent().getStringExtra("receiver_name");
        receiverProfileImage = getIntent().getStringExtra("receiver_profile_image");

        if (conversationId == -1 && receiverId != -1) {
            conversationId = conversationRepository.createConversationIfNotExists(sessionManager.getUserId(), receiverId);
        } else if (receiverId == -1 && conversationId != -1) {
            receiverId = conversationRepository.getOtherUserId(conversationId, sessionManager.getUserId());
        }

        if (receiverId != -1) {
            User user = userRepository.getUserById(receiverId);
            if (user != null) {
                if (receiverName == null || receiverName.isEmpty()) {
                    receiverName = user.getName();
                }
                if (receiverProfileImage == null || receiverProfileImage.isEmpty()) {
                    receiverProfileImage = user.getProfileImage();
                }
            }
        }

        if (receiverName != null && !receiverName.isEmpty()) {
            tvChatName.setText(receiverName);
        } else {
            tvChatName.setText("Discussion");
        }

        ImageUtils.loadProfileImage(this, receiverProfileImage, imgChatAvatar);
        loadUserPresence();
        listenToTypingStatus();

        if (conversationId != -1) {
            messageRepository.loadMessagesFromCloud(conversationId, () -> loadMessages());
        }
    }

    private void loadUserPresence() {
        if (receiverId == -1 && conversationId != -1) {
            receiverId = conversationRepository.getOtherUserId(conversationId, sessionManager.getUserId());
        }
        if (receiverId == -1) {
            if (viewOnlineIndicator != null) viewOnlineIndicator.setVisibility(View.GONE);
            if (tvUserStatus != null) tvUserStatus.setVisibility(View.GONE);
            return;
        }

        User user = userRepository.getUserById(receiverId);
        if (user == null) {
            if (viewOnlineIndicator != null) viewOnlineIndicator.setVisibility(View.GONE);
            if (tvUserStatus != null) tvUserStatus.setVisibility(View.GONE);
            return;
        }

        if (user.isOnline()) {
            if (viewOnlineIndicator != null) viewOnlineIndicator.setVisibility(View.VISIBLE);
            if (tvUserStatus != null) {
                tvUserStatus.setText("En ligne");
                tvUserStatus.setVisibility(View.VISIBLE);
            }
        } else {
            if (viewOnlineIndicator != null) viewOnlineIndicator.setVisibility(View.GONE);
            String formattedTime = formatLastSeenTime(user.getLastSeen());
            if (formattedTime != null && !formattedTime.isEmpty() && tvUserStatus != null) {
                tvUserStatus.setText(formattedTime);
                tvUserStatus.setVisibility(View.VISIBLE);
            } else if (tvUserStatus != null) {
                tvUserStatus.setVisibility(View.GONE);
            }
        }
    }

    private void listenToTypingStatus() {
        if (conversationId != -1 && receiverId != -1) {
            FirebaseDatabase.getInstance().getReference("conversations")
                    .child(String.valueOf(conversationId))
                    .child("typing")
                    .child(String.valueOf(receiverId))
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            Boolean isOtherTyping = snapshot.getValue(Boolean.class);
                            if (tvTypingIndicator != null) {
                                tvTypingIndicator.setVisibility((isOtherTyping != null && isOtherTyping) ? View.VISIBLE : View.GONE);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                        }
                    });
        }
    }

    private String formatLastSeenTime(String lastSeenStr) {
        if (lastSeenStr == null || lastSeenStr.trim().isEmpty()) {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        try {
            Date date = sdf.parse(lastSeenStr);
            if (date == null) return null;

            long diffMs = System.currentTimeMillis() - date.getTime();
            if (diffMs < 0) diffMs = 0;

            long diffMinutes = diffMs / (60 * 1000);

            if (diffMinutes < 1) {
                return "En ligne il y a un instant";
            } else if (diffMinutes <= 60) {
                if (diffMinutes == 60) {
                    return "En ligne il y a 1h";
                } else {
                    return "En ligne il y a " + diffMinutes + " min";
                }
            } else {
                // Limite "En ligne il y a 1h, pas plus"
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
    }

    private void setupCharacterCounter() {
        etMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int length = s.length();
                tvCounter.setText(length + "/" + MAX_CHARS);
                progressChars.setProgress(length);
                btnSend.setEnabled(length <= MAX_CHARS);

                // Publication du statut de saisie de l'expéditeur vers Firebase Realtime Database
                if (conversationId != -1 && sessionManager != null) {
                    FirebaseDatabase.getInstance().getReference("conversations")
                            .child(String.valueOf(conversationId))
                            .child("typing")
                            .child(String.valueOf(sessionManager.getUserId()))
                            .setValue(length > 0);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Saisissez un message", Toast.LENGTH_SHORT).show();
            return;
        }

        Integer replyId = replyingToMessage != null ? replyingToMessage.getId() : null;
        String replyText = replyingToMessage != null ?
                (replyingToMessage.getMessage() != null && !replyingToMessage.getMessage().isEmpty() ? replyingToMessage.getMessage() : "Photo") : null;

        boolean success = messageRepository.sendMessage(
                conversationId,
                sessionManager.getUserId(),
                text,
                null,
                replyId,
                replyText
        );

        if (success) {
            if (!MessageRepository.isNetworkAvailable(this)) {
                Toasty.warning(this, "⚠️ Mode Hors-Ligne : Message enregistré localement !", Toasty.LENGTH_SHORT).show();
            }
            if (conversationId != -1 && sessionManager != null) {
                FirebaseDatabase.getInstance().getReference("conversations")
                        .child(String.valueOf(conversationId))
                        .child("typing")
                        .child(String.valueOf(sessionManager.getUserId()))
                        .setValue(false);
            }
            etMessage.setText("");
            tvCounter.setText("0/500");
            progressChars.setProgress(0);
            cancelQuoteReply();
            hideKeyboard();
            loadMessages();
        } else {
            Toasty.warning(this, "Erreur lors de l'envoi", Toasty.LENGTH_SHORT).show();
        }
    }

    private void sendImageMessage(Uri imageUri) {
        String base64Image = ImageUtils.uriToBase64(this, imageUri);
        String savedPath = base64Image != null && !base64Image.trim().isEmpty() ? base64Image : ImageUtils.saveImageToInternalStorage(this, imageUri);
        if (savedPath == null) {
            Toasty.warning(this, "Erreur lors de la préparation de l'image", Toasty.LENGTH_SHORT).show();
            return;
        }

        String captionText = etMessage.getText().toString().trim();

        Integer replyId = replyingToMessage != null ? replyingToMessage.getId() : null;
        String replyText = replyingToMessage != null ?
                (replyingToMessage.getMessage() != null && !replyingToMessage.getMessage().isEmpty() ? replyingToMessage.getMessage() : "Photo") : null;

        boolean success = messageRepository.sendMessage(
                conversationId,
                sessionManager.getUserId(),
                captionText,
                savedPath,
                replyId,
                replyText
        );

        if (success) {
            etMessage.setText("");
            tvCounter.setText("0/500");
            progressChars.setProgress(0);
            cancelQuoteReply();
            hideKeyboard();
            loadMessages();
        } else {
            Toasty.warning(this, "Erreur lors de l'envoi de l'image", Toasty.LENGTH_SHORT).show();
        }
    }

    /**
     * Boîte de dialogue sur appui long pour ajouter une réaction émoji ou citer/répondre à un message.
     */
    private void showMessageOptionsDialog(Message message) {
        List<String> optionsList = new ArrayList<>();
        optionsList.add("❤️ Réaction Coeur");
        optionsList.add("😂 Réaction Rire");
        optionsList.add("👍 Réaction Pouce");
        optionsList.add("↩ Répondre au message (Citer)");

        if (message.getSenderId() == sessionManager.getUserId()) {
            if (TimeUtils.isWithinHours(message.getCreatedAt(), 1)) {
                optionsList.add("✏️ Modifier le message");
            }
            optionsList.add("🗑️ Supprimer le message");
        }

        String[] options = optionsList.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Options du message")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        messageRepository.setMessageReaction(message.getId(), "❤️");
                        loadMessages();
                    } else if (which == 1) {
                        messageRepository.setMessageReaction(message.getId(), "😂");
                        loadMessages();
                    } else if (which == 2) {
                        messageRepository.setMessageReaction(message.getId(), "👍");
                        loadMessages();
                    } else if (which == 3) {
                        setupQuoteReply(message);
                    } else if (which == 4) {
                        if (message.getSenderId() == sessionManager.getUserId()) {
                            showEditMessageDialog(message);
                        } else {
                            deleteMessageAction(message);
                        }
                    } else if (which == 5) {
                        deleteMessageAction(message);
                    }
                })
                .show();
    }

    private void deleteMessageAction(Message message) {
        boolean deleted = messageRepository.deleteMessage(message.getId());
        if (deleted) {
            Toasty.success(ChatActivity.this, "Message supprimé", Toast.LENGTH_SHORT).show();
            loadMessages();
        } else {
            Toasty.error(ChatActivity.this, "Impossible de supprimer : le délai d'une heure est dépassé", Toast.LENGTH_LONG).show();
        }
    }

    private void showEditMessageDialog(Message message) {
        EditText input = new EditText(this);
        input.setText(message.getMessage());
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Modifier le message")
                .setView(input)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String newText = input.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        boolean updated = messageRepository.updateMessage(message.getId(), newText);
                        if (updated) {
                            Toasty.success(this, "Message modifié", Toast.LENGTH_SHORT).show();
                            loadMessages();
                        } else {
                            Toasty.error(this, "Erreur lors de la modification", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void setupQuoteReply(Message message) {
        replyingToMessage = message;
        if (layoutReplyBanner != null && tvReplyTextPreview != null) {
            layoutReplyBanner.setVisibility(View.VISIBLE);
            String snippet = message.getMessage() != null && !message.getMessage().isEmpty() ? message.getMessage() : "Photo";
            tvReplyTextPreview.setText("Réponse à : " + snippet);
        }
    }

    private void cancelQuoteReply() {
        replyingToMessage = null;
        if (layoutReplyBanner != null) {
            layoutReplyBanner.setVisibility(View.GONE);
        }
    }

    private void showImagePreviewDialog(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        if (activeImageDialog != null && activeImageDialog.isShowing()) {
            activeImageDialog.dismiss();
        }

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);
        ImageButton ibShare = dialogView.findViewById(R.id.ibSharePreview);
        ImageButton ibDownload = dialogView.findViewById(R.id.ibDownloadPreview);
        if (ibDownload != null) {
            ibDownload.setOnClickListener(v -> ImageUtils.downloadImageToGallery(this, imagePath));
        }

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);

        activeImageDialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.setOnClickListener(v -> {
                if (activeImageDialog != null) activeImageDialog.dismiss();
            });
        }
        if (ibShare != null) {
            ibShare.setOnClickListener(v -> showShareImageToFriendsDialog(imagePath));
        }
        dialogView.setOnClickListener(v -> {
            if (activeImageDialog != null) activeImageDialog.dismiss();
        });
        activeImageDialog.setOnDismissListener(d -> activeImageDialog = null);
        activeImageDialog.show();
    }

    private void showShareImageToFriendsDialog(String imagePath) {
        FriendRepository friendRepo = new FriendRepository(this);
        List<User> friends = friendRepo.getFriendsList(sessionManager.getUserId(), null);
        if (friends.isEmpty()) {
            Toasty.info(this, "Aucun ami dans votre liste à qui partager", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] friendNames = new String[friends.size()];
        for (int i = 0; i < friends.size(); i++) {
            friendNames[i] = friends.get(i).getName() != null ? friends.get(i).getName() : "Ami";
        }

        new AlertDialog.Builder(this)
                .setTitle("Partager l'image avec un ami")
                .setItems(friendNames, (dialog, which) -> {
                    User selectedFriend = friends.get(which);
                    int convId = conversationRepository.createConversationIfNotExists(sessionManager.getUserId(), selectedFriend.getId());
                    boolean sent = messageRepository.sendMessage(convId, sessionManager.getUserId(), null, imagePath);
                    if (sent) {
                        Toasty.success(this, "Image partagée avec " + selectedFriend.getName(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toasty.error(this, "Erreur lors du partage", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void loadMessages() {
        // 1. Ne marquer comme lu en BDD que s'il y a des messages non lus
        if (messageRepository.getUnreadCount(conversationId, sessionManager.getUserId()) > 0) {
            messageRepository.markMessagesAsRead(conversationId, sessionManager.getUserId());
        }

        // 2. Récupérer la liste des messages
        List<Message> newMessages = messageRepository.getMessages(conversationId);

        // 3. Éviter les rafraîchissements d'UI inutiles si rien n'a changé
        if (isMessageListIdentical(messageList, newMessages)) {
            return;
        }

        boolean wasAtBottom = isScrolledToBottom();

        messageList.clear();
        messageList.addAll(newMessages);
        adapter.notifyDataSetChanged();

        if (wasAtBottom) {
            scrollToBottom();
        }
    }

    private boolean isMessageListIdentical(List<Message> oldList, List<Message> newList) {
        if (oldList.size() != newList.size()) {
            return false;
        }
        if (oldList.isEmpty()) {
            return true;
        }

        Message lastOld = oldList.get(oldList.size() - 1);
        Message lastNew = newList.get(newList.size() - 1);

        if (lastOld.getId() != lastNew.getId() || lastOld.isRead() != lastNew.isRead()) {
            return false;
        }

        String reactOld = lastOld.getReaction() != null ? lastOld.getReaction() : "";
        String reactNew = lastNew.getReaction() != null ? lastNew.getReaction() : "";

        return reactOld.equals(reactNew);
    }

    private boolean isScrolledToBottom() {
        if (rvMessages == null || rvMessages.getAdapter() == null || messageList.isEmpty()) {
            return true;
        }
        LinearLayoutManager layoutManager = (LinearLayoutManager) rvMessages.getLayoutManager();
        if (layoutManager == null) return true;

        int lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition();
        return lastVisibleItem >= messageList.size() - 2;
    }

    private void scrollToBottom() {
        if (!messageList.isEmpty()) {
            rvMessages.post(() -> rvMessages.smoothScrollToPosition(messageList.size() - 1));
        }
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserPresence();
        if (conversationId != -1) {
            firestoreListener = FirebaseHelper.getInstance().listenToMessages(conversationId, (snapshot, error) -> {
                if (snapshot != null && !snapshot.isEmpty()) {
                    loadMessages();
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (firestoreListener != null) {
            firestoreListener.remove();
            firestoreListener = null;
        }
        if (conversationId != -1 && sessionManager != null) {
            FirebaseDatabase.getInstance().getReference("conversations")
                    .child(String.valueOf(conversationId))
                    .child("typing")
                    .child(String.valueOf(sessionManager.getUserId()))
                    .setValue(false);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 3002, 0, "📞 Appel vocal");
        menu.add(0, 3003, 0, "📹 Appel vidéo");
        menu.add(0, 3001, 0, "🗑️ Supprimer la conversation");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 3002) {
            Intent callIntent = new Intent(ChatActivity.this, CallActivity.class);
            callIntent.putExtra("target_name", receiverName != null ? receiverName : "Correspondant");
            callIntent.putExtra("is_video_call", false);
            startActivity(callIntent);
            return true;
        } else if (item.getItemId() == 3003) {
            Intent callIntent = new Intent(ChatActivity.this, CallActivity.class);
            callIntent.putExtra("target_name", receiverName != null ? receiverName : "Correspondant");
            callIntent.putExtra("is_video_call", true);
            startActivity(callIntent);
            return true;
        } else if (item.getItemId() == 3001) {
            new AlertDialog.Builder(this)
                    .setTitle("Supprimer la conversation 🗑️")
                    .setMessage("Voulez-vous vraiment supprimer cette conversation ? Tous les messages seront effacés.")
                    .setPositiveButton("Supprimer", (dialog, which) -> {
                        boolean deleted = conversationRepository.deleteConversation(conversationId);
                        if (deleted) {
                            Toasty.success(this, "Conversation supprimée", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toasty.error(this, "Erreur lors de la suppression", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Annuler", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        if (isFinishingAdShown || isFinishing() || isDestroyed()) {
            super.finish();
            return;
        }
        isFinishingAdShown = true;
        if (activeImageDialog != null && activeImageDialog.isShowing()) {
            activeImageDialog.dismiss();
            activeImageDialog = null;
        }
        AdMobManager.showInterstitialAd(this, super::finish);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activeImageDialog != null && activeImageDialog.isShowing()) {
            activeImageDialog.dismiss();
            activeImageDialog = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecordingAudio();
            } else {
                Toasty.error(this, "Permission microphone refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
