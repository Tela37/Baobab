package td.teladoumbaobabtd;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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

import td.teladoumbaobabtd.repository.GroupRepository;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Écran de discussion de groupe.
 * Gère l'affichage des messages, la sélection multi-images (jusqu'à 10), l'envoi de vidéo,
 * les réactions/likes sur les messages et l'état de verrouillage administrateur.
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

    private Handler handler;
    private Runnable refreshRunnable;

    private final ActivityResultLauncher<CropImageContractOptions> imagePickerLauncher =
            registerForActivityResult(new CropImageContract(), (CropImageView.CropResult result) -> {
                if (result.isSuccessful()) {
                    Uri uri = result.getUriContent();
                    if (uri != null) {
                        String savedPath = ImageUtils.saveImageToInternalStorage(this, uri);
                        if (savedPath != null) {
                            sendGroupImageMessage(savedPath);
                        }
                    }
                }
            });

    private final ActivityResultLauncher<String> multipleImagePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) {
                    int count = Math.min(uris.size(), 10);
                    if (uris.size() > 10) {
                        Toasty.info(this, "Seules les 10 premières photos ont été envoyées", Toasty.LENGTH_SHORT).show();
                    }
                    for (int i = 0; i < count; i++) {
                        String savedPath = ImageUtils.saveImageToInternalStorage(this, uris.get(i));
                        if (savedPath != null) {
                            sendGroupImageMessage(savedPath);
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

        configureAutoRefresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadGroupInfo();
        loadGroupMessages();
        if (handler != null) {
            handler.post(refreshRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null) {
            handler.removeCallbacks(refreshRunnable);
        }
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
                message -> toggleGroupMessageLike(message)
        );
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

    private void showMediaSelectionOptions() {
        String[] options = {"📸 Prendre une photo (Recadrage)", "🖼️ Sélectionner plusieurs photos (Jusqu'à 10)", "🎥 Sélectionner et envoyer une vidéo"};
        new AlertDialog.Builder(this)
                .setTitle("Joindre un fichier 📎")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        launchCropper();
                    } else if (which == 1) {
                        multipleImagePickerLauncher.launch("image/*");
                    } else if (which == 2) {
                        videoPickerLauncher.launch("video/*");
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

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);
        if (imgEnlarged != null) {
            ImageUtils.enablePinchToZoom(imgEnlarged);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.bringToFront();
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
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

    private void toggleGroupMessageLike(GroupMessage message) {
        boolean isLiked = groupRepository.toggleLikeGroupMessage(message.getId(), currentUserId);
        if (isLiked) {
            Toasty.info(this, "J'aime ❤️", Toasty.LENGTH_SHORT).show();
        }
        loadGroupMessages();
    }

    private void configureAutoRefresh() {
        handler = new Handler();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadGroupInfo();
                loadGroupMessages();
                handler.postDelayed(this, 2000);
            }
        };
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class GroupMessageAdapter extends RecyclerView.Adapter<GroupMessageAdapter.ViewHolder> {

        private final List<GroupMessage> list;
        private final int currentUserId;
        private final OnGroupImageClickListener imageClickListener;
        private final OnGroupVideoClickListener videoClickListener;
        private final OnGroupMessageLikeListener likeListener;

        interface OnGroupImageClickListener {
            void onImageClick(String imagePath);
        }

        interface OnGroupVideoClickListener {
            void onVideoClick(String videoPath);
        }

        interface OnGroupMessageLikeListener {
            void onMessageLike(GroupMessage message);
        }

        public GroupMessageAdapter(List<GroupMessage> list,
                                   int currentUserId,
                                   OnGroupImageClickListener imageClickListener,
                                   OnGroupVideoClickListener videoClickListener,
                                   OnGroupMessageLikeListener likeListener) {
            this.list = list;
            this.currentUserId = currentUserId;
            this.imageClickListener = imageClickListener;
            this.videoClickListener = videoClickListener;
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
                imgGroupVideoThumbnail = itemView.findViewById(R.id.imgGroupVideoThumbnail);
                tvGroupMessageText = itemView.findViewById(R.id.tvGroupMessageText);
                tvGroupMessageTime = itemView.findViewById(R.id.tvGroupMessageTime);
            }
        }
    }
}
