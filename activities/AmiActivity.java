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

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;
import td.teladoum.baobab.repositories.ConversationRepository;
import td.teladoum.baobab.repositories.FriendRepository;
import td.teladoum.baobab.repositories.UserRepository;

/**
 * Activité de gestion des amis (AmiActivity).
 * Affiche la liste des amis actuels, les demandes d'amis en attente,
 * permet d'accepter ou décliner les demandes, et d'envoyer un message directement
 * à un contact (même sans être ami).
 */
public class AmiActivity extends AppCompatActivity {

    private Button btnOpenTrouverAmis;
    private EditText etSearchFriend;
    private TextView tvHeaderRequests;
    private RecyclerView rvPendingRequests;
    private TextView tvHeaderSuggestions;
    private RecyclerView rvSuggestions;
    private RecyclerView rvFriendsList;

    private FriendRepository friendRepository;
    private ConversationRepository conversationRepository;
    private UserRepository userRepository;
    private SessionManager sessionManager;

    private PendingRequestsAdapter requestsAdapter;
    private FriendsListAdapter friendsAdapter;
    private SuggestionsAdapter suggestionsAdapter;

    private final List<FriendRepository.FriendRequestItem> requestsList = new ArrayList<>();
    private final List<User> friendsList = new ArrayList<>();
    private final List<FriendRepository.UserSearchItem> suggestionsList = new ArrayList<>();

