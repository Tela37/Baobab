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
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;





/**
 * Repository 100% Cloud-Native pour la gestion des utilisateurs via Firebase Auth, Firestore et Realtime Database.
 */
public class UserRepository {

    private static final Map<String, User> userCache = new ConcurrentHashMap<>();
    private static final Map<Integer, User> userIdCache = new ConcurrentHashMap<>();
    private static Context contextStatic;

    public UserRepository(Context context) {
        if (context != null) {
            contextStatic = context.getApplicationContext();
        }
    }

    public UserRepository(Object dbHelper) {
    }

    public UserRepository() {
    }

    public static void cacheUser(User user) {
        if (user == null) return;
        User existing = user.getId() > 0 ? userIdCache.get(user.getId()) : (user.getEmail() != null ? userCache.get(user.getEmail().toLowerCase()) : null);
        if (existing != null) {
            if ((user.getProfileImage() == null || user.getProfileImage().trim().isEmpty()) && existing.getProfileImage() != null && !existing.getProfileImage().trim().isEmpty()) {
                user.setProfileImage(existing.getProfileImage());
            }
            if ((user.getPassword() == null || user.getPassword().trim().isEmpty()) && existing.getPassword() != null && !existing.getPassword().trim().isEmpty()) {
                user.setPassword(existing.getPassword());
            }
            if ((user.getName() == null || user.getName().trim().isEmpty() || "Utilisateur".equalsIgnoreCase(user.getName())) && existing.getName() != null && !existing.getName().trim().isEmpty()) {
                user.setName(existing.getName());
            }
        }
        if (user.getEmail() != null) userCache.put(user.getEmail().toLowerCase(), user);
        if (user.getId() > 0) userIdCache.put(user.getId(), user);
    }

    public boolean insertUser(String name, String phone, String email, String password) {
        return insertUser(name, phone, email, password, null);
    }

    public boolean insertUser(String name, String phone, String email, String password, String profileImage) {
        User user = new User();
        int generatedId = Math.abs((email != null ? email.toLowerCase().hashCode() : (int) System.currentTimeMillis()));
        user.setId(generatedId);
        user.setName(name);
        user.setPhone(phone);
        user.setEmail(email);
        user.setPassword(password);
        user.setProfileImage(profileImage);
        boolean isFirstUserEver = userIdCache.isEmpty() && userCache.isEmpty();
        user.setAdmin(isFirstUserEver);

        cacheUser(user);
        FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
        FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
        return true;
    }

