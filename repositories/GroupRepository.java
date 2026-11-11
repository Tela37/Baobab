package td.teladoum.baobab.repositories;

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

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;







/**
 * Repository Cloud-Native pour la gestion des groupes de discussion exclusivement via Realtime Database.
 */
public class GroupRepository {

    private static final Map<Integer, Group> groupsCache = new ConcurrentHashMap<>();
    private static final Map<Integer, List<GroupMessage>> groupMessagesCache = new ConcurrentHashMap<>();
    private static final Map<Integer, List<Integer>> groupMembersCache = new ConcurrentHashMap<>();

    public GroupRepository(Context context) {
    }

    public GroupRepository(Object unused) {
    }

    public long createGroup(String name, String icon, int creatorId, List<Integer> selectedFriendIds) {
        int groupId = Math.abs((name + "_" + creatorId + "_" + System.currentTimeMillis()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        Group group = new Group();
        group.setId(groupId);
        group.setName(name);
        group.setIcon(icon);
        group.setCreatorId(creatorId);
        group.setLocked(false);
        group.setVoiceEnabled(true);
        group.setCreatedAt(createdAt);

        List<Integer> members = Collections.synchronizedList(new ArrayList<>());
        members.add(creatorId);
        if (selectedFriendIds != null) {
            for (int fId : selectedFriendIds) {
                if (fId != creatorId && !members.contains(fId)) {
                    members.add(fId);
                }
            }
        }
        group.setMemberCount(members.size());

        groupsCache.put(groupId, group);
        groupMembersCache.put(groupId, members);

        FirebaseHelper.getInstance().syncGroupToRealtimeDb(group);

        return groupId;
    }

    public List<Group> getUserGroups(int userId) {
        List<Group> list = new ArrayList<>();
        for (Map.Entry<Integer, List<Integer>> entry : groupMembersCache.entrySet()) {
            if (entry.getValue().contains(userId)) {
                Group g = groupsCache.get(entry.getKey());
                if (g != null) list.add(g);
            }
        }
        return list;
    }

    public Group getGroupById(int groupId) {
        return groupsCache.get(groupId);
    }

    public boolean setGroupVoiceEnabled(int groupId, boolean isVoiceEnabled) {
        Group g = groupsCache.get(groupId);
        if (g != null) {
            g.setVoiceEnabled(isVoiceEnabled);
            FirebaseHelper.getInstance().syncGroupToRealtimeDb(g);
            return true;
        }
        return false;
    }

    public boolean isUserAdmin(int groupId, int userId) {
        Group g = groupsCache.get(groupId);
        return g != null && g.getCreatorId() == userId;
    }

    public boolean updateGroupName(int groupId, String newName) {
        Group g = groupsCache.get(groupId);
        if (g != null) {
            g.setName(newName);
            FirebaseHelper.getInstance().syncGroupToRealtimeDb(g);
            return true;
        }
        return false;
    }

    public boolean setGroupLocked(int groupId, boolean isLocked) {
        Group g = groupsCache.get(groupId);
        if (g != null) {
            g.setLocked(isLocked);
            FirebaseHelper.getInstance().syncGroupToRealtimeDb(g);
            return true;
        }
        return false;
    }

    public boolean addMemberToGroup(int groupId, int userId) {
        List<Integer> members = groupMembersCache.computeIfAbsent(groupId, k -> Collections.synchronizedList(new ArrayList<>()));
        if (!members.contains(userId)) {
            members.add(userId);
            Group g = groupsCache.get(groupId);
            if (g != null) g.setMemberCount(members.size());
            return true;
        }
        return false;
    }

    public boolean removeMemberFromGroup(int groupId, int userId) {
        List<Integer> members = groupMembersCache.get(groupId);
        if (members != null) {
            boolean removed = members.remove(Integer.valueOf(userId));
            Group g = groupsCache.get(groupId);
            if (g != null) g.setMemberCount(members.size());
            return removed;
        }
        return false;
    }

    public List<User> getGroupMembers(int groupId) {
        return new ArrayList<>();
    }

    public boolean sendGroupMessage(int groupId, int senderId, String message, String imagePath) {
        boolean hasText = message != null && !message.trim().isEmpty();
        boolean hasImage = imagePath != null && !imagePath.trim().isEmpty();
        if (!hasText && !hasImage) return false;

        int msgId = Math.abs((groupId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        GroupMessage gMsg = new GroupMessage();
        gMsg.setId(msgId);
        gMsg.setGroupId(groupId);
        gMsg.setSenderId(senderId);
        gMsg.setSenderName("Utilisateur");
        gMsg.setMessage(hasText ? message.trim() : "");
        gMsg.setImagePath(imagePath);
        gMsg.setCreatedAt(createdAt);

        List<GroupMessage> list = groupMessagesCache.computeIfAbsent(groupId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(gMsg);

        FirebaseHelper.getInstance().syncGroupMessageToRealtimeDb(groupId, gMsg);

        return true;
    }

    public boolean sendGroupVideoMessage(int groupId, int senderId, String videoPath) {
        if (videoPath == null || videoPath.trim().isEmpty()) return false;
        int msgId = Math.abs((groupId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        GroupMessage gMsg = new GroupMessage();
        gMsg.setId(msgId);
        gMsg.setGroupId(groupId);
        gMsg.setSenderId(senderId);
        gMsg.setVideoPath(videoPath);
        gMsg.setCreatedAt(createdAt);

        List<GroupMessage> list = groupMessagesCache.computeIfAbsent(groupId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(gMsg);

        FirebaseHelper.getInstance().syncGroupMessageToRealtimeDb(groupId, gMsg);
        return true;
    }

    public boolean sendGroupAudioMessage(int groupId, int senderId, String audioPath) {
        if (audioPath == null || audioPath.trim().isEmpty()) return false;
        int msgId = Math.abs((groupId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        GroupMessage gMsg = new GroupMessage();
        gMsg.setId(msgId);
        gMsg.setGroupId(groupId);
        gMsg.setSenderId(senderId);
        gMsg.setAudioPath(audioPath);
        gMsg.setCreatedAt(createdAt);

        List<GroupMessage> list = groupMessagesCache.computeIfAbsent(groupId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(gMsg);

        FirebaseHelper.getInstance().syncGroupMessageToRealtimeDb(groupId, gMsg);
        return true;
    }

    public boolean sendGroupFileMessage(int groupId, int senderId, String filePath, String fileName) {
        if (filePath == null || filePath.trim().isEmpty()) return false;
        int msgId = Math.abs((groupId + "_" + senderId + "_" + System.currentTimeMillis() + "_" + System.nanoTime()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        GroupMessage gMsg = new GroupMessage();
        gMsg.setId(msgId);
        gMsg.setGroupId(groupId);
        gMsg.setSenderId(senderId);
        gMsg.setFilePath(filePath);
        gMsg.setFileName(fileName != null ? fileName : "Document.pdf");
        gMsg.setCreatedAt(createdAt);

        List<GroupMessage> list = groupMessagesCache.computeIfAbsent(groupId, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(gMsg);

        FirebaseHelper.getInstance().syncGroupMessageToRealtimeDb(groupId, gMsg);
        return true;
    }

    public boolean clearGroupMessages(int groupId) {
        groupMessagesCache.remove(groupId);
        return true;
    }

    public boolean deleteGroupMessage(int groupId, int messageId, int userId) {
        List<GroupMessage> list = groupMessagesCache.get(groupId);
        if (list == null) return false;
        synchronized (list) {
            for (GroupMessage gMsg : list) {
                if (gMsg.getId() == messageId) {
                    if (gMsg.getSenderId() != userId && !isUserAdmin(groupId, userId)) {
                        return false;
                    }
                    if (!TimeUtils.isWithinHours(gMsg.getCreatedAt(), 10)) {
                        return false;
                    }
                    gMsg.setMessage("Ce message a été supprimé 🚫");
                    gMsg.setImagePath(null);
                    gMsg.setVideoPath(null);
                    gMsg.setAudioPath(null);
                    gMsg.setFilePath(null);
                    FirebaseHelper.getInstance().syncGroupMessageToRealtimeDb(groupId, gMsg);
                    return true;
                }
            }
        }
        return false;
    }

    public boolean toggleLikeGroupMessage(int groupMessageId, int userId) {
        return true;
    }

    public List<GroupMessage> getGroupMessages(int groupId, int currentUserId) {
        List<GroupMessage> list = groupMessagesCache.get(groupId);
        if (list == null) return new ArrayList<>();
        synchronized (list) {
            return new ArrayList<>(list);
        }
    }
}