    private int currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ami);

        Toolbar toolbar = findViewById(R.id.toolbarAmi);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        friendRepository = new FriendRepository(this);
        conversationRepository = new ConversationRepository(this);
        userRepository = new UserRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        setupRecyclerViews();

        btnOpenTrouverAmis.setOnClickListener(v -> startActivity(new Intent(AmiActivity.this, TrouverAmisActivity.class)));

        etSearchFriend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadFriends(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadData();
    }

    private void initViews() {
        btnOpenTrouverAmis = findViewById(R.id.btnOpenTrouverAmis);
        etSearchFriend = findViewById(R.id.etSearchFriend);
        tvHeaderRequests = findViewById(R.id.tvHeaderRequests);
        rvPendingRequests = findViewById(R.id.rvPendingRequests);
        tvHeaderSuggestions = findViewById(R.id.tvHeaderSuggestions);
        rvSuggestions = findViewById(R.id.rvSuggestions);
        rvFriendsList = findViewById(R.id.rvFriendsList);
    }

    private void openChatWithUser(int receiverId, String receiverName, String receiverProfileImage) {
        int conversationId = conversationRepository.createConversationIfNotExists(currentUserId, receiverId);
        Intent intent = new Intent(AmiActivity.this, ChatActivity.class);
        intent.putExtra("conversation_id", conversationId);
        intent.putExtra("receiver_id", receiverId);
        intent.putExtra("receiver_name", receiverName);
        intent.putExtra("receiver_profile_image", receiverProfileImage);
        startActivity(intent);
    }

    private void setupRecyclerViews() {
        rvPendingRequests.setLayoutManager(new LinearLayoutManager(this));
        requestsAdapter = new PendingRequestsAdapter(requestsList, new OnRequestActionListener() {
            @Override
            public void onAccept(FriendRepository.FriendRequestItem item) {
                boolean accepted = friendRepository.acceptFriendRequest(item.requestId, item.senderId, currentUserId);
                if (accepted) {
                    Toasty.success(AmiActivity.this, "Demande acceptée !", Toasty.LENGTH_SHORT).show();
                    loadData();
                } else {
                    Toasty.error(AmiActivity.this, "Erreur", Toasty.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onDecline(FriendRepository.FriendRequestItem item) {
                boolean declined = friendRepository.declineFriendRequest(item.requestId);
                if (declined) {
                    Toasty.info(AmiActivity.this, "Demande refusée", Toasty.LENGTH_SHORT).show();
                    loadData();
                }
            }
        });
        rvPendingRequests.setAdapter(requestsAdapter);

        rvFriendsList.setLayoutManager(new LinearLayoutManager(this));
        friendsAdapter = new FriendsListAdapter(friendsList, new OnFriendActionListener() {
            @Override
            public void onRemoveFriend(User friend) {
                new AlertDialog.Builder(AmiActivity.this)
                        .setTitle("Retirer un ami")
                        .setMessage("Voulez-vous vraiment retirer " + friend.getName() + " de votre liste d'amis ?")
                        .setPositiveButton("Retirer", (dialog, which) -> {
                            boolean removed = friendRepository.removeFriend(currentUserId, friend.getId());
                            if (removed) {
                                Toasty.success(AmiActivity.this, "Ami retiré", Toasty.LENGTH_SHORT).show();
                                loadData();
                            } else {
                                Toasty.error(AmiActivity.this, "Erreur de suppression", Toasty.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Annuler", null)
                        .show();
            }
        });
        rvFriendsList.setAdapter(friendsAdapter);

        rvSuggestions.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        suggestionsAdapter = new SuggestionsAdapter(suggestionsList, new OnSuggestionActionListener() {
            @Override
            public void onAddClick(FriendRepository.UserSearchItem item) {
                boolean sent = friendRepository.sendFriendRequest(currentUserId, item.userId);
                if (sent) {
                    Toasty.success(AmiActivity.this, "Invitation envoyée !", Toasty.LENGTH_SHORT).show();
                    item.requestSent = true;
                    suggestionsAdapter.notifyDataSetChanged();
                } else {
                    Toasty.error(AmiActivity.this, "Erreur d'envoi", Toasty.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelClick(FriendRepository.UserSearchItem item) {
                boolean canceled = friendRepository.cancelFriendRequest(currentUserId, item.userId);
                if (canceled) {
                    Toasty.info(AmiActivity.this, "Demande annulée", Toasty.LENGTH_SHORT).show();
                    item.requestSent = false;
                    suggestionsAdapter.notifyDataSetChanged();
                } else {
                    Toasty.error(AmiActivity.this, "Erreur d'annulation", Toasty.LENGTH_SHORT).show();
                }
            }
        });
        rvSuggestions.setAdapter(suggestionsAdapter);
    }

    private void loadData() {
        if (userRepository != null) {
            userRepository.syncAllUsersFromCloud(() -> {
                loadPendingRequests();
                loadSuggestions();
                loadFriends(etSearchFriend.getText().toString());
            });
        } else {
            loadPendingRequests();
            loadSuggestions();
            loadFriends(etSearchFriend.getText().toString());
        }
    }

    private void loadSuggestions() {
        List<FriendRepository.UserSearchItem> suggestions = friendRepository.getRandomSuggestions(currentUserId, 10);
        suggestionsList.clear();
        suggestionsList.addAll(suggestions);
        suggestionsAdapter.notifyDataSetChanged();

        if (suggestions.isEmpty()) {
            tvHeaderSuggestions.setVisibility(View.GONE);
            rvSuggestions.setVisibility(View.GONE);
        } else {
            tvHeaderSuggestions.setVisibility(View.VISIBLE);
            rvSuggestions.setVisibility(View.VISIBLE);
        }
    }

    private void loadPendingRequests() {
        List<FriendRepository.FriendRequestItem> pending = friendRepository.getPendingRequests(currentUserId);
        requestsList.clear();
        requestsList.addAll(pending);
        requestsAdapter.notifyDataSetChanged();

        if (pending.isEmpty()) {
            tvHeaderRequests.setVisibility(View.GONE);
            rvPendingRequests.setVisibility(View.GONE);
        } else {
            tvHeaderRequests.setVisibility(View.VISIBLE);
            rvPendingRequests.setVisibility(View.VISIBLE);
        }
    }

    private void loadFriends(String query) {
        List<User> list = friendRepository.getFriendsList(currentUserId, query);
        friendsList.clear();
        friendsList.addAll(list);
        friendsAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private interface OnRequestActionListener {
        void onAccept(FriendRepository.FriendRequestItem item);
        void onDecline(FriendRepository.FriendRequestItem item);
    }

    private interface OnFriendActionListener {
        void onRemoveFriend(User friend);
    }

    private static class PendingRequestsAdapter extends RecyclerView.Adapter<PendingRequestsAdapter.ViewHolder> {
        private final List<FriendRepository.FriendRequestItem> list;
        private final OnRequestActionListener listener;

        public PendingRequestsAdapter(List<FriendRepository.FriendRequestItem> list, OnRequestActionListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendRepository.FriendRequestItem item = list.get(position);
            holder.tvFriendName.setText(item.senderName != null ? item.senderName : "Utilisateur");
            ImageUtils.loadProfileImage(holder.itemView.getContext(), item.senderProfileImage, holder.imgFriendAvatar);

            View.OnClickListener profileClickListener = v -> {
                Intent intent = new Intent(v.getContext(), PageUtilisateurActivity.class);
                intent.putExtra("user_id", item.senderId);
                intent.putExtra("user_name", item.senderName);
                intent.putExtra("user_profile_image", item.senderProfileImage);
                v.getContext().startActivity(intent);
            };

            holder.imgFriendAvatar.setOnClickListener(profileClickListener);
            holder.tvFriendName.setOnClickListener(profileClickListener);

            holder.btnAccept.setVisibility(View.VISIBLE);
            holder.btnDecline.setVisibility(View.VISIBLE);
            holder.btnRemoveFriend.setVisibility(View.GONE);

            holder.btnAccept.setOnClickListener(v -> {
                if (listener != null) listener.onAccept(item);
            });
            holder.btnDecline.setOnClickListener(v -> {
                if (listener != null) listener.onDecline(item);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgFriendAvatar;
            TextView tvFriendName;
            Button btnAccept, btnDecline, btnRemoveFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
                tvFriendName = itemView.findViewById(R.id.tvFriendName);
                btnAccept = itemView.findViewById(R.id.btnAcceptRequest);
                btnDecline = itemView.findViewById(R.id.btnDeclineRequest);
                btnRemoveFriend = itemView.findViewById(R.id.btnRemoveFriend);
            }
        }
    }

    private static class FriendsListAdapter extends RecyclerView.Adapter<FriendsListAdapter.ViewHolder> {
        private final List<User> list;
        private final OnFriendActionListener listener;

        public FriendsListAdapter(List<User> list, OnFriendActionListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = list.get(position);
            holder.tvFriendName.setText(user.getName() != null ? user.getName() : "Ami");
            ImageUtils.loadProfileImage(holder.itemView.getContext(), user.getProfileImage(), holder.imgFriendAvatar);

            View.OnClickListener profileClickListener = v -> {
                Intent intent = new Intent(v.getContext(), PageUtilisateurActivity.class);
                intent.putExtra("user_id", user.getId());
                intent.putExtra("user_name", user.getName());
                intent.putExtra("user_profile_image", user.getProfileImage());
                v.getContext().startActivity(intent);
            };

            holder.imgFriendAvatar.setOnClickListener(profileClickListener);
            holder.tvFriendName.setOnClickListener(profileClickListener);

            holder.btnAccept.setVisibility(View.GONE);
            holder.btnDecline.setVisibility(View.GONE);
            holder.btnRemoveFriend.setVisibility(View.VISIBLE);

            holder.btnRemoveFriend.setOnClickListener(v -> {
                if (listener != null) listener.onRemoveFriend(user);
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgFriendAvatar;
            TextView tvFriendName;
            Button btnAccept, btnDecline, btnRemoveFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
                tvFriendName = itemView.findViewById(R.id.tvFriendName);
                btnAccept = itemView.findViewById(R.id.btnAcceptRequest);
                btnDecline = itemView.findViewById(R.id.btnDeclineRequest);
                btnRemoveFriend = itemView.findViewById(R.id.btnRemoveFriend);
            }
        }
    }

    private interface OnSuggestionActionListener {
        void onAddClick(FriendRepository.UserSearchItem item);
        void onCancelClick(FriendRepository.UserSearchItem item);
    }

    private static class SuggestionsAdapter extends RecyclerView.Adapter<SuggestionsAdapter.ViewHolder> {
        private final List<FriendRepository.UserSearchItem> list;
        private final OnSuggestionActionListener listener;

        public SuggestionsAdapter(List<FriendRepository.UserSearchItem> list, OnSuggestionActionListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_suggestion_card, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendRepository.UserSearchItem item = list.get(position);
            holder.tvUserSearchName.setText(item.name != null ? item.name : "Utilisateur");

            String avatarUrl = item.profileImage;
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                td.teladoum.baobab.repositories.UserRepository uRepo = new td.teladoum.baobab.repositories.UserRepository(holder.itemView.getContext());
                User u = uRepo.getUserById(item.userId);
                if (u != null && u.getProfileImage() != null && !u.getProfileImage().trim().isEmpty()) {
                    avatarUrl = u.getProfileImage();
                }
            }
            ImageUtils.loadProfileImage(holder.itemView.getContext(), avatarUrl, holder.imgUserSearchAvatar);

            View.OnClickListener profileClickListener = v -> {
                Intent intent = new Intent(v.getContext(), PageUtilisateurActivity.class);
                intent.putExtra("user_id", item.userId);
                intent.putExtra("user_name", item.name);
                intent.putExtra("user_profile_image", item.profileImage);
                v.getContext().startActivity(intent);
            };

            holder.imgUserSearchAvatar.setOnClickListener(profileClickListener);
            holder.tvUserSearchName.setOnClickListener(profileClickListener);

            if (item.requestSent) {
                holder.btnAddFriend.setText("Annuler");
                holder.btnAddFriend.setEnabled(true);
                holder.btnAddFriend.setTextColor(android.graphics.Color.RED);
                holder.btnAddFriend.setOnClickListener(v -> {
                    if (listener != null) listener.onCancelClick(item);
                });
            } else {
                holder.btnAddFriend.setText("Ajouter");
                holder.btnAddFriend.setEnabled(true);
                holder.btnAddFriend.setTextColor(android.graphics.Color.WHITE);
                holder.btnAddFriend.setOnClickListener(v -> {
                    if (listener != null) listener.onAddClick(item);
                });
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgUserSearchAvatar;
            TextView tvUserSearchName;
            Button btnAddFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgUserSearchAvatar = itemView.findViewById(R.id.imgUserSearchAvatar);
                tvUserSearchName = itemView.findViewById(R.id.tvUserSearchName);
                btnAddFriend = itemView.findViewById(R.id.btnAddFriend);
            }
        }
    }
}

