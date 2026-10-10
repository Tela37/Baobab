package td.teladoumbaobabtd;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import td.teladoumbaobabtd.repository.FriendRepository;
import td.teladoumbaobabtd.repository.GroupRepository;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Activité affichant la liste des groupes de l'utilisateur.
 * Vérifie le nombre d'amis pour masquer la création si < 2 amis.
 */
public class GroupListActivity extends AppCompatActivity {

    private RecyclerView rvGroupList;
    private View layoutEmptyGroups;
    private LinearLayout layoutMinFriendsInfo;
    private FloatingActionButton fabCreateGroup;

    private GroupRepository groupRepository;
    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private int currentUserId;

    private final List<Group> groupList = new ArrayList<>();
    private GroupAdapter groupAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_list);

        Toolbar toolbar = findViewById(R.id.toolbarGroupList);
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

        fabCreateGroup.setOnClickListener(v -> {
            List<User> friends = friendRepository.getFriendsList(currentUserId, "");
            if (friends.size() < 2) {
                Toasty.warning(this, "Vous devez posséder au moins 2 amis pour créer un groupe.", Toasty.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(GroupListActivity.this, CreateGroupActivity.class));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkFriendsCountAndOption();
        loadGroups();
    }

    private void initViews() {
        rvGroupList = findViewById(R.id.rvGroupList);
        layoutEmptyGroups = findViewById(R.id.layoutEmptyGroups);
        layoutMinFriendsInfo = findViewById(R.id.layoutMinFriendsInfo);
        fabCreateGroup = findViewById(R.id.fabCreateGroup);
    }

    private void setupRecyclerView() {
        rvGroupList.setLayoutManager(new LinearLayoutManager(this));
        groupAdapter = new GroupAdapter(groupList, group -> {
            Intent intent = new Intent(GroupListActivity.this, GroupChatActivity.class);
            intent.putExtra("group_id", group.getId());
            startActivity(intent);
        });
        rvGroupList.setAdapter(groupAdapter);
    }

    private void checkFriendsCountAndOption() {
        List<User> friends = friendRepository.getFriendsList(currentUserId, "");
        if (friends.size() < 2) {
            fabCreateGroup.setVisibility(View.GONE);
            layoutMinFriendsInfo.setVisibility(View.VISIBLE);
        } else {
            fabCreateGroup.setVisibility(View.VISIBLE);
            layoutMinFriendsInfo.setVisibility(View.GONE);
        }
    }

    private void loadGroups() {
        List<Group> list = groupRepository.getUserGroups(currentUserId);
        groupList.clear();
        groupList.addAll(list);
        groupAdapter.notifyDataSetChanged();

        if (groupList.isEmpty()) {
            layoutEmptyGroups.setVisibility(View.VISIBLE);
        } else {
            layoutEmptyGroups.setVisibility(View.GONE);
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

    // Adaptateur interne pour la liste des groupes
    private static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

        private final List<Group> list;
        private final OnGroupClickListener listener;

        interface OnGroupClickListener {
            void onGroupClick(Group group);
        }

        public GroupAdapter(List<Group> list, OnGroupClickListener listener) {
            this.list = list;
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Group group = list.get(position);
            holder.tvGroupName.setText(group.getName());
            holder.tvGroupLastMessage.setText(group.getLastMessage());
            holder.tvGroupMemberCount.setText(group.getMemberCount() + " membres");

            if (group.isLocked()) {
                holder.tvGroupLockStatus.setVisibility(View.VISIBLE);
            } else {
                holder.tvGroupLockStatus.setVisibility(View.GONE);
            }

            ImageUtils.loadProfileImage(holder.itemView.getContext(), group.getIcon(), holder.imgGroupAvatar);

            holder.itemView.setOnClickListener(v -> listener.onGroupClick(group));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgGroupAvatar;
            TextView tvGroupName;
            TextView tvGroupLockStatus;
            TextView tvGroupLastMessage;
            TextView tvGroupMemberCount;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgGroupAvatar = itemView.findViewById(R.id.imgGroupAvatar);
                tvGroupName = itemView.findViewById(R.id.tvGroupName);
                tvGroupLockStatus = itemView.findViewById(R.id.tvGroupLockStatus);
                tvGroupLastMessage = itemView.findViewById(R.id.tvGroupLastMessage);
                tvGroupMemberCount = itemView.findViewById(R.id.tvGroupMemberCount);
            }
        }
    }
}
