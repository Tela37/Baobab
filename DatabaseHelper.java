package td.teladoumbaobabtd;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "messages.db";
    private static final int DATABASE_VERSION = 15;

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME,
                null, DATABASE_VERSION);
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
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        createTablesIfNotExist(db);
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
                "CREATE TABLE IF NOT EXISTS conversations (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user1_id INTEGER NOT NULL," +
                        "user2_id INTEGER NOT NULL," +
                        "is_archived INTEGER DEFAULT 0," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS messages (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "conversation_id INTEGER NOT NULL," +
                        "sender_id INTEGER NOT NULL," +
                        "message TEXT NOT NULL," +
                        "image_path TEXT," +
                        "audio_path TEXT," +
                        "pdf_path TEXT," +
                        "is_read INTEGER DEFAULT 0," +
                        "is_delivered INTEGER DEFAULT 0," +
                        "is_edited INTEGER DEFAULT 0," +
                        "is_starred INTEGER DEFAULT 0," +
                        "read_at DATETIME," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                        "FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE," +
                        "FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE" +
                        ")"
        );

        db.execSQL(
                "CREATE TABLE IF NOT EXISTS posts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id INTEGER NOT NULL," +
                        "content TEXT," +
                        "image_path TEXT," +
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
                "CREATE TABLE IF NOT EXISTS friend_requests (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "sender_id INTEGER NOT NULL," +
                        "receiver_id INTEGER NOT NULL," +
                        "status TEXT DEFAULT 'PENDING'," +
                        "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
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

        // Indexation SQLite pour hautes performances
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_conv ON messages(conversation_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_unread ON messages(conversation_id, sender_id, is_read);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email COLLATE NOCASE);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_conversations_users ON conversations(user1_id, user2_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_posts_user ON posts(user_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comments_post ON comments(post_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_comments_parent ON comments(parent_id);");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_friend_requests_recv ON friend_requests(receiver_id, status);");

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN profile_image TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN first_name TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN last_name TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN dob TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN neighborhood TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN city TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN country TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN is_online INTEGER DEFAULT 1");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN last_seen DATETIME");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("UPDATE users SET last_seen = CURRENT_TIMESTAMP WHERE last_seen IS NULL");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN hide_email INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN hide_dob INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE users ADD COLUMN hide_location INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE conversations ADD COLUMN is_archived INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN is_delivered INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN image_path TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN audio_path TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN pdf_path TEXT");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN is_edited INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN is_starred INTEGER DEFAULT 0");
        } catch (Exception ignored) {
        }

        try {
            db.execSQL("ALTER TABLE messages ADD COLUMN read_at DATETIME");
        } catch (Exception ignored) {
        }
    }
}
