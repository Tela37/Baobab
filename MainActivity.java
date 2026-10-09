package td.teladoumbaobabtd;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import td.teladoumbaobabtd.repository.ConversationRepository;
import td.teladoumbaobabtd.repository.UserRepository;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import es.dmoral.toasty.Toasty;

/**
 * Activité principale gérant la liste des discussions/conversations.
 * Intègre la recherche, la suppression groupée (ActionMode), l'état vide avec invitation à discuter,
 * la protection des discussions privées par Empreinte biométrique ou Code PIN, et le rafraîchissement automatique.
 */
public class MainActivity extends AppCompatActivity {

    private RecyclerView rvConversations;
    private View layoutEmptyState;
    private Button btnStartDiscussion;

    private UserRepository userRepository;
    private ConversationRepository conversationRepository;
    private SessionManager sessionManager;

    private ConversationAdapter adapter;

    private final List<ConversationItem> conversationList = new ArrayList<>();
    private final List<ConversationItem> fullConversationList = new ArrayList<>();

    private Handler handler;
    private Runnable refreshRunnable;
    private String currentSearchQuery = "";
    private int lastTotalUnreadCount = -1;

    private ActionMode actionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        rvConversations = findViewById(R.id.rvConversations);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        btnStartDiscussion = findViewById(R.id.btnStartDiscussion);
        FloatingActionButton fabNewConversation = findViewById(R.id.fabNewConversation);

        if (btnStartDiscussion != null) {
            btnStartDiscussion.setOnClickListener(v -> showUserSelectionDialog());
        }

