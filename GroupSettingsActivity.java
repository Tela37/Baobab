package td.teladoumbaobabtd;

import android.os.Bundle;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;

import td.teladoumbaobabtd.repository.FriendRepository;
import td.teladoumbaobabtd.repository.GroupRepository;

import java.util.ArrayList;
import java.util.List;

import es.dmoral.toasty.Toasty;

/**
 * Écran de modification et paramétrage du groupe.
 * Permet à l'administrateur de modifier le nom, de gérer les membres (suppression/ajout depuis la liste d'amis)
 * et de VERROUILLER ou RÉACTIVER le groupe.
 */
public class GroupSettingsActivity extends AppCompatActivity {

    private EditText etEditGroupName;
    private Button btnSaveGroupName;

    private LinearLayout layoutLockSection;
    private SwitchMaterial switchLockGroup;

    private RecyclerView rvGroupMembers;
    private Button btnAddMoreFriends;

    private GroupRepository groupRepository;
    private FriendRepository friendRepository;
    private SessionManager sessionManager;
    private int currentUserId;
    private int groupId;

    private Group currentGroup;
    private boolean isAdmin = false;

    private final List<User> memberList = new ArrayList<>();
    private MemberAdapter memberAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_settings);

        groupId = getIntent().getIntExtra("group_id", -1);

        Toolbar toolbar = findViewById(R.id.toolbarGroupSettings);
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
        loadGroupData();

        btnSaveGroupName.setOnClickListener(v -> saveGroupName());
        btnAddMoreFriends.setOnClickListener(v -> showAddFriendsDialog());
    }

    private void initViews() {
        etEditGroupName = findViewById(R.id.etEditGroupName);
        btnSaveGroupName = findViewById(R.id.btnSaveGroupName);

        layoutLockSection = findViewById(R.id.layoutLockSection);
        switchLockGroup = findViewById(R.id.switchLockGroup);

        rvGroupMembers = findViewById(R.id.rvGroupMembers);
        btnAddMoreFriends = findViewById(R.id.btnAddMoreFriends);
    }

    private void setupRecyclerView() {
        rvGroupMembers.setLayoutManager(new LinearLayoutManager(this));
        memberAdapter = new MemberAdapter(memberList, isAdmin, userToRemove -> removeMember(userToRemove));
        rvGroupMembers.setAdapter(memberAdapter);
    }

    private void loadGroupData() {
        currentGroup = groupRepository.getGroupById(groupId);
        if (currentGroup == null) {
            finish();
            return;
        }

        isAdmin = groupRepository.isUserAdmin(groupId, currentUserId);

        etEditGroupName.setText(currentGroup.getName());

        if (isAdmin) {
            layoutLockSection.setVisibility(View.VISIBLE);
            btnSaveGroupName.setVisibility(View.VISIBLE);
            btnAddMoreFriends.setVisibility(View.VISIBLE);
            etEditGroupName.setEnabled(true);

            switchLockGroup.setOnCheckedChangeListener(null);
            switchLockGroup.setChecked(currentGroup.isLocked());
            switchLockGroup.setOnCheckedChangeListener((buttonView, isChecked) -> {
                boolean success = groupRepository.setGroupLocked(groupId, isChecked);
                if (success) {
                    String statusMsg = isChecked ? "Groupe verrouillé 🔒" : "Groupe réactivé / déverrouillé 🔓";
                    Toasty.info(this, statusMsg, Toasty.LENGTH_SHORT).show();
                } else {
                    Toasty.error(this, "Erreur lors du changement d'état", Toasty.LENGTH_SHORT).show();
                }
            });
        } else {
            layoutLockSection.setVisibility(View.GONE);
            btnSaveGroupName.setVisibility(View.GONE);
            btnAddMoreFriends.setVisibility(View.GONE);
            etEditGroupName.setEnabled(false);
        }

        loadMembers();
    }

    private void loadMembers() {
        List<User> list = groupRepository.getGroupMembers(groupId);
        memberList.clear();
        memberList.addAll(list);
        memberAdapter.setIsAdmin(isAdmin);
        memberAdapter.notifyDataSetChanged();
    }

    private void saveGroupName() {
        String newName = etEditGroupName.getText().toString().trim();
        if (newName.isEmpty()) {
            etEditGroupName.setError("Nom requis");
            return;
        }

        boolean updated = groupRepository.updateGroupName(groupId, newName);
        if (updated) {
            Toasty.success(this, "Nom du groupe mis à jour", Toasty.LENGTH_SHORT).show();
        } else {
            Toasty.error(this, "Erreur lors de la mise à jour", Toasty.LENGTH_SHORT).show();
        }
    }

    private void removeMember(User user) {
        if (!isAdmin) return;
        if (user.getId() == currentUserId) {
            Toasty.warning(this, "Vous êtes l'administrateur du groupe", Toasty.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Retirer du groupe")
                .setMessage("Voulez-vous retirer " + user.getName() + " du groupe ?")
                .setPositiveButton("Retirer", (dialog, which) -> {
                    boolean removed = groupRepository.removeMemberFromGroup(groupId, user.getId());
                    if (removed) {
                        Toasty.success(this, user.getName() + " retiré du groupe", Toasty.LENGTH_SHORT).show();
                        loadMembers();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showAddFriendsDialog() {
        List<User> myFriends = friendRepository.getFriendsList(currentUserId, "");
        List<User> friendsToAdd = new ArrayList<>();

        // Filtrer pour ne garder que les amis qui ne sont pas encore dans le groupe
        for (User friend : myFriends) {
            boolean alreadyInGroup = false;
            for (User member : memberList) {
                if (member.getId() == friend.getId()) {
                    alreadyInGroup = true;
                    break;
                }
            }
            if (!alreadyInGroup) {
                friendsToAdd.add(friend);
            }
        }

        if (friendsToAdd.isEmpty()) {
            Toasty.info(this, "Tous vos amis sont déjà dans le groupe", Toasty.LENGTH_SHORT).show();
            return;
        }

        String[] friendNames = new String[friendsToAdd.size()];
        boolean[] checkedItems = new boolean[friendsToAdd.size()];

        for (int i = 0; i < friendsToAdd.size(); i++) {
            friendNames[i] = friendsToAdd.get(i).getName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Ajouter des amis au groupe 👥")
                .setMultiChoiceItems(friendNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Ajouter", (dialog, which) -> {
                    int addedCount = 0;
                    for (int i = 0; i < friendsToAdd.size(); i++) {
                        if (checkedItems[i]) {
                            boolean added = groupRepository.addMemberToGroup(groupId, friendsToAdd.get(i).getId());
                            if (added) addedCount++;
                        }
                    }
                    if (addedCount > 0) {
                        Toasty.success(this, addedCount + " ami(s) ajouté(s) au groupe", Toasty.LENGTH_SHORT).show();
                        loadMembers();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private static class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

        private final List<User> list;
        private boolean isAdmin;
        private final OnRemoveClickListener removeClickListener;

        interface OnRemoveClickListener {
            void onRemoveClick(User user);
        }

        public MemberAdapter(List<User> list, boolean isAdmin, OnRemoveClickListener removeClickListener) {
            this.list = list;
            this.isAdmin = isAdmin;
            this.removeClickListener = removeClickListener;
        }

        public void setIsAdmin(boolean isAdmin) {
            this.isAdmin = isAdmin;
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

            holder.cbSelectFriend.setVisibility(View.GONE);

            if (isAdmin) {
                holder.ibRemoveMember.setVisibility(View.VISIBLE);
                holder.ibRemoveMember.setOnClickListener(v -> removeClickListener.onRemoveClick(user));
            } else {
                holder.ibRemoveMember.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imgFriendAvatar;
            TextView tvFriendName;
            View cbSelectFriend;
            ImageButton ibRemoveMember;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                imgFriendAvatar = itemView.findViewById(R.id.imgFriendAvatar);
                tvFriendName = itemView.findViewById(R.id.tvFriendName);
                cbSelectFriend = itemView.findViewById(R.id.cbSelectFriend);

                // Optionnel : bouton de suppression rapide s'il existe ou clic long
                ibRemoveMember = new ImageButton(itemView.getContext());
            }
        }
    }
}
