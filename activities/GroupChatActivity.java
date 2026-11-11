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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.GroupRepository;

/**
 * Écran de discussion de groupe (GroupChatActivity).
 * Gère l'affichage des messages de groupe, la sélection multi-images, l'envoi de vidéo,
 * l'envoi de documents PDF, les messages vocaux (audio) avec option de désactivation administrateur,
 * et les réactions/likes sur les messages.
 */
public class GroupChatActivity extends AppCompatActivity {

    private Toolbar toolbarGroupChat;
    private TextView tvGroupChatTitle;
    private TextView tvGroupChatSubTitle;
    private ImageButton ibGroupSettings;

    private LinearLayout layoutGroupLockedBanner;
    private RecyclerView rvGroupMessages;

    private LinearLayout layoutGroupSend;
    private ImageButton ibAttachGroupImage;
    private ImageButton ibRecordAudio;
    private EditText etGroupMessage;
    private Button btnSendGroupMessage;

    private GroupRepository groupRepository;
    private SessionManager sessionManager;
    private int currentUserId;
    private int groupId;

    private Group currentGroup;
    private boolean isAdmin = false;

    private final List<GroupMessage> messageList = new ArrayList<>();
    private GroupMessageAdapter adapter;

    // Enregistrement audio
    private android.media.MediaRecorder mediaRecorder;
    private String currentAudioRecordingPath;
    private boolean isRecording = false;

