package td.teladoumbaobabtd;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import es.dmoral.toasty.Toasty;
import td.teladoumbaobabtd.repository.ConversationRepository;
import td.teladoumbaobabtd.repository.UserRepository;

public class AffichageMsg extends AppCompatActivity {

    private RecyclerView rvConversations;
    private View layoutEmptyConversations;
    private Button btnStartFirstChat;

    private UserRepository userRepository;
    private ConversationRepository conversationRepository;
    private SessionManager sessionManager;

    private ConversationAdapter adapter;

    private final List<ConversationItem> conversationList = new ArrayList<>();
    private final List<ConversationItem> fullConversationList = new ArrayList<>();

    private Handler handler;
    private Runnable refreshRunnable;
    private String currentSearchQuery = "";
    private final Map<Integer, Integer> lastUnreadCounts = new HashMap<>();
    private boolean showingArchived = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_affichage_msg);

        Toolbar toolbar = findViewById(R.id.toolbarAffichageMsg);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rvConversations = findViewById(R.id.rvConversations);
        layoutEmptyConversations = findViewById(R.id.layoutEmptyConversations);
        btnStartFirstChat = findViewById(R.id.btnStartFirstChat);
        FloatingActionButton fabNewConversation = findViewById(R.id.fabNewConversation);

        if (btnStartFirstChat != null) {
            btnStartFirstChat.setOnClickListener(v -> showUserSelectionDialog());
        }

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(AffichageMsg.this, LoginActivity.class));
            finish();
            return;
        }

        userRepository = new UserRepository(this);
        conversationRepository = new ConversationRepository(this);

        setupRecyclerView();
        setupAdapter();

        fabNewConversation.setOnClickListener(v -> showUserSelectionDialog());

        loadConversations();
        setupAutoRefresh();
    }

    private void setupRecyclerView() {
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setHasFixedSize(true);
        rvConversations.setItemAnimator(new DefaultItemAnimator());
    }

    private void setupAdapter() {
        adapter = new ConversationAdapter(
                conversationList,
                conversation -> {
                    Intent intent = new Intent(AffichageMsg.this, ChatActivity.class);
                    intent.putExtra("conversation_id", conversation.getConversationId());
                    intent.putExtra("receiver_name", conversation.getUserName());
                    intent.putExtra("receiver_profile_image", conversation.getProfileImage());
                    startActivity(intent);
                },
                conversation -> showConversationOptionsDialog(conversation)
        );

        rvConversations.setAdapter(adapter);
    }

    private void showConversationOptionsDialog(ConversationItem conversation) {
        String archiveOption = showingArchived ? "Désarchiver la conversation" : "Archiver la conversation";
        String[] options = new String[]{archiveOption, "Supprimer la conversation"};

        new AlertDialog.Builder(this)
                .setTitle(conversation.getUserName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        conversationRepository.toggleArchiveConversation(conversation.getConversationId(), !showingArchived);
                        Toasty.success(AffichageMsg.this, showingArchived ? "Conversation désarchivée" : "Conversation archivée", Toasty.LENGTH_SHORT).show();
                        loadConversations();
                    } else if (which == 1) {
                        showDeleteConversationDialog(conversation);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteConversationDialog(ConversationItem conversation) {
        new AlertDialog.Builder(this)
                .setTitle("Supprimer la conversation")
                .setMessage("Voulez-vous vraiment supprimer la conversation avec " + conversation.getUserName() + " ?")
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    boolean deleted = conversationRepository.deleteConversation(conversation.getConversationId());
                    if (deleted) {
                        Toasty.success(AffichageMsg.this, "Conversation supprimée", Toasty.LENGTH_SHORT).show();
                        loadConversations();
                    } else {
                        Toasty.warning(AffichageMsg.this, "Erreur de suppression", Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadConversations() {
        List<ConversationItem> newList = conversationRepository.getConversations(sessionManager.getUserId(), showingArchived);

        for (ConversationItem item : newList) {
            int previousUnread = lastUnreadCounts.containsKey(item.getConversationId()) ? lastUnreadCounts.get(item.getConversationId()) : 0;
            if (item.getUnreadCount() > previousUnread && item.getUnreadCount() > 0) {
                NotificationHelper.showNotification(
                        this,
                        "Nouveau message de " + item.getUserName(),
                        item.getLastMessage(),
                        item.getConversationId(),
                        item.getUserName()
                );
            }
            lastUnreadCounts.put(item.getConversationId(), item.getUnreadCount());
        }

        fullConversationList.clear();
        fullConversationList.addAll(newList);

        filterConversations(currentSearchQuery);
    }

    private void filterConversations(String query) {
        currentSearchQuery = query;
        List<ConversationItem> displayList;
        if (query == null || query.trim().isEmpty()) {
            displayList = fullConversationList;
        } else {
            String lowerQuery = query.toLowerCase().trim();
            displayList = new ArrayList<>();
            for (ConversationItem item : fullConversationList) {
                if (item.getUserName().toLowerCase().contains(lowerQuery) ||
                        item.getLastMessage().toLowerCase().contains(lowerQuery)) {
                    displayList.add(item);
                }
            }
        }
        adapter.updateData(displayList);
        updateEmptyState(displayList.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        if (layoutEmptyConversations != null && rvConversations != null) {
            if (isEmpty) {
                layoutEmptyConversations.setVisibility(View.VISIBLE);
                rvConversations.setVisibility(View.GONE);
            } else {
                layoutEmptyConversations.setVisibility(View.GONE);
                rvConversations.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showUserSelectionDialog() {
        List<User> userList = userRepository.getAllUsersExcept(sessionManager.getUserId());

        List<String> displayNames = new ArrayList<>();
        List<User> filteredUsers = new ArrayList<>();

        for (User u : userList) {
            displayNames.add(u.getName());
            filteredUsers.add(u);
        }

        ArrayAdapter<String> listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayNames);

        ListView listView = new ListView(this);
        listView.setAdapter(listAdapter);

        EditText etSearchContact = new EditText(this);
        etSearchContact.setHint("Rechercher un contact...");
        etSearchContact.setPadding(32, 24, 32, 24);

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.addView(etSearchContact);
        container.addView(listView);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.select_contact)
                .setView(container)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        etSearchContact.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().toLowerCase().trim();
                displayNames.clear();
                filteredUsers.clear();

                for (User u : userList) {
                    if (u.getName().toLowerCase().contains(q) || u.getEmail().toLowerCase().contains(q)) {
                        displayNames.add(u.getName());
                        filteredUsers.add(u);
                    }
                }
                listAdapter.notifyDataSetChanged();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            if (position < filteredUsers.size()) {
                User selectedUser = filteredUsers.get(position);
                if (selectedUser != null) {
                    int conversationId = conversationRepository.createConversationIfNotExists(
                            sessionManager.getUserId(),
                            selectedUser.getId()
                    );

                    Intent intent = new Intent(AffichageMsg.this, ChatActivity.class);
                    intent.putExtra("conversation_id", conversationId);
                    intent.putExtra("receiver_name", selectedUser.getName());
                    intent.putExtra("receiver_profile_image", selectedUser.getProfileImage());
                    startActivity(intent);
                }
            }
        });

        dialog.show();
    }

    private void setupAutoRefresh() {
        handler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                loadConversations();
                handler.postDelayed(this, 2000);
            }
        };
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setQueryHint("Rechercher...");
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        filterConversations(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        filterConversations(newText);
                        return true;
                    }
                });
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_profile) {
            startActivity(new Intent(AffichageMsg.this, ProfileActivity.class));
            return true;
        } else if (id == R.id.action_archived) {
            showingArchived = !showingArchived;
            item.setTitle(showingArchived ? "Conversations actives" : "Conversations archivées");
            loadConversations();
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(AffichageMsg.this, ParametreActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
            handler.post(refreshRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
