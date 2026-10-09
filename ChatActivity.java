package td.teladoumbaobabtd;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
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
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import td.teladoumbaobabtd.repository.MessageRepository;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Activité principale de discussion en direct (Chat).
 * Gère l'envoi/réception de messages et photos, l'affichage de l'indicateur de saisie,
 * les statuts de lecture (✓/✓✓ bleu), les réactions émojis et les réponses ciblées (Quote Reply).
 */
public class ChatActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private TextView tvChatName;
    private TextView tvTypingIndicator;
    private ImageView imgChatAvatar;

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
    private SessionManager sessionManager;

    private ChatAdapter adapter;
    private List<Message> messageList;

    private int conversationId;
    private String receiverName;
    private String receiverProfileImage;

    private Handler handler;
    private Runnable refreshRunnable;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        initViews();

        messageRepository = new MessageRepository(this);
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
                imagePath -> showImagePreviewDialog(imagePath)
        );

        rvMessages.setAdapter(adapter);

        loadMessages();

        btnSend.setOnClickListener(v -> sendMessage());
        ibAttachImage.setOnClickListener(v -> launchImageCropper());
        if (ibCancelReply != null) {
            ibCancelReply.setOnClickListener(v -> cancelQuoteReply());
        }

        configureAutoRefresh();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvChatName = findViewById(R.id.tvChatName);
        tvTypingIndicator = findViewById(R.id.tvTypingIndicator);
        imgChatAvatar = findViewById(R.id.imgChatAvatar);

        rvMessages = findViewById(R.id.rvMessages);

        etMessage = findViewById(R.id.etMessage);
        ibAttachImage = findViewById(R.id.ibAttachImage);
        btnSend = findViewById(R.id.btnSend);

        tvCounter = findViewById(R.id.tvCounter);
        progressChars = findViewById(R.id.progressChars);

        layoutReplyBanner = findViewById(R.id.layoutReplyBanner);
        tvReplyTextPreview = findViewById(R.id.tvReplyTextPreview);
        ibCancelReply = findViewById(R.id.ibCancelReply);
    }

    private void readIntentData() {
        conversationId = getIntent().getIntExtra("conversation_id", -1);
        receiverName = getIntent().getStringExtra("receiver_name");
        receiverProfileImage = getIntent().getStringExtra("receiver_profile_image");

        if (receiverName != null && !receiverName.isEmpty()) {
            tvChatName.setText(receiverName);
        } else {
            tvChatName.setText("Discussion");
        }

        ImageUtils.loadProfileImage(this, receiverProfileImage, imgChatAvatar);
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

                // Indicateur de saisie ("En train d'écrire...")
                if (tvTypingIndicator != null) {
                    tvTypingIndicator.setVisibility(length > 0 ? View.VISIBLE : View.GONE);
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
        String savedPath = ImageUtils.saveImageToInternalStorage(this, imageUri);
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
                        boolean deleted = messageRepository.deleteMessage(message.getId());
                        if (deleted) {
                            Toasty.success(ChatActivity.this, "Message supprimé", Toasty.LENGTH_SHORT).show();
                            loadMessages();
                        }
                    }
                })
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

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_image_preview, null);
        ImageView imgEnlarged = dialogView.findViewById(R.id.imgEnlarged);
        ImageButton ibClose = dialogView.findViewById(R.id.ibClosePreview);

        ImageUtils.loadFullImage(this, imagePath, imgEnlarged);

        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .setView(dialogView)
                .create();

        if (ibClose != null) {
            ibClose.setOnClickListener(v -> dialog.dismiss());
        }
        dialogView.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void loadMessages() {
        // Marquer automatiquement les messages reçus comme lus (✓✓ bleu)
        messageRepository.markMessagesAsRead(conversationId, sessionManager.getUserId());

        List<Message> newMessages = messageRepository.getMessages(conversationId);
        messageList.clear();
        messageList.addAll(newMessages);
        adapter.notifyDataSetChanged();
        scrollToBottom();
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

    private void configureAutoRefresh() {
        handler = new Handler();
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadMessages();
                handler.postDelayed(this, 1500);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    public void finish() {
        AdMobManager.showInterstitialAd(this, super::finish);
    }
}