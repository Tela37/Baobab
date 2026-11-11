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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;




/**
 * Repository 100% Cloud-Native pour la gestion des conversations.
 */
public class ConversationRepository {

    private static final Map<String, Integer> convPairCache = new ConcurrentHashMap<>();
    private static final Map<Integer, ConversationData> convCache = new ConcurrentHashMap<>();
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;

    public static class ConversationData {
        public int id;
        public int user1Id;
        public int user2Id;
        public boolean isPrivate;
        public boolean isPinned;
    }

    public ConversationRepository(Context context) {
        this.userRepository = new UserRepository(context);
        this.messageRepository = new MessageRepository(context);
    }

    public ConversationRepository(Object dbHelper, UserRepository userRepository, MessageRepository messageRepository) {
        this.userRepository = userRepository != null ? userRepository : new UserRepository();
        this.messageRepository = messageRepository != null ? messageRepository : new MessageRepository((Context) null);
    }

    public long createConversation(int user1Id, int user2Id) {
        int u1 = Math.min(user1Id, user2Id);
        int u2 = Math.max(user1Id, user2Id);
        String key = u1 + "_" + u2;

        if (convPairCache.containsKey(key)) {
            return convPairCache.get(key);
        }

        int convId = Math.abs(key.hashCode());
        ConversationData data = new ConversationData();
        data.id = convId;
        data.user1Id = u1;
        data.user2Id = u2;

        convPairCache.put(key, convId);
        convCache.put(convId, data);

        FirebaseHelper.getInstance().syncConversationToRealtimeDb(convId, u1, u2);
        return convId;
    }

    public int getConversationId(int user1Id, int user2Id) {
        int u1 = Math.min(user1Id, user2Id);
        int u2 = Math.max(user1Id, user2Id);
        String key = u1 + "_" + u2;
        return convPairCache.getOrDefault(key, -1);
    }

    public int createConversationIfNotExists(int user1Id, int user2Id) {
        int existing = getConversationId(user1Id, user2Id);
        if (existing != -1) return existing;
        return (int) createConversation(user1Id, user2Id);
    }

    public int getOtherUserId(int conversationId, int currentUserId) {
        ConversationData data = convCache.get(conversationId);
        if (data != null) {
            return data.user1Id == currentUserId ? data.user2Id : data.user1Id;
        }
        return -1;
    }

    public List<ConversationItem> getConversations(int currentUserId) {
        List<ConversationItem> result = new ArrayList<>();
        for (ConversationData data : convCache.values()) {
            if (data.user1Id == currentUserId || data.user2Id == currentUserId) {
                int otherUserId = (data.user1Id == currentUserId) ? data.user2Id : data.user1Id;
                result.add(
                        new ConversationItem(
                                data.id,
                                otherUserId,
                                userRepository.getUserName(otherUserId),
                                messageRepository.getLastMessage(data.id),
                                userRepository.getUserProfileImage(otherUserId),
                                messageRepository.getUnreadCount(data.id, currentUserId),
                                data.isPrivate,
                                data.isPinned
                        )
                );
            }
        }
        return result;
    }

    public int getTotalUnreadCount(int currentUserId) {
        int total = 0;
        for (ConversationData data : convCache.values()) {
            if (data.user1Id == currentUserId || data.user2Id == currentUserId) {
                total += messageRepository.getUnreadCount(data.id, currentUserId);
            }
        }
        return total;
    }

    public boolean setConversationPrivate(int conversationId, boolean isPrivate) {
        ConversationData data = convCache.get(conversationId);
        if (data != null) {
            data.isPrivate = isPrivate;
            return true;
        }
        return false;
    }

    public boolean setConversationPinned(int conversationId, boolean isPinned) {
        ConversationData data = convCache.get(conversationId);
        if (data != null) {
            data.isPinned = isPinned;
            return true;
        }
        return false;
    }

    public boolean isConversationPrivate(int conversationId) {
        ConversationData data = convCache.get(conversationId);
        return data != null && data.isPrivate;
    }

    public boolean deleteConversation(int conversationId) {
        convCache.remove(conversationId);
        messageRepository.clearConversationMessages(conversationId);
        return true;
    }
}

