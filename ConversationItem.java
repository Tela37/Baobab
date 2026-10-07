package td.teladoumbaobabtd;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ConversationItem {

    private int conversationId;
    private String userName;
    private String lastMessage;
    private String profileImage;
    private int unreadCount;
    private boolean isOnline;
    private String lastSeen;

    public ConversationItem(int conversationId,
                            String userName,
                            String lastMessage,
                            String profileImage,
                            int unreadCount) {

        this.conversationId = conversationId;
        this.userName = userName;
        this.lastMessage = lastMessage;
        this.profileImage = profileImage;
        this.unreadCount = unreadCount;
    }

    public ConversationItem(int conversationId,
                            String userName,
                            String lastMessage,
                            String profileImage,
                            int unreadCount,
                            boolean isOnline,
                            String lastSeen) {

        this.conversationId = conversationId;
        this.userName = userName;
        this.lastMessage = lastMessage;
        this.profileImage = profileImage;
        this.unreadCount = unreadCount;
        this.isOnline = isOnline;
        this.lastSeen = lastSeen;
    }

    public int getConversationId() {
        return conversationId;
    }

    public String getUserName() {
        return userName == null ? "Utilisateur" : userName;
    }

    public String getLastMessage() {
        return lastMessage == null ? "Aucun message" : lastMessage;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public int getUnreadCount() {
        return unreadCount;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public String getLastSeen() {
        return lastSeen;
    }

    public String getPresenceStatus() {
        if (isOnline) {
            return "En ligne 🟢";
        }
        if (lastSeen == null || lastSeen.trim().isEmpty()) {
            return "Hors ligne";
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(lastSeen);
            if (date == null) return "Hors ligne";

            long diffMillis = System.currentTimeMillis() - date.getTime();
            long diffMins = diffMillis / (1000 * 60);

            if (diffMins < 0) return "En ligne 🟢";

            if (diffMins < 60) {
                return "En ligne il y a " + Math.max(1, diffMins) + " min";
            } else if (diffMins <= 120) {
                long hours = diffMins / 60;
                return "En ligne il y a " + hours + " h";
            } else {
                return "Hors ligne";
            }
        } catch (Exception e) {
            return "Hors ligne";
        }
    }
}