    private final ActivityResultLauncher<CropImageContractOptions> imagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        String base64 = ImageUtils.uriToBase64(this, uri);
                        String savedPath = base64 != null && !base64.trim().isEmpty() ? base64 : ImageUtils.saveImageToInternalStorage(this, uri);
                        if (savedPath != null) {
                            sendGroupImageMessage(savedPath);
                        }
                    }
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
                        boolean anySuccess = false;
                        for (int i = 0; i < count; i++) {
                            Uri u = uris.get(i);
                            String base64 = ImageUtils.uriToBase64(this, u);
                            String savedPath = base64 != null && !base64.trim().isEmpty() ? base64 : ImageUtils.saveImageToInternalStorage(this, u);
                            if (savedPath != null) {
                                boolean sent = groupRepository.sendGroupMessage(groupId, currentUserId, null, savedPath);
                                if (sent) anySuccess = true;
                            }
                        }

                        if (anySuccess) {
                            loadGroupMessages();
                            Toasty.success(this, "Photos de groupe envoyées", Toast.LENGTH_SHORT).show();
                        } else {
                            Toasty.error(this, "Erreur lors de l'envoi des photos", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> videoPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), videoUri -> {
                if (videoUri != null) {
                    String savedPath = ImageUtils.saveVideoToInternalStorage(this, videoUri);
                    if (savedPath != null) {
                        sendGroupVideoMessage(savedPath);
                    } else {
                        Toasty.error(this, "Erreur lors de l'enregistrement de la vidéo", Toasty.LENGTH_SHORT).show();
                    }
                }
            });

    private final ActivityResultLauncher<String> pdfPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(), pdfUri -> {
                if (pdfUri != null) {
                    String savedPath = ImageUtils.saveFileToInternalStorage(this, pdfUri, "pdf");
                    if (savedPath != null) {
                        String fileName = "Document_" + System.currentTimeMillis() + ".pdf";
                        sendGroupFileMessage(savedPath, fileName);
                    } else {
                        Toasty.error(this, "Erreur lors de l'enregistrement du PDF", Toasty.LENGTH_SHORT).show();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_chat);

        groupId = getIntent().getIntExtra("group_id", -1);

        groupRepository = new GroupRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        setupToolbar();
        setupRecyclerView();

        ibGroupSettings.setOnClickListener(v -> {
            Intent intent = new Intent(GroupChatActivity.this, GroupSettingsActivity.class);
            intent.putExtra("group_id", groupId);
            startActivity(intent);
        });

        btnSendGroupMessage.setOnClickListener(v -> sendTextMessage());
        ibAttachGroupImage.setOnClickListener(v -> showMediaSelectionOptions());

        if (ibRecordAudio != null) {
            ibRecordAudio.setOnClickListener(v -> {
                if (currentGroup != null && !currentGroup.isVoiceEnabled() && !isAdmin) {
                    Toasty.warning(this, "Les messages vocaux ont été désactivés par l'administrateur 🔇", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isRecording) {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 102);
                    } else {
                        startGroupVoiceRecording();
                    }
                } else {
                    stopAndSendGroupVoice();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroupInfo();
        loadGroupMessages();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    private void initViews() {
        toolbarGroupChat = findViewById(R.id.toolbarGroupChat);
        tvGroupChatTitle = findViewById(R.id.tvGroupChatTitle);
        tvGroupChatSubTitle = findViewById(R.id.tvGroupChatSubTitle);
        ibGroupSettings = findViewById(R.id.ibGroupSettings);

        layoutGroupLockedBanner = findViewById(R.id.layoutGroupLockedBanner);
        rvGroupMessages = findViewById(R.id.rvGroupMessages);

        layoutGroupSend = findViewById(R.id.layoutGroupSend);
        ibAttachGroupImage = findViewById(R.id.ibAttachGroupImage);
        ibRecordAudio = findViewById(R.id.ibRecordAudio);
        etGroupMessage = findViewById(R.id.etGroupMessage);
        btnSendGroupMessage = findViewById(R.id.btnSendGroupMessage);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbarGroupChat);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvGroupMessages.setLayoutManager(layoutManager);

        adapter = new GroupMessageAdapter(
                messageList,
                currentUserId,
                imagePath -> showImagePreviewDialog(imagePath),
                videoPath -> showVideoPreviewDialog(videoPath),
                filePath -> openPdfFile(filePath),
                audioPath -> playAudioMessage(audioPath),
                message -> toggleGroupMessageLike(message)
        );
        adapter.setOnGroupMessageLongClickListener(msg -> showGroupMessageOptionsDialog(msg));
        rvGroupMessages.setAdapter(adapter);
    }

    private void loadGroupInfo() {
        currentGroup = groupRepository.getGroupById(groupId);
        if (currentGroup == null) {
            finish();
            return;
        }

        isAdmin = groupRepository.isUserAdmin(groupId, currentUserId);

        tvGroupChatTitle.setText(currentGroup.getName());
        tvGroupChatSubTitle.setText(currentGroup.getMemberCount() + " membres");

        if (currentGroup.isLocked() && !isAdmin) {
            layoutGroupSend.setVisibility(View.GONE);
            layoutGroupLockedBanner.setVisibility(View.VISIBLE);
        } else {
            layoutGroupSend.setVisibility(View.VISIBLE);
            layoutGroupLockedBanner.setVisibility(View.GONE);
        }

        if (!currentGroup.isVoiceEnabled() && !isAdmin) {
            if (ibRecordAudio != null) ibRecordAudio.setVisibility(View.GONE);
        } else {
            if (ibRecordAudio != null) ibRecordAudio.setVisibility(View.VISIBLE);
        }
    }

    private void loadGroupMessages() {
        List<GroupMessage> list = groupRepository.getGroupMessages(groupId, currentUserId);
        messageList.clear();
        messageList.addAll(list);
        adapter.notifyDataSetChanged();
        if (!messageList.isEmpty()) {
            rvGroupMessages.smoothScrollToPosition(messageList.size() - 1);
        }
    }

    private void sendTextMessage() {
        String text = etGroupMessage.getText().toString().trim();
        if (text.isEmpty()) return;

        boolean sent = groupRepository.sendGroupMessage(groupId, currentUserId, text, null);
        if (sent) {
            etGroupMessage.setText("");
            loadGroupMessages();
        } else {
            Toasty.error(this, "Erreur d'envoi du message", Toasty.LENGTH_SHORT).show();
        }
    }

    private void sendGroupImageMessage(String imagePath) {
        boolean sent = groupRepository.sendGroupMessage(groupId, currentUserId, null, imagePath);
        if (sent) {
            loadGroupMessages();
        }
    }

    private void sendGroupVideoMessage(String videoPath) {
        boolean sent = groupRepository.sendGroupVideoMessage(groupId, currentUserId, videoPath);
        if (sent) {
            loadGroupMessages();
        }
    }

    private void sendGroupFileMessage(String filePath, String fileName) {
        boolean sent = groupRepository.sendGroupFileMessage(groupId, currentUserId, filePath, fileName);
        if (sent) {
            loadGroupMessages();
        } else {
            Toasty.error(this, "Erreur lors de l'envoi du document", Toasty.LENGTH_SHORT).show();
        }
    }

    private void startGroupVoiceRecording() {
        try {
            File dir = new File(getFilesDir(), "voice_messages");
            if (!dir.exists()) dir.mkdirs();
            currentAudioRecordingPath = new File(dir, "audio_" + System.currentTimeMillis() + ".3gp").getAbsolutePath();

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

    private void stopAndSendGroupVoice() {
        if (!isRecording) return;
        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
            if (ibRecordAudio != null) ibRecordAudio.setImageResource(android.R.drawable.ic_btn_speak_now);

            if (currentAudioRecordingPath != null) {
                boolean sent = groupRepository.sendGroupAudioMessage(groupId, currentUserId, currentAudioRecordingPath);
                if (sent) {
                    loadGroupMessages();
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

    private void showMediaSelectionOptions() {
        String[] options = {
                "📸 Prendre une photo (Recadrage)",
                "🖼️ Sélectionner plusieurs photos (Jusqu'à 10)",
                "🎥 Sélectionner et envoyer une vidéo",
                "📄 Envoyer un document PDF"
        };
        new AlertDialog.Builder(this)
                .setTitle("Joindre un fichier 📎")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchCropper();
                    } else if (which == 1) {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.setType("image/*");
                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        multipleImagePickerLauncher.launch(Intent.createChooser(intent, "Sélectionner des photos"));
                    } else if (which == 2) {
                        videoPickerLauncher.launch("video/*");
                    } else if (which == 3) {
                        pdfPickerLauncher.launch("application/pdf");
                    }
                })
                .show();
    }

    private void launchCropper() {
        CropImageOptions options = new CropImageOptions();
        options.imageSourceIncludeGallery = true;
        options.imageSourceIncludeCamera = true;
        options.guidelines = CropImageView.Guidelines.ON;
        options.activityTitle = "Joindre une photo au groupe";
        imagePickerLauncher.launch(new CropImageContractOptions(null, options));
    }

    private void showImagePreviewDialog(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) return;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);
        ImageButton ibDownload = dialogView.findViewById(R.id.ibDownloadPreview);
        if (ibDownload != null) {
            ibDownload.bringToFront();
            ibDownload.setOnClickListener(v -> ImageUtils.downloadImageToGallery(this, imagePath));
        }

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);
        if (imgEnlarged != null) {
            ImageUtils.enablePinchToZoom(imgEnlarged);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            });
        }

        dialog.show();
    }

    private void showGroupMessageOptionsDialog(GroupMessage msg) {
        if (msg.getSenderId() != currentUserId && !isAdmin) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Options du message")
                .setItems(new String[]{"🗑️ Supprimer le message"}, (dialog, which) -> {
                    if (which == 0) {
                        boolean deleted = groupRepository.deleteGroupMessage(groupId, msg.getId(), currentUserId);
                        if (deleted) {
                            Toasty.success(this, "Message supprimé", Toast.LENGTH_SHORT).show();
                            loadGroupMessages();
                        } else {
                            Toasty.error(this, "Impossible de supprimer : le délai de 10 heures est dépassé ou non autorisé", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .show();
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

    private void openPdfFile(String filePath) {
        if (filePath == null || filePath.trim().isEmpty()) return;
        try {
            File file = new File(filePath);
            Uri uri = androidx.core.content.FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (Exception e) {
            Toasty.error(this, "Aucune application trouvée pour ouvrir ce PDF", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleGroupMessageLike(GroupMessage message) {
        boolean isLiked = groupRepository.toggleLikeGroupMessage(message.getId(), currentUserId);
        if (isLiked) {
            Toasty.info(this, "J'aime ❤️", Toast.LENGTH_SHORT).show();
        }
        loadGroupMessages();
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 4001, 0, "📞 Appel vocal de groupe");
        menu.add(0, 4002, 0, "📹 Appel vidéo de groupe");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 4001) {
            String groupName = currentGroup != null ? currentGroup.getName() : "Groupe";
            Intent callIntent = new Intent(GroupChatActivity.this, CallActivity.class);
            callIntent.putExtra("target_name", "Groupe: " + groupName);
            callIntent.putExtra("is_video_call", false);
            startActivity(callIntent);
            return true;
        } else if (item.getItemId() == 4002) {
            String groupName = currentGroup != null ? currentGroup.getName() : "Groupe";
            Intent callIntent = new Intent(GroupChatActivity.this, CallActivity.class);
            callIntent.putExtra("target_name", "Groupe: " + groupName);
            callIntent.putExtra("is_video_call", true);
            startActivity(callIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startGroupVoiceRecording();
            } else {
                Toasty.error(this, "Permission microphone refusée", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Adaptateur RecyclerView pour l'affichage des messages de groupe.
     */
    private static class GroupMessageAdapter extends RecyclerView.Adapter<GroupMessageAdapter.ViewHolder> {

        private final List<GroupMessage> list;
        private final int currentUserId;
        private final OnGroupImageClickListener imageClickListener;
        private final OnGroupVideoClickListener videoClickListener;
        private final OnGroupFileClickListener fileClickListener;
        private final OnGroupAudioClickListener audioClickListener;
        private final OnGroupMessageLikeListener likeListener;
        private OnGroupMessageLongClickListener longClickListener;

        interface OnGroupImageClickListener {
            void onImageClick(String imagePath);
        }

        interface OnGroupVideoClickListener {
            void onVideoClick(String videoPath);
        }

        interface OnGroupFileClickListener {
            void onFileClick(String filePath);
        }

        interface OnGroupAudioClickListener {
            void onAudioClick(String audioPath);
        }

        interface OnGroupMessageLikeListener {
            void onMessageLike(GroupMessage message);
        }

        interface OnGroupMessageLongClickListener {
            void onMessageLongClick(GroupMessage message);
        }

        public void setOnGroupMessageLongClickListener(OnGroupMessageLongClickListener listener) {
            this.longClickListener = listener;
        }

        public GroupMessageAdapter(List<GroupMessage> list,
                                   int currentUserId,
                                   OnGroupImageClickListener imageClickListener,
                                   OnGroupVideoClickListener videoClickListener,
                                   OnGroupFileClickListener fileClickListener,
                                   OnGroupAudioClickListener audioClickListener,
                                   OnGroupMessageLikeListener likeListener) {
            this.list = list;
            this.currentUserId = currentUserId;
            this.imageClickListener = imageClickListener;
            this.videoClickListener = videoClickListener;
            this.fileClickListener = fileClickListener;
            this.audioClickListener = audioClickListener;
            this.likeListener = likeListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GroupMessage msg = list.get(position);

            boolean isMe = (msg.getSenderId() == currentUserId);

            if (isMe) {
                holder.layoutMessageContainer.setGravity(android.view.Gravity.END);
                holder.layoutBubble.setBackgroundResource(R.drawable.message_sent_background);
                holder.tvSenderName.setVisibility(View.GONE);
                holder.imgSenderAvatar.setVisibility(View.GONE);
            } else {
                holder.layoutMessageContainer.setGravity(android.view.Gravity.START);
                holder.layoutBubble.setBackgroundResource(R.drawable.message_received_background);
                holder.tvSenderName.setVisibility(View.VISIBLE);
                holder.tvSenderName.setText(msg.getSenderName());
                holder.imgSenderAvatar.setVisibility(View.VISIBLE);
                ImageUtils.loadProfileImage(holder.itemView.getContext(), msg.getSenderAvatar(), holder.imgSenderAvatar);
            }

            // Image
            if (msg.getImagePath() != null && !msg.getImagePath().trim().isEmpty()) {
                holder.imgGroupMessagePhoto.setVisibility(View.VISIBLE);
                ImageUtils.loadFullImage(holder.itemView.getContext(), msg.getImagePath(), holder.imgGroupMessagePhoto);
                holder.imgGroupMessagePhoto.setOnClickListener(v -> {
                    if (imageClickListener != null) imageClickListener.onImageClick(msg.getImagePath());
                });
            } else {
                holder.imgGroupMessagePhoto.setVisibility(View.GONE);
            }

            // Video
            if (msg.getVideoPath() != null && !msg.getVideoPath().trim().isEmpty() && holder.layoutGroupVideoContainer != null && holder.imgGroupVideoThumbnail != null) {
                holder.layoutGroupVideoContainer.setVisibility(View.VISIBLE);
                ImageUtils.loadVideoThumbnail(holder.itemView.getContext(), msg.getVideoPath(), holder.imgGroupVideoThumbnail);
                holder.layoutGroupVideoContainer.setOnClickListener(v -> {
                    if (videoClickListener != null) videoClickListener.onVideoClick(msg.getVideoPath());
                });
            } else if (holder.layoutGroupVideoContainer != null) {
                holder.layoutGroupVideoContainer.setVisibility(View.GONE);
            }

            // PDF File
            if (msg.getFilePath() != null && !msg.getFilePath().trim().isEmpty() && holder.layoutGroupFileContainer != null && holder.tvGroupFileName != null) {
                holder.layoutGroupFileContainer.setVisibility(View.VISIBLE);
                holder.tvGroupFileName.setText(msg.getFileName());
                holder.layoutGroupFileContainer.setOnClickListener(v -> {
                    if (fileClickListener != null) fileClickListener.onFileClick(msg.getFilePath());
                });
            } else if (holder.layoutGroupFileContainer != null) {
                holder.layoutGroupFileContainer.setVisibility(View.GONE);
            }

            // Audio
            if (msg.getAudioPath() != null && !msg.getAudioPath().trim().isEmpty() && holder.layoutAudioContainer != null && holder.ibAudioPlay != null) {
                holder.layoutAudioContainer.setVisibility(View.VISIBLE);
                if (holder.tvAudioDuration != null) {
                    holder.tvAudioDuration.setText(ImageUtils.getAudioDuration(msg.getAudioPath()));
                }
                holder.ibAudioPlay.setOnClickListener(v -> {
                    if (audioClickListener != null) audioClickListener.onAudioClick(msg.getAudioPath());
                });
            } else if (holder.layoutAudioContainer != null) {
                holder.layoutAudioContainer.setVisibility(View.GONE);
            }

            // Text
            if (msg.getMessage() != null && !msg.getMessage().trim().isEmpty()) {
                holder.tvGroupMessageText.setVisibility(View.VISIBLE);
                holder.tvGroupMessageText.setText(msg.getMessage());
            } else {
                holder.tvGroupMessageText.setVisibility(View.GONE);
            }

            holder.tvGroupMessageTime.setText(msg.getCreatedAt() != null ? msg.getCreatedAt() : "");

            // Like / Long Press Reaction
            holder.layoutBubble.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onMessageLongClick(msg);
                    return true;
                }
                if (likeListener != null) {
                    likeListener.onMessageLike(msg);
                    return true;
                }
                return false;
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout layoutMessageContainer;
            LinearLayout layoutBubble;
            ImageView imgSenderAvatar;
            TextView tvSenderName;
            ImageView imgGroupMessagePhoto;
            View layoutGroupVideoContainer;
            ImageView imgGroupVideoThumbnail;
            View layoutGroupFileContainer;
            TextView tvGroupFileName;
            View layoutAudioContainer;
            View ibAudioPlay;
            TextView tvAudioDuration;
            TextView tvGroupMessageText;
            TextView tvGroupMessageTime;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                layoutMessageContainer = itemView.findViewById(R.id.layoutMessageContainer);
                layoutBubble = itemView.findViewById(R.id.layoutBubble);
                imgSenderAvatar = itemView.findViewById(R.id.imgSenderAvatar);
                tvSenderName = itemView.findViewById(R.id.tvSenderName);
                imgGroupMessagePhoto = itemView.findViewById(R.id.imgGroupMessagePhoto);
                layoutGroupVideoContainer = itemView.findViewById(R.id.layoutGroupVideoContainer);
                imgGroupVideoThumbnail = itemView.findViewById(R.id.imgVideoThumbnail);
                layoutGroupFileContainer = itemView.findViewById(R.id.layoutGroupFileContainer);
                tvGroupFileName = itemView.findViewById(R.id.tvGroupFileName);
                layoutAudioContainer = itemView.findViewById(R.id.layoutAudioContainer);
                ibAudioPlay = itemView.findViewById(R.id.ibAudioPlay);
                tvAudioDuration = itemView.findViewById(R.id.tvAudioDuration);
                tvGroupMessageText = itemView.findViewById(R.id.tvGroupMessageText);
                tvGroupMessageTime = itemView.findViewById(R.id.tvGroupMessageTime);
            }
        }
    }
}

