package td.teladoumbaobabtd;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import td.teladoumbaobabtd.repository.ConversationRepository;
import td.teladoumbaobabtd.repository.MessageRepository;

public class ChatActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ImageView imgHeaderAvatar;
    private TextView tvHeaderName;
    private TextView tvHeaderStatus;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;

    private MessageRepository messageRepository;
    private ConversationRepository conversationRepository;
    private SessionManager sessionManager;

    private ChatAdapter chatAdapter;
    private final List<MessageModel> messagesList = new ArrayList<>();

    private int conversationId = -1;
    private int currentUserId = -1;
    private int receiverId = -1;
    private String receiverName;
    private String receiverProfileImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        messageRepository = new MessageRepository(this);
        conversationRepository = new ConversationRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        parseIntentExtras();
        setupToolbar();
        setupRecyclerView();

        btnSend.setOnClickListener(v -> sendMessage());

        loadMessages();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        imgHeaderAvatar = findViewById(R.id.imgHeaderAvatar);
        tvHeaderName = findViewById(R.id.tvHeaderName);
        tvHeaderStatus = findViewById(R.id.tvHeaderStatus);
        rvMessages = findViewById(R.id.rvMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
    }

    private void parseIntentExtras() {
        Intent intent = getIntent();
        if (intent == null) return;

        conversationId = intent.getIntExtra("conversation_id", -1);
        if (conversationId == -1) {
            conversationId = intent.getIntExtra("conversationId", -1);
        }

        receiverId = intent.getIntExtra("receiverId", -1);
        if (receiverId == -1) {
            receiverId = intent.getIntExtra("receiver_id", -1);
        }

        receiverName = intent.getStringExtra("receiver_name");
        if (receiverName == null) {
            receiverName = intent.getStringExtra("receiverName");
        }

        receiverProfileImage = intent.getStringExtra("receiver_profile_image");

        if (conversationId == -1 && receiverId != -1) {
            conversationId = conversationRepository.createConversationIfNotExists(currentUserId, receiverId);
        }
    }

    private void setupToolbar() {
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
        }

        if (tvHeaderName != null) {
            tvHeaderName.setText(receiverName != null ? receiverName : "Discussion");
        }

        if (imgHeaderAvatar != null) {
            ImageUtils.loadProfileImage(this, receiverProfileImage, imgHeaderAvatar);
        }

        if (tvHeaderStatus != null) {
            tvHeaderStatus.setVisibility(View.GONE);
        }
    }

    private void setupRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);

        chatAdapter = new ChatAdapter(messagesList, currentUserId, this::showMessageOptions);
        rvMessages.setAdapter(chatAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        markMessagesAsRead();
        loadMessages();
    }

    private void markMessagesAsRead() {
        if (conversationId != -1) {
            messageRepository.markConversationAsRead(conversationId, currentUserId);
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }

        if (conversationId == -1 && receiverId != -1) {
            conversationId = conversationRepository.createConversationIfNotExists(currentUserId, receiverId);
        }

        if (conversationId == -1) {
            return;
        }

        messageRepository.sendMessage(conversationId, currentUserId, text);
        etMessage.setText("");
        loadMessages();
    }

    private void loadMessages() {
        if (conversationId == -1) {
            return;
        }

        List<MessageModel> newMessages = messageRepository.getMessages(conversationId);
        messagesList.clear();
        messagesList.addAll(newMessages);
        chatAdapter.notifyDataSetChanged();

        if (!messagesList.isEmpty()) {
            rvMessages.scrollToPosition(messagesList.size() - 1);
        }
    }

    private void showMessageOptions(MessageModel message) {
        String[] options;
        if (message.getSenderId() == currentUserId) {
            options = new String[]{"Modifier", "Supprimer"};
        } else {
            options = new String[]{"Supprimer"};
        }

        new AlertDialog.Builder(this)
                .setTitle("Options du message")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if ("Modifier".equals(selected)) {
                        editMessage(message);
                    } else if ("Supprimer".equals(selected)) {
                        confirmDeleteMessage(message);
                    }
                })
                .show();
    }

    private void editMessage(MessageModel message) {
        EditText input = new EditText(this);
        input.setText(message.getMessage());

        new AlertDialog.Builder(this)
                .setTitle("Modifier le message")
                .setView(input)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String newText = input.getText().toString().trim();
                    if (!newText.isEmpty()) {
                        messageRepository.updateMessage(message.getId(), newText);
                        loadMessages();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void confirmDeleteMessage(MessageModel message) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer le message")
                .setMessage("Voulez-vous vraiment supprimer ce message ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    messageRepository.deleteMessage(message.getId());
                    loadMessages();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
