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
 * Repository Cloud-Native pour la gestion des stories éphémères exclusivement via Realtime Database.
 */
public class StoryRepository {

    private static final Map<Integer, Story> storiesCache = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> storyViewsCount = new ConcurrentHashMap<>();
    private static com.google.firebase.database.ValueEventListener liveStoriesListener;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public StoryRepository(Context context) {
        this.notificationRepository = new NotificationRepository(context);
        this.userRepository = new UserRepository(context);
    }

    public boolean createStory(int userId, String imagePath, String caption) {
        int id = Math.abs((userId + "_" + System.currentTimeMillis()).hashCode());
        String createdAt = TimeUtils.getCurrentLocalDateTime();

        td.teladoum.baobab.models.User user = userRepository.getUserById(userId);
        String userName = (user != null && user.getName() != null && !user.getName().trim().isEmpty()) ? user.getName() : "Utilisateur";
        String userAvatar = user != null ? user.getProfileImage() : null;

        Story story = new Story(id, userId, userName, userAvatar, imagePath, caption != null ? caption.trim() : "", createdAt);
        storiesCache.put(id, story);

        FirebaseHelper.getInstance().syncStoryToRealtimeDb(story);

        return true;
    }

    public void loadStoriesFromCloud(Runnable onComplete) {
        if (liveStoriesListener == null) {
            liveStoriesListener = new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@androidx.annotation.NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    storiesCache.clear();
                    for (com.google.firebase.database.DataSnapshot snap : snapshot.getChildren()) {
                        Story story = snap.getValue(Story.class);
                        if (story != null) {
                            storiesCache.put(story.getId(), story);
                        }
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }

                @Override
                public void onCancelled(@androidx.annotation.NonNull com.google.firebase.database.DatabaseError error) {
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            };
            FirebaseHelper.getInstance().getRealtimeDb().getReference("stories")
                    .addValueEventListener(liveStoriesListener);
        } else {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    public List<Story> getRecentStories() {
        return new ArrayList<>(storiesCache.values());
    }

    public List<UserStoryGroup> getGroupedRecentStories() {
        List<Story> allStories = getRecentStories();
        List<UserStoryGroup> groups = new ArrayList<>();

        for (Story story : allStories) {
            td.teladoum.baobab.models.User user = userRepository.getUserById(story.getUserId());
            if (user != null) {
                if (user.getProfileImage() != null && !user.getProfileImage().trim().isEmpty()) {
                    story.setUserAvatar(user.getProfileImage());
                }
                if (user.getName() != null && !user.getName().trim().isEmpty()) {
                    story.setUserName(user.getName());
                }
            }

            UserStoryGroup existingGroup = null;
            for (UserStoryGroup group : groups) {
                if (group.getUserId() == story.getUserId()) {
                    existingGroup = group;
                    break;
                }
            }

            if (existingGroup != null) {
                existingGroup.getStories().add(story);
            } else {
                List<Story> userStories = new ArrayList<>();
                userStories.add(story);
                UserStoryGroup newGroup = new UserStoryGroup(
                        story.getUserId(),
                        story.getUserName(),
                        story.getUserAvatar(),
                        userStories
                );
                groups.add(newGroup);
            }
        }

        return groups;
    }

    public boolean setStoryReaction(int storyId, int userId, String reactionType) {
        FirebaseHelper.getInstance().syncStoryReactionToRealtimeDb(storyId, userId, reactionType);
        return true;
    }

    public String getStoryReaction(int storyId, int userId) {
        return null;
    }

    public void recordStoryView(int storyId, int userId) {
        storyViewsCount.put(storyId, storyViewsCount.getOrDefault(storyId, 0) + 1);
    }

    public int getStoryViewsCount(int storyId) {
        return storyViewsCount.getOrDefault(storyId, 0);
    }

    public boolean addStoryComment(int storyId, int userId, String comment) {
        if (comment == null || comment.trim().isEmpty()) return false;
        FirebaseHelper.getInstance().syncStoryCommentToRealtimeDb(storyId, userId, comment.trim());
        return true;
    }
}

