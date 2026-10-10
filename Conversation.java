package td.teladoumbaobabtd;

/**
 * Modèle représentant une relation de conversation entre deux utilisateurs dans SQLite.
 */
public class Conversation {

    private int id;
    private int user1Id;
    private int user2Id;
    private String createdAt;

    public Conversation(int id,
                        int user1Id,
                        int user2Id,
                        String createdAt) {

        this.id = id;
        this.user1Id = user1Id;
        this.user2Id = user2Id;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getUser1Id() {
        return user1Id;
    }

    public int getUser2Id() {
        return user2Id;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setUser1Id(int user1Id) {
        this.user1Id = user1Id;
    }

    public void setUser2Id(int user2Id) {
        this.user2Id = user2Id;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}