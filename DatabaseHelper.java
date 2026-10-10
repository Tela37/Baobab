package td.teladoumbaobabtd;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Gestionnaire principal de la base de données SQLite locale.
 * Gère la création des tables, la gestion des contraintes de clés étrangères
 * et les migrations séquentielles explicites (version par version).
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "messagerie.db";
    private static final int DATABASE_VERSION = 11;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTablesIfNotExist(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.setForeignKeyConstraintsEnabled(true);
        createTablesIfNotExist(db);
        execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN video_path TEXT");
        execSqlQuietly(db, "ALTER TABLE group_messages ADD COLUMN video_path TEXT");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 1. S'assurer que les tables fondamentales existent
        createTablesIfNotExist(db);

        // 2. Migrations séquentielles explicites selon la version précédente (oldVersion)
        if (oldVersion < 2) {
            execSqlQuietly(db, "ALTER TABLE conversations ADD COLUMN is_private INTEGER DEFAULT 0");
            execSqlQuietly(db, "ALTER TABLE conversations ADD COLUMN is_pinned INTEGER DEFAULT 0");
        }

        if (oldVersion < 3) {
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN profile_image TEXT");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN first_name TEXT");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN last_name TEXT");
        }

        if (oldVersion < 4) {
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN dob TEXT");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN neighborhood TEXT");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN city TEXT");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN country TEXT");
        }

        if (oldVersion < 5) {
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN is_online INTEGER DEFAULT 1");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN last_seen DATETIME");
            execSqlQuietly(db, "UPDATE users SET last_seen = CURRENT_TIMESTAMP WHERE last_seen IS NULL");
        }

        if (oldVersion < 6) {
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN hide_email INTEGER DEFAULT 0");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN hide_dob INTEGER DEFAULT 0");
            execSqlQuietly(db, "ALTER TABLE users ADD COLUMN hide_location INTEGER DEFAULT 0");
        }

        if (oldVersion < 7) {
            execSqlQuietly(db, "CREATE TABLE IF NOT EXISTS stories (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, image_path TEXT, caption TEXT, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            execSqlQuietly(db, "CREATE TABLE IF NOT EXISTS story_reactions (story_id INTEGER NOT NULL, user_id INTEGER NOT NULL, reaction_type TEXT NOT NULL, PRIMARY KEY (story_id, user_id), FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
        }

        if (oldVersion < 8) {
            execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN reply_to_message_id INTEGER DEFAULT NULL");
            execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN reply_to_text TEXT DEFAULT NULL");
            execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN reaction TEXT DEFAULT NULL");
        }

        if (oldVersion < 9) {
            execSqlQuietly(db, "CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY AUTOINCREMENT, recipient_id INTEGER NOT NULL, sender_id INTEGER NOT NULL, type TEXT NOT NULL, target_id INTEGER NOT NULL, message TEXT NOT NULL, is_read INTEGER DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE, FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE)");
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, is_read)");
        }

        if (oldVersion < 10) {
            execSqlQuietly(db, "ALTER TABLE posts ADD COLUMN video_path TEXT");
        }

        if (oldVersion < 11) {
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id)");
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_messages_read ON messages(is_read)");
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id)");
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at)");
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_messages_conv_read ON messages(conversation_id, is_read)");
            execSqlQuietly(db, "CREATE INDEX IF NOT EXISTS idx_messages_conv_sender ON messages(conversation_id, sender_id)");
        }
    }

    private void execSqlQuietly(SQLiteDatabase db, String sql) {
        try {
            db.execSQL(sql);
        } catch (Exception ignored) {
        }
    }

    private void createTablesIfNotExist(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "first_name TEXT," +
                        "last_name TEXT," +
                        "phone TEXT," +
                        "email TEXT UNIQUE NOT NULL COLLATE NOCASE," +
                        "password TEXT NOT NULL," +
                        "profile_image TEXT," +
                        "dob TEXT," +
                        "neighborhood TEXT," +
                        "city TEXT," +
                        "country TEXT," +
                        "is_online INTEGER DEFAULT 1," +
                        "last_seen DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "hide_email INTEGER DEFAULT 0," +
                        "hide_dob INTEGER DEFAULT 0," +
                        "hide_location INTEGER DEFAULT 0" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS posts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id INTEGER NOT NULL," +
                        "content TEXT," +
                        "image_path TEXT," +
                        "video_path TEXT," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_likes (" +
                        "post_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "PRIMARY KEY (post_id, user_id)," +
                        "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_reactions (" +
                        "post_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "reaction_type TEXT NOT NULL," +
                        "PRIMARY KEY (post_id, user_id)," +
                        "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS post_shares (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "post_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS comments (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "post_id INTEGER NOT NULL," +
                        "parent_id INTEGER DEFAULT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "content TEXT NOT NULL," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (parent_id) REFERENCES comments(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS comment_likes (" +
                        "comment_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "PRIMARY KEY (comment_id, user_id)," +
                        "FOREIGN KEY (comment_id) REFERENCES comments(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS reports (" +
                        "post_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "reason TEXT," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (post_id, user_id)," +
                        "FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS blocks (" +
                        "blocker_id INTEGER NOT NULL," +
                        "blocked_id INTEGER NOT NULL," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (blocker_id, blocked_id)," +
                        "FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS friend_requests (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "sender_id INTEGER NOT NULL," +
                        "receiver_id INTEGER NOT NULL," +
                        "status TEXT DEFAULT 'PENDING'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "UNIQUE(sender_id, receiver_id)," +
                        "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS friends (" +
                        "user1_id INTEGER NOT NULL," +
                        "user2_id INTEGER NOT NULL," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (user1_id, user2_id)," +
                        "FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS `groups` (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "icon TEXT," +
                        "creator_id INTEGER NOT NULL," +
                        "is_locked INTEGER DEFAULT 0," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS group_members (" +
                        "group_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "role TEXT DEFAULT 'MEMBER'," +
                        "joined_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "PRIMARY KEY (group_id, user_id)," +
                        "FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS group_messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "group_id INTEGER NOT NULL," +
                        "sender_id INTEGER NOT NULL," +
                        "message TEXT," +
                        "image_path TEXT," +
                        "video_path TEXT," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (group_id) REFERENCES `groups`(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS group_message_likes (" +
                        "group_message_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "PRIMARY KEY (group_message_id, user_id)," +
                        "FOREIGN KEY (group_message_id) REFERENCES group_messages(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS conversations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user1_id INTEGER NOT NULL," +
                        "user2_id INTEGER NOT NULL," +
                        "is_private INTEGER DEFAULT 0," +
                        "is_pinned INTEGER DEFAULT 0," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "UNIQUE(user1_id, user2_id)," +
                        "FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "conversation_id INTEGER NOT NULL," +
                        "sender_id INTEGER NOT NULL," +
                        "message TEXT," +
                        "image_path TEXT," +
                        "video_path TEXT," +
                        "is_read INTEGER DEFAULT 0," +
                        "is_delivered INTEGER DEFAULT 1," +
                        "is_deleted INTEGER DEFAULT 0," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        // Table des stories éphémères (24h)
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id INTEGER NOT NULL," +
                        "image_path TEXT," +
                        "caption TEXT," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        // Table des réactions émojis sur les stories
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS story_reactions (" +
                        "story_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "reaction_type TEXT NOT NULL," +
                        "PRIMARY KEY (story_id, user_id)," +
                        "FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        // Table des notifications d'interactions (réactions, commentaires)
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS notifications (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "recipient_id INTEGER NOT NULL," +
                        "sender_id INTEGER NOT NULL," +
                        "type TEXT NOT NULL," +
                        "target_type TEXT NOT NULL DEFAULT 'POST'," +
                        "target_id INTEGER NOT NULL," +
                        "message TEXT NOT NULL," +
                        "is_read INTEGER DEFAULT 0," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (recipient_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        // Indexation SQLite pour hautes performances
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email COLLATE NOCASE);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_posts_user ON posts(user_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments(post_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comments_parent ON comments(parent_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_friend_requests_recv ON friend_requests(receiver_id, status);");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_friend_requests_sender_receiver ON friend_requests(sender_id, receiver_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stories_user ON stories(user_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notifications_recipient ON notifications(recipient_id, is_read);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conversation ON messages(conversation_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages(sender_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_created ON messages(created_at);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_read ON messages(is_read);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv_read ON messages(conversation_id, is_read);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv_sender ON messages(conversation_id, sender_id);");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_conversations_user1_user2 ON conversations(user1_id, user2_id);");

        // Table des réactions émojis sur les messages (Multi-utilisateurs)
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS message_reactions (" +
                        "message_id INTEGER NOT NULL," +
                        "user_id INTEGER NOT NULL," +
                        "reaction_type TEXT NOT NULL," +
                        "PRIMARY KEY (message_id, user_id)," +
                        "FOREIGN KEY (message_id) REFERENCES messages(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        // Migrations dynamiques de sécurité pour requêtes existantes
        execSqlQuietly(db, "ALTER TABLE notifications ADD COLUMN target_type TEXT DEFAULT 'POST'");
        execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN reply_to_message_id INTEGER DEFAULT NULL");
        execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN reply_to_text TEXT DEFAULT NULL");
        execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN reaction TEXT DEFAULT NULL");
        execSqlQuietly(db, "ALTER TABLE messages ADD COLUMN is_deleted INTEGER DEFAULT 0");
        execSqlQuietly(db, "ALTER TABLE posts ADD COLUMN video_path TEXT");
        execSqlQuietly(db, "ALTER TABLE conversations ADD COLUMN is_private INTEGER DEFAULT 0");
        execSqlQuietly(db, "ALTER TABLE conversations ADD COLUMN is_pinned INTEGER DEFAULT 0");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN profile_image TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN first_name TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN last_name TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN dob TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN neighborhood TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN city TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN country TEXT");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN is_online INTEGER DEFAULT 1");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN last_seen DATETIME");
        execSqlQuietly(db, "UPDATE users SET last_seen = CURRENT_TIMESTAMP WHERE last_seen IS NULL");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN hide_email INTEGER DEFAULT 0");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN hide_dob INTEGER DEFAULT 0");
        execSqlQuietly(db, "ALTER TABLE users ADD COLUMN hide_location INTEGER DEFAULT 0");
    }
}