        sessionManager = new SessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
            return;
        }

        userRepository = new UserRepository(this);
        conversationRepository = new ConversationRepository(this);

        checkPermissions();
        setupRecyclerView();
        setupAdapter();

        fabNewConversation.setOnClickListener(v -> showUserSelectionDialog());

        loadConversations();
        setupAutoRefresh();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
    }

    private void setupRecyclerView() {
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setHasFixedSize(true);
        rvConversations.setItemAnimator(new DefaultItemAnimator());
    }

    private void setupAdapter() {
        adapter = new ConversationAdapter(
                conversationList,
                conversation -> openConversationWithCheck(conversation),
                conversation -> showConversationOptionsDialog(conversation)
        );

        adapter.setOnSelectionChangedListener(count -> {
            if (actionMode != null) {
                actionMode.setTitle(count + " sélectionnée(s)");
            }
        });

        rvConversations.setAdapter(adapter);
    }

    private void openConversationWithCheck(ConversationItem conversation) {
        if (conversation.isPrivate()) {
            if (!sessionManager.hasPrivatePin()) {
                showSetPinDialog(() -> openChatActivity(conversation));
            } else {
                BiometricHelper.showBiometricPrompt(
                        this,
                        () -> openChatActivity(conversation),
                        () -> showVerifyPinDialog(() -> openChatActivity(conversation))
                );
            }
        } else {
            openChatActivity(conversation);
        }
    }

    private void openChatActivity(ConversationItem conversation) {
        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
        intent.putExtra("conversation_id", conversation.getConversationId());
        intent.putExtra("receiver_name", conversation.getUserName());
        intent.putExtra("receiver_profile_image", conversation.getProfileImage());
        startActivity(intent);
    }

    private void showSetPinDialog(Runnable onSuccess) {
        final EditText etPin = new EditText(this);
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setHint("Entrez un code PIN (4 chiffres)");

        new AlertDialog.Builder(this)
                .setTitle("Créer un code PIN 🔒")
                .setMessage("Définissez un code PIN pour protéger vos conversations privées :")
                .setView(etPin)
                .setPositiveButton("Enregistrer", (dialog, which) -> {
                    String pin = etPin.getText().toString().trim();
                    if (pin.length() < 4) {
                        Toasty.warning(this, "Le code PIN doit contenir au moins 4 chiffres", Toasty.LENGTH_SHORT).show();
                    } else {
                        sessionManager.setPrivatePin(pin);
                        Toasty.success(this, "Code PIN configuré !", Toasty.LENGTH_SHORT).show();
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showVerifyPinDialog(Runnable onSuccess) {
        final EditText etPin = new EditText(this);
        etPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        etPin.setHint("Code PIN à 4 chiffres");

        new AlertDialog.Builder(this)
                .setTitle("Code PIN requis 🔒")
                .setMessage("Saisissez votre code PIN pour déverrouiller l'accès :")
                .setView(etPin)
                .setPositiveButton("Valider", (dialog, which) -> {
                    String inputPin = etPin.getText().toString().trim();
                    if (inputPin.equals(sessionManager.getPrivatePin())) {
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    } else {
                        Toasty.error(this, "Code PIN incorrect", Toasty.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private final ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            menu.add(0, 1001, 0, "Tout sélectionner").setIcon(android.R.drawable.ic_menu_agenda).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, 1002, 1, "Supprimer").setIcon(android.R.drawable.ic_menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == 1001) {
                if (adapter != null) {
                    adapter.selectAll();
                }
                return true;
            } else if (itemId == 1002) {
                if (adapter != null && adapter.getSelectedCount() > 0) {
                    confirmAndBatchDelete();
                } else {
                    Toasty.warning(MainActivity.this, "Aucune conversation sélectionnée", Toasty.LENGTH_SHORT).show();
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (adapter != null) {
                adapter.setSelectionMode(false);
            }
            actionMode = null;
        }
    };

    private void confirmAndBatchDelete() {
        Set<Integer> selectedIds = adapter.getSelectedIds();
        int count = selectedIds.size();

        new AlertDialog.Builder(this)
                .setTitle("Suppression groupée")
                .setMessage("Voulez-vous vraiment supprimer " + count + " discussion(s) sélectionnée(s) ?")
                .setPositiveButton("Supprimer", (dialog, which) -> {
                    for (int convId : selectedIds) {
                        conversationRepository.deleteConversation(convId);
                    }
                    if (actionMode != null) {
                        actionMode.finish();
                    }
                    Toasty.success(MainActivity.this, count + " discussion(s) supprimée(s)", Toasty.LENGTH_SHORT).show();
                    loadConversations();
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showConversationOptionsDialog(ConversationItem conversation) {
        String selectOption = "Sélection multiple pour suppression 🛑";
        String pinOption = conversation.isPrivate() ? "Déverrouiller / Retirer du mode privé 🔓" : "Protéger par code PIN (Rendre privée) 🔒";
        String pinTopOption = conversation.isPinned() ? "Désépingler de la liste 📌" : "Épingler en haut 📌";
        String deleteOption = "Supprimer la discussion 🗑️";

        String[] options = new String[]{selectOption, pinOption, pinTopOption, deleteOption};

        new AlertDialog.Builder(this)
                .setTitle(conversation.getUserName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        adapter.setSelectionMode(true);
                        adapter.toggleSelection(conversation.getConversationId());
                        actionMode = startSupportActionMode(actionModeCallback);
                        if (actionMode != null) {
                            actionMode.setTitle(adapter.getSelectedCount() + " sélectionnée(s)");
                        }
                    } else if (which == 1) {
                        if (!conversation.isPrivate()) {
                            if (!sessionManager.hasPrivatePin()) {
                                showSetPinDialog(() -> {
                                    conversationRepository.setConversationPrivate(conversation.getConversationId(), true);
                                    Toasty.success(MainActivity.this, "Conversation protégée par PIN 🔒", Toasty.LENGTH_SHORT).show();
                                    loadConversations();
                                });
                            } else {
                                conversationRepository.setConversationPrivate(conversation.getConversationId(), true);
                                Toasty.success(MainActivity.this, "Conversation protégée par PIN 🔒", Toasty.LENGTH_SHORT).show();
                                loadConversations();
                            }
                        } else {
                            showVerifyPinDialog(() -> {
                                conversationRepository.setConversationPrivate(conversation.getConversationId(), false);
                                Toasty.info(MainActivity.this, "Conversation retirée du mode privé 🔓", Toasty.LENGTH_SHORT).show();
                                loadConversations();
                            });
                        }
                    } else if (which == 2) {
                        boolean newPinned = !conversation.isPinned();
                        conversationRepository.setConversationPinned(conversation.getConversationId(), newPinned);
                        Toasty.info(MainActivity.this, newPinned ? "Conversation épinglée 📌" : "Conversation désépinglée", Toasty.LENGTH_SHORT).show();
                        loadConversations();
                    } else if (which == 3) {
                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Supprimer la conversation")
                                .setMessage("Voulez-vous vraiment supprimer la discussion avec " + conversation.getUserName() + " ?")
                                .setPositiveButton("Supprimer", (d, w) -> {
                                    conversationRepository.deleteConversation(conversation.getConversationId());
                                    Toasty.success(MainActivity.this, "Discussion supprimée", Toasty.LENGTH_SHORT).show();
                                    loadConversations();
                                })
                                .setNegativeButton("Annuler", null)
                                .show();
                    }
                })
                .show();
    }

    private void loadConversations() {
        List<ConversationItem> newList = conversationRepository.getConversations(sessionManager.getUserId());
        
        int currentUnread = 0;
        for (ConversationItem item : newList) {
            currentUnread += item.getUnreadCount();
            if (lastTotalUnreadCount != -1 && item.getUnreadCount() > 0) {
                NotificationHelper.showNotification(
                        this,
                        "Nouveau message de " + item.getUserName(),
                        item.getLastMessage(),
                        item.getConversationId(),
                        item.getUserName()
                );
            }
        }

        if (lastTotalUnreadCount == -1) {
            lastTotalUnreadCount = currentUnread;
        } else {
            lastTotalUnreadCount = currentUnread;
        }

        fullConversationList.clear();
        fullConversationList.addAll(newList);

        updateEmptyState();
        filterConversations(currentSearchQuery);
    }

    private void updateEmptyState() {
        boolean isEmpty = fullConversationList.isEmpty();
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (rvConversations != null) {
            rvConversations.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        invalidateOptionsMenu();
    }

    private void filterConversations(String query) {
        currentSearchQuery = query;
        if (query == null || query.trim().isEmpty()) {
            adapter.updateData(fullConversationList);
            return;
        }

        String lowerQuery = query.toLowerCase().trim();
        List<ConversationItem> filtered = new ArrayList<>();
        for (ConversationItem item : fullConversationList) {
            if (item.getUserName().toLowerCase().contains(lowerQuery) ||
                    item.getLastMessage().toLowerCase().contains(lowerQuery)) {
                filtered.add(item);
            }
        }
        adapter.updateData(filtered);
    }

    private void showUserSelectionDialog() {
        List<User> userList = userRepository.getAllUsersExcept(sessionManager.getUserId());

        if (userList.isEmpty()) {
            Toast.makeText(this, "Aucun autre utilisateur disponible", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> options = new ArrayList<>();
        for (User user : userList) {
            options.add(user.getName() + " (" + user.getEmail() + ")");
        }

        String[] optionsArray = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_contact)
                .setItems(optionsArray, (dialog, which) -> {
                    User selectedUser = userList.get(which);
                    int conversationId = conversationRepository.createConversationIfNotExists(
                            sessionManager.getUserId(),
                            selectedUser.getId()
                    );

                    Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                    intent.putExtra("conversation_id", conversationId);
                    intent.putExtra("receiver_name", selectedUser.getName());
                    intent.putExtra("receiver_profile_image", selectedUser.getProfileImage());
                    startActivity(intent);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
        getMenuInflater().inflate(R.menu.activity_main_menu, menu);

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
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem privateChatsItem = menu.findItem(R.id.action_private_chats);
        if (privateChatsItem != null) {
            privateChatsItem.setVisible(!fullConversationList.isEmpty());
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            Intent intent = new Intent(MainActivity.this, AcceuilActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
            return true;
        } else if (id == R.id.action_private_chats) {
            if (!sessionManager.hasPrivatePin()) {
                showSetPinDialog(null);
            } else {
                BiometricHelper.showBiometricPrompt(
                        this,
                        () -> Toasty.success(MainActivity.this, "Mode privé déverrouillé 🔒", Toasty.LENGTH_SHORT).show(),
                        () -> showVerifyPinDialog(() -> Toasty.success(MainActivity.this, "Mode privé déverrouillé 🔒", Toasty.LENGTH_SHORT).show())
                );
            }
            return true;
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(MainActivity.this, ParametreActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        Intent intent = new Intent(MainActivity.this, AcceuilActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (handler != null && refreshRunnable != null) {
            handler.post(refreshRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}