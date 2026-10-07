package td.teladoumbaobabtd;

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
import td.teladoumbaobabtd.repository.ConversationRepository;
import td.teladoumbaobabtd.repository.FriendRepository;

public class AmiActivity extends AppCompatActivity {

    private Button btnOpenTrouverAmis;
    private EditText etSearchFriend;
    private TextView tvHeaderRequests;
    private RecyclerView rvPendingRequests;
    private RecyclerView rvFriendsList;

    private FriendRepository friendRepository;
    private ConversationRepository conversationRepository;
    private SessionManager sessionManager;

    private PendingRequestsAdapter requestsAdapter;
    private FriendsListAdapter friendsAdapter;

    private final List<FriendRepository.FriendRequestItem> requestsList = new ArrayList<>();
    private final List<User> friendsList = new ArrayList<>();

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
        rvFriendsList = findViewById(R.id.rvFriendsList);
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
            public void onSendMessage(User friend) {
                int convId = conversationRepository.createConversationIfNotExists(currentUserId, friend.getId());
                Intent intent = new Intent(AmiActivity.this, ChatActivity.class);
                intent.putExtra("conversation_id", convId);
                intent.putExtra("receiver_name", friend.getName());
                intent.putExtra("receiver_profile_image", friend.getProfileImage());
                startActivity(intent);
            }

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
    }

    private void loadData() {
        loadPendingRequests();
        loadFriends(etSearchFriend.getText().toString());
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
        void onSendMessage(User friend);
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

            holder.btnAccept.setVisibility(View.VISIBLE);
            holder.btnDecline.setVisibility(View.VISIBLE);
            holder.btnSendMessage.setVisibility(View.GONE);
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
            Button btnAccept, btnDecline, btnSendMessage, btnRemoveFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
                tvFriendName = itemView.findViewById(R.id.tvFriendName);
                btnAccept = itemView.findViewById(R.id.btnAcceptRequest);
                btnDecline = itemView.findViewById(R.id.btnDeclineRequest);
                btnSendMessage = itemView.findViewById(R.id.btnSendMessage);
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

            holder.btnAccept.setVisibility(View.GONE);
            holder.btnDecline.setVisibility(View.GONE);
            holder.btnSendMessage.setVisibility(View.VISIBLE);
            holder.btnRemoveFriend.setVisibility(View.VISIBLE);

            holder.btnSendMessage.setOnClickListener(v -> {
                if (listener != null) listener.onSendMessage(user);
            });

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
            Button btnAccept, btnDecline, btnSendMessage, btnRemoveFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
                tvFriendName = itemView.findViewById(R.id.tvFriendName);
                btnAccept = itemView.findViewById(R.id.btnAcceptRequest);
                btnDecline = itemView.findViewById(R.id.btnDeclineRequest);
                btnSendMessage = itemView.findViewById(R.id.btnSendMessage);
                btnRemoveFriend = itemView.findViewById(R.id.btnRemoveFriend);
            }
        }
    }
}
