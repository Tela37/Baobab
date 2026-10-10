package td.teladoumbaobabtd;

import android.graphics.Color;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import td.teladoumbaobabtd.repository.FriendRepository;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Activité de recherche d'amis.
 * Permet de rechercher des utilisateurs par nom ou email et de leur envoyer une demande d'ami.
 */
public class TrouverAmisActivity extends AppCompatActivity {

    private EditText etSearchUsersToBefriend;
    private RecyclerView rvUsersToBefriend;

    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private int currentUserId;

    private UserSearchAdapter adapter;
    private final List<FriendRepository.UserSearchItem> userList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trouver_amis);

        Toolbar toolbar = findViewById(R.id.toolbarTrouverAmis);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        friendRepository = new FriendRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        etSearchUsersToBefriend = findViewById(R.id.etSearchUsersToBefriend);
        rvUsersToBefriend = findViewById(R.id.rvUsersToBefriend);

        rvUsersToBefriend.setLayoutManager(new LinearLayoutManager(this));
        adapter = new UserSearchAdapter(userList, new OnUserSearchActionListener() {
            @Override
            public void onAddClick(FriendRepository.UserSearchItem item) {
                boolean sent = friendRepository.sendFriendRequest(currentUserId, item.userId);
                if (sent) {
                    Toasty.success(TrouverAmisActivity.this, "Invitation envoyée !", Toasty.LENGTH_SHORT).show();
                    item.requestSent = true;
                    adapter.notifyDataSetChanged();
                } else {
                    Toasty.error(TrouverAmisActivity.this, "Erreur d'envoi", Toasty.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelClick(FriendRepository.UserSearchItem item) {
                boolean canceled = friendRepository.cancelFriendRequest(currentUserId, item.userId);
                if (canceled) {
                    Toasty.info(TrouverAmisActivity.this, "Demande annulée", Toasty.LENGTH_SHORT).show();
                    item.requestSent = false;
                    adapter.notifyDataSetChanged();
                } else {
                    Toasty.error(TrouverAmisActivity.this, "Erreur d'annulation", Toasty.LENGTH_SHORT).show();
                }
            }
        });
        rvUsersToBefriend.setAdapter(adapter);

        etSearchUsersToBefriend.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchUsers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        searchUsers("");
    }

    private void searchUsers(String query) {
        List<FriendRepository.UserSearchItem> results = friendRepository.searchUsersToBefriend(currentUserId, query);
        userList.clear();
        userList.addAll(results);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private interface OnUserSearchActionListener {
        void onAddClick(FriendRepository.UserSearchItem item);
        void onCancelClick(FriendRepository.UserSearchItem item);
    }

    private static class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.ViewHolder> {
        private final List<FriendRepository.UserSearchItem> list;
        private final OnUserSearchActionListener listener;

        public UserSearchAdapter(List<FriendRepository.UserSearchItem> list, OnUserSearchActionListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendRepository.UserSearchItem item = list.get(position);

            holder.tvUserSearchName.setText(item.name != null ? item.name : "Utilisateur");
            ImageUtils.loadProfileImage(holder.itemView.getContext(), item.profileImage, holder.imgUserSearchAvatar);

            if (item.isFriend) {
                holder.btnAddFriend.setText("Déjà amis");
                holder.btnAddFriend.setEnabled(false);
                holder.btnAddFriend.setTextColor(Color.GRAY);
                holder.btnAddFriend.setOnClickListener(null);
            } else if (item.requestSent) {
                holder.btnAddFriend.setText("Annuler");
                holder.btnAddFriend.setEnabled(true);
                holder.btnAddFriend.setTextColor(Color.RED);
                holder.btnAddFriend.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onCancelClick(item);
                    }
                });
            } else {
                holder.btnAddFriend.setText("Ajouter");
                holder.btnAddFriend.setEnabled(true);
                holder.btnAddFriend.setTextColor(Color.WHITE);
                holder.btnAddFriend.setOnClickListener(v -> {
                    if (listener != null) {
                        listener.onAddClick(item);
                    }
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