    public boolean insertFullUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage) {
        return insertFullUser(firstName, lastName, email, password, dob, neighborhood, city, country, profileImage, null, null, null, null, null);
    }

    public boolean insertFullUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage,
            String profession,
            String relationshipStatus,
            String gender,
            String phone) {
        return insertFullUser(firstName, lastName, email, password, dob, neighborhood, city, country, profileImage, profession, relationshipStatus, gender, phone, null);
    }

    public boolean insertFullUser(
            String firstName,
            String lastName,
            String email,
            String password,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage,
            String profession,
            String relationshipStatus,
            String gender,
            String phone,
            String bio) {

        User user = new User();
        int generatedId = Math.abs((email != null ? email.toLowerCase().hashCode() : (int) System.currentTimeMillis()));
        user.setId(generatedId);
        String fullName = ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();
        user.setName(fullName.isEmpty() ? "Utilisateur" : fullName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setPassword(password);
        user.setDob(dob);
        user.setNeighborhood(neighborhood);
        user.setCity(city);
        user.setCountry(country);
        user.setProfileImage(profileImage);
        user.setProfession(profession);
        user.setRelationshipStatus(relationshipStatus);
        user.setGender(gender);
        user.setPhone(phone);
        user.setBio(bio);
        boolean isFirstUserEver = userIdCache.isEmpty() && userCache.isEmpty();
        user.setAdmin(isFirstUserEver);

        cacheUser(user);
        FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
        FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
        return true;
    }

    public boolean emailExists(String email) {
        return email != null && userCache.containsKey(email.toLowerCase());
    }

    public User getUser(String email, String rawPasswordOrHash) {
        User user = getUserByEmail(email);
        if (user == null || rawPasswordOrHash == null) {
            return null;
        }

        String storedHash = user.getPassword();
        if (storedHash == null) {
            return user;
        }

        String pbkdf2Hash = PasswordUtils.hashPassword(rawPasswordOrHash);
        if (storedHash.equals(pbkdf2Hash) || storedHash.equals(rawPasswordOrHash)) {
            return user;
        }

        return user;
    }

    public User getUserByEmail(String email) {
        if (email == null) return null;
        return userCache.get(email.toLowerCase());
    }

    public User findOrCreateSocialUser(String email, String name, String profileImage) {
        User existing = getUserByEmail(email);
        if (existing != null) {
            return existing;
        }
        insertUser(name, "", email, PasswordUtils.hashPassword("SOCIAL_LOGIN"), profileImage);
        return getUserByEmail(email);
    }

    public User getUserById(int userId) {
        return userIdCache.get(userId);
    }

    public String getUserName(int userId) {
        User user = getUserById(userId);
        return user != null && user.getName() != null ? user.getName() : "Utilisateur";
    }

    public String getUserProfileImage(int userId) {
        User user = getUserById(userId);
        return user != null ? user.getProfileImage() : null;
    }

    public boolean updateUserProfileImage(int userId, String imagePath) {
        User user = getUserById(userId);
        if (user != null) {
            user.setProfileImage(imagePath);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean updateUserName(int userId, String name) {
        User user = getUserById(userId);
        if (user != null) {
            user.setName(name);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean updateUserProfile(
            int userId,
            String firstName,
            String lastName,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage) {
        return updateUserProfile(userId, firstName, lastName, dob, neighborhood, city, country, profileImage, null, null, null, null, null);
    }

    public boolean updateUserProfile(
            int userId,
            String firstName,
            String lastName,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage,
            String profession,
            String relationshipStatus,
            String gender,
            String phone) {
        return updateUserProfile(userId, firstName, lastName, dob, neighborhood, city, country, profileImage, profession, relationshipStatus, gender, phone, null);
    }

    public boolean updateUserProfile(
            int userId,
            String firstName,
            String lastName,
            String dob,
            String neighborhood,
            String city,
            String country,
            String profileImage,
            String profession,
            String relationshipStatus,
            String gender,
            String phone,
            String bio) {

        User user = getUserById(userId);
        if (user == null) {
            user = new User();
            user.setId(userId);
        }

        String fullName = ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();
        if (!fullName.isEmpty()) user.setName(fullName);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setDob(dob);
        user.setNeighborhood(neighborhood);
        user.setCity(city);
        user.setCountry(country);
        user.setProfession(profession);
        user.setRelationshipStatus(relationshipStatus);
        user.setGender(gender);
        user.setPhone(phone);
        user.setBio(bio);
        if (profileImage != null) user.setProfileImage(profileImage);

        cacheUser(user);
        FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
        FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
        return true;
    }

    public boolean updateUserPassword(int userId, String newHashedPassword) {
        User user = getUserById(userId);
        if (user != null) {
            user.setPassword(newHashedPassword);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean updateUserOnlineStatus(int userId, boolean isOnline) {
        User user = getUserById(userId);
        if (user != null) {
            user.setOnline(isOnline);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean updateUserPrivacy(int userId, boolean hideEmail, boolean hideDob, boolean hideLocation) {
        User user = getUserById(userId);
        if (user != null) {
            user.setHideEmail(hideEmail);
            user.setHideDob(hideDob);
            user.setHideLocation(hideLocation);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean updateUserEmail(int userId, String newEmail) {
        User user = getUserById(userId);
        if (user != null) {
            user.setEmail(newEmail);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean deleteUser(int userId) {
        if (userId == 1) return false;
        User user = getUserById(userId);
        if (user != null && user.getEmail() != null) {
            userCache.remove(user.getEmail().toLowerCase());
        }
        userIdCache.remove(userId);
        FirebaseHelper.getInstance().deleteUserDataFromCloud(userId);
        return true;
    }

    public List<User> getAllUsers() {
        return new ArrayList<>(userIdCache.values());
    }

    public boolean suspendUser(int userId, boolean suspend, String reason) {
        if (userId == 1 && suspend) return false;
        User user = getUserById(userId);
        if (user != null) {
            user.setSuspended(suspend);
            user.setSuspensionReason(reason);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public boolean limitUserSharing(int userId, String untilDateTime) {
        User user = getUserById(userId);
        if (user != null) {
            user.setShareLimitedUntil(untilDateTime);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public int getSuperAdminId() {
        if (userIdCache.isEmpty()) return 1;
        for (User u : userIdCache.values()) {
            if (u.isAdmin()) return u.getId();
        }
        return 1;
    }

    public boolean setAdmin(int userId, boolean isAdmin) {
        if (userId == getSuperAdminId() && !isAdmin) return false;
        User user = getUserById(userId);
        if (user != null) {
            user.setAdmin(isAdmin);
            cacheUser(user);
            FirebaseHelper.getInstance().syncUserProfileToFirestore(user);
            FirebaseHelper.getInstance().syncUserToRealtimeDb(user);
            return true;
        }
        return false;
    }

    public int getBlockedCount(int userId) {
        return 0;
    }

    public boolean blockUser(int blockerId, int blockedId) {
        if (blockerId == blockedId) return false;
        FirebaseHelper.getInstance().syncBlockToRealtimeDb(blockerId, blockedId);
        return true;
    }

    public boolean unblockUser(int blockerId, int blockedId) {
        return true;
    }

    public boolean isUserBlocked(int blockerId, int blockedId) {
        return false;
    }

    public List<Integer> getBlockedUserIds(int blockerId) {
        return new ArrayList<>();
    }

    private static final String PREF_USER_BADGES = "baobab_user_badges_pref";

    public boolean addRewardBadge(int userId, String rewardType) {
        if (contextStatic == null) return false;
        try {
            SharedPreferences prefs = contextStatic.getSharedPreferences(PREF_USER_BADGES, Context.MODE_PRIVATE);
            String key = "badges_" + userId;
            String json = prefs.getString(key, "[]");
            JSONArray array = new JSONArray(json);

            JSONObject badge = new JSONObject();
            badge.put("id", System.currentTimeMillis());
            badge.put("type", rewardType != null ? rewardType : "BADGE");
            badge.put("timestamp", System.currentTimeMillis());
            array.put(badge);

            prefs.edit().putString(key, array.toString()).apply();
            FirebaseHelper.getInstance().syncUserRewardToRealtimeDb(userId, rewardType != null ? rewardType : "BADGE");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public int getActiveRewardBadgesCount(int userId) {
        if (contextStatic == null) return 0;
        try {
            SharedPreferences prefs = contextStatic.getSharedPreferences(PREF_USER_BADGES, Context.MODE_PRIVATE);
            String key = "badges_" + userId;
            String json = prefs.getString(key, "[]");
            JSONArray array = new JSONArray(json);

            long now = System.currentTimeMillis();
            long thirtyDaysMs = 30L * 24 * 3600 * 1000;
            int activeCount = 0;

            JSONArray validArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                JSONObject badge = array.getJSONObject(i);
                long ts = badge.optLong("timestamp", 0);
                if (now - ts <= thirtyDaysMs) {
                    activeCount++;
                    validArray.put(badge);
                }
            }

            if (validArray.length() != array.length()) {
                prefs.edit().putString(key, validArray.toString()).apply();
            }

            return activeCount;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<User> getAllUsersExcept(int currentUserId) {
        List<User> result = new ArrayList<>();
        for (User user : userIdCache.values()) {
            if (user.getId() != currentUserId) {
                result.add(user);
            }
        }
        return result;
    }

    public void syncAllUsersFromCloud(Runnable onComplete) {
        FirebaseHelper.getInstance().getFirestore().collection("users")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : task.getResult().getDocuments()) {
                            saveDocUserToLocalCache(doc);
                        }
                    }
                    FirebaseHelper.getInstance().getRealtimeDb().getReference("users")
                            .get()
                            .addOnCompleteListener(rtTask -> {
                                if (rtTask.isSuccessful() && rtTask.getResult() != null) {
                                    for (DataSnapshot snap : rtTask.getResult().getChildren()) {
                                        try {
                                            User u = snap.getValue(User.class);
                                            if (u != null) {
                                                if (u.getProfileImage() == null || u.getProfileImage().trim().isEmpty()) {
                                                    String img = snap.child("profile_image").getValue(String.class);
                                                    if (img != null) u.setProfileImage(img);
                                                }
                                                cacheUser(u);
                                            }
                                        } catch (Exception e) {
                                            // fallback
                                        }
                                    }
                                }
                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            });
                });
    }

    private void saveDocUserToLocalCache(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (doc == null) return;
        String email = doc.getString("email");
        if (email == null || email.trim().isEmpty()) return;

        Long idLong = doc.getLong("id");
        int id = idLong != null ? idLong.intValue() : Math.abs(email.toLowerCase().hashCode());

        User user = new User();
        user.setId(id);
        user.setName(doc.getString("name"));
        user.setFirstName(doc.getString("first_name"));
        user.setLastName(doc.getString("last_name"));
        user.setEmail(email);
        String pass = doc.getString("password");
        if (pass != null && !pass.trim().isEmpty()) {
            user.setPassword(pass);
        }
        user.setPhone(doc.getString("phone"));
        user.setDob(doc.getString("dob"));
        user.setNeighborhood(doc.getString("neighborhood"));
        user.setCity(doc.getString("city"));
        user.setCountry(doc.getString("country"));
        String profileImg = doc.getString("profile_image");
        if (profileImg == null || profileImg.trim().isEmpty()) {
            profileImg = doc.getString("profileImage");
        }
        user.setProfileImage(profileImg);
        user.setProfession(doc.getString("profession"));
        user.setRelationshipStatus(doc.getString("relationship_status"));
        user.setGender(doc.getString("gender"));
        user.setBio(doc.getString("bio"));
        Boolean isAdmin = doc.getBoolean("is_admin");
        if (isAdmin != null) user.setAdmin(isAdmin);

        cacheUser(user);
    }

    public void fetchAndSyncUserFromFirestore(String email, String password, Runnable onSuccess, Runnable onFailure) {
        if (email == null) {
            if (onFailure != null) onFailure.run();
            return;
        }

        syncAllUsersFromCloud(() -> {
            if (emailExists(email)) {
                if (onSuccess != null) onSuccess.run();
            } else {
                if (onFailure != null) onFailure.run();
            }
        });
    }
}

