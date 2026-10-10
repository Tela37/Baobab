package td.teladoumbaobabtd;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import td.teladoumbaobabtd.repository.FriendRepository;
import td.teladoumbaobabtd.repository.GroupRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import es.dmoral.toasty.Toasty;

/**
 * Écran de création de groupe.
 * Limite la sélection de membres exclusivement aux utilisateurs de la liste d'amis.
 */
public class CreateGroupActivity extends AppCompatActivity {

    private EditText etGroupName;
    private RecyclerView rvFriendsToSelect;
    private Button btnConfirmCreateGroup;

    private GroupRepository groupRepository;
    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private int currentUserId;

    private final List<User> friendsList = new ArrayList<>();
    private final Set<Integer> selectedFriendIds = new HashSet<>();
    private FriendSelectAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        Toolbar toolbar = findViewById(R.id.toolbarCreateGroup);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        groupRepository = new GroupRepository(this);
        friendRepository = new FriendRepository(this);
        sessionManager = new SessionManager(this);
        currentUserId = sessionManager.getUserId();

        initViews();
        setupRecyclerView();
        loadFriendsOnly();

        btnConfirmCreateGroup.setOnClickListener(v -> createGroup());
    }

    private void initViews() {
        etGroupName = findViewById(R.id.etGroupName);
        rvFriendsToSelect = findViewById(R.id.rvFriendsToSelect);
        btnConfirmCreateGroup = findViewById(R.id.btnConfirmCreateGroup);
    }

    private void setupRecyclerView() {
        rvFriendsToSelect.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FriendSelectAdapter(friendsList, selectedFriendIds);
        rvFriendsToSelect.setAdapter(adapter);
    }

    private void loadFriendsOnly() {
        List<User> friends = friendRepository.getFriendsList(currentUserId, "");
        friendsList.clear();
        friendsList.addAll(friends);
        adapter.notifyDataSetChanged();

        if (friendsList.size() < 2) {
            Toasty.warning(this, "Vous devez posséder au moins 2 amis pour créer un groupe.", Toasty.LENGTH_LONG).show();
            finish();
        }
    }

    private void createGroup() {
        String groupName = etGroupName.getText().toString().trim();
        if (groupName.isEmpty()) {
            etGroupName.setError("Le nom du groupe est requis");
            etGroupName.requestFocus();
            return;
        }

        if (selectedFriendIds.size() < 1) {
            Toasty.warning(this, "Veuillez sélectionner au moins 1 ami à ajouter au groupe", Toasty.LENGTH_SHORT).show();
            return;
        }

        List<Integer> memberIds = new ArrayList<>(selectedFriendIds);
        long groupId = groupRepository.createGroup(groupName, null, currentUserId, memberIds);

        if (groupId != -1) {
            Toasty.success(this, "Groupe créé avec succès ! 🚀", Toasty.LENGTH_SHORT).show();
            finish();
        } else {
            Toasty.error(this, "Erreur lors de la création du groupe", Toasty.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class FriendSelectAdapter extends RecyclerView.Adapter<FriendSelectAdapter.ViewHolder> {

        private final List<User> list;
        private final Set<Integer> selectedIds;

        public FriendSelectAdapter(List<User> list, Set<Integer> selectedIds) {
            this.list = list;
            this.selectedIds = selectedIds;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_select, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            User user = list.get(position);
            holder.tvFriendName.setText(user.getName());
            ImageUtils.loadProfileImage(holder.itemView.getContext(), user.getProfileImage(), holder.imgFriendAvatar);

            holder.cbSelectFriend.setOnCheckedChangeListener(null);
            holder.cbSelectFriend.setChecked(selectedIds.contains(user.getId()));

            holder.cbSelectFriend.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedIds.add(user.getId());
                } else {
                    selectedIds.remove(user.getId());
                }
            });

            holder.itemView.setOnClickListener(v -> holder.cbSelectFriend.setChecked(!holder.cbSelectFriend.isChecked()));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgFriendAvatar;
            TextView tvFriendName;
            CheckBox cbSelectFriend;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
                tvFriendName = itemView.findViewById(R.id.tvFriendName);
                cbSelectFriend = itemView.findViewById(R.id.cbSelectFriend);
            }
        }
    }
}
