package td.teladoumbaobabtd;

/**
 * Modèle représentant un groupe de discussion.
 * Contient les informations de groupe, le créateur/administrateur,
 * le statut de verrouillage par l'administrateur (isLocked) et le nombre de membres.
 */
public class Group {

    private int id;
    private String name;
    private String icon;
    private int creatorId;
    private boolean isLocked;
    private String createdAt;
    private int memberCount;
    private String lastMessage;

    public Group() {
    }

    public Group(int id, String name, String icon, int creatorId, boolean isLocked, String createdAt, int memberCount) {
        this.id = id;
        this.name = name;
        this.icon = icon;
        this.creatorId = creatorId;
        this.isLocked = isLocked;
        this.createdAt = createdAt;
        this.memberCount = memberCount;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Groupe";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public int getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public String getLastMessage() {
        return lastMessage != null ? lastMessage : "Aucun message";
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }
}
