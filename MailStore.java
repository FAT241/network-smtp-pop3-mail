import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

final class MailStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String url = System.getenv().getOrDefault("LAB3_DB_URL",
            "jdbc:postgresql://127.0.0.1:5432/lab3_mail");
    private final String user = System.getenv().getOrDefault("LAB3_DB_USER", "postgres");
    private final String password = System.getenv().getOrDefault("LAB3_DB_PASSWORD", "");

    record Message(long id, String content) {
        int bytes() {
            return content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    void initialize() throws SQLException, IOException {
        try (Connection db = connect(); Statement sql = db.createStatement()) {
            db.setAutoCommit(false);
            sql.execute("""
                    CREATE TABLE IF NOT EXISTS mail_accounts (
                        email text PRIMARY KEY,
                        salt bytea NOT NULL,
                        password_hash bytea NOT NULL
                    )
                    """);
            sql.execute("""
                    CREATE TABLE IF NOT EXISTS mail_messages (
                        id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                        recipient text NOT NULL REFERENCES mail_accounts(email),
                        content text NOT NULL,
                        received_at timestamptz NOT NULL DEFAULT now()
                    )
                    """);
            sql.execute("CREATE INDEX IF NOT EXISTS mail_inbox ON mail_messages (recipient, id)");
            sql.execute("CREATE TABLE IF NOT EXISTS mail_imports (source text PRIMARY KEY)");
            importLegacy(db, Path.of("mail_data"));
            register(db, "sv1@vku.udn.vn", "123456");
            register(db, "sv2@vku.udn.vn", "123456");
            db.commit();
        }
    }

    private void importLegacy(Connection db, Path root) throws IOException, SQLException {
        if (!Files.isDirectory(root)) return;
        try (var folders = Files.list(root)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                Path info = folder.resolve("account_info.txt");
                if (!Files.isRegularFile(info)) continue;
                String email = null;
                String pass = null;
                for (String line : Files.readAllLines(info)) {
                    if (line.startsWith("Tên tài khoản:")) {
                        email = line.substring("Tên tài khoản:".length()).trim().toLowerCase(Locale.ROOT);
                    } else if (line.startsWith("Mật khẩu:")) {
                        pass = line.substring("Mật khẩu:".length()).trim();
                    }
                }
                if (email == null || pass == null) continue;
                register(db, email, pass);
                try (var files = Files.list(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                        String name = file.getFileName().toString();
                        if (!name.startsWith("mail_") || !name.endsWith(".txt")) continue;
                        try (PreparedStatement mark = db.prepareStatement(
                                "INSERT INTO mail_imports VALUES (?) ON CONFLICT DO NOTHING")) {
                            mark.setString(1, root.relativize(file).toString().replace('\\', '/'));
                            if (mark.executeUpdate() == 1) {
                                insert(db, email, Files.readString(file));
                            }
                        }
                    }
                }
            }
        }
    }

    boolean register(String email, String pass) throws SQLException {
        try (Connection db = connect()) {
            return register(db, email, pass);
        }
    }

    private boolean register(Connection db, String email, String pass) throws SQLException {
        if (email.isBlank() || pass.isBlank()) return false;
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        try (PreparedStatement sql = db.prepareStatement(
                "INSERT INTO mail_accounts VALUES (?, ?, ?) ON CONFLICT DO NOTHING")) {
            sql.setString(1, email);
            sql.setBytes(2, salt);
            sql.setBytes(3, hash(pass, salt));
            return sql.executeUpdate() == 1;
        }
    }

    boolean exists(String email) throws SQLException {
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(
                "SELECT 1 FROM mail_accounts WHERE email = ?")) {
            sql.setString(1, email);
            try (ResultSet rows = sql.executeQuery()) {
                return rows.next();
            }
        }
    }

    boolean authenticate(String email, String pass) throws SQLException {
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(
                "SELECT salt, password_hash FROM mail_accounts WHERE email = ?")) {
            sql.setString(1, email);
            try (ResultSet rows = sql.executeQuery()) {
                return rows.next() && MessageDigest.isEqual(rows.getBytes(2), hash(pass, rows.getBytes(1)));
            }
        }
    }

    private static byte[] hash(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 600_000, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        } finally {
            spec.clearPassword();
        }
    }

    void deliver(String sender, List<String> recipients, String subject, String body) throws SQLException {
        try (Connection db = connect()) {
            db.setAutoCommit(false);
            for (String recipient : recipients.stream().distinct().toList()) {
                insert(db, recipient, "From: " + sender + "\r\nTo: " + recipient
                        + "\r\nSubject: " + subject + "\r\n\r\n" + body);
            }
            db.commit();
        }
    }

    private void insert(Connection db, String recipient, String content) throws SQLException {
        try (PreparedStatement sql = db.prepareStatement(
                "INSERT INTO mail_messages (recipient, content) VALUES (?, ?)")) {
            sql.setString(1, recipient);
            sql.setString(2, content);
            sql.executeUpdate();
        }
    }

    List<Message> inbox(String email) throws SQLException {
        List<Message> messages = new ArrayList<>();
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(
                "SELECT id, content FROM mail_messages WHERE recipient = ? ORDER BY id")) {
            sql.setString(1, email);
            try (ResultSet rows = sql.executeQuery()) {
                while (rows.next()) messages.add(new Message(rows.getLong(1), rows.getString(2)));
            }
        }
        return messages;
    }

    void delete(String email, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return;
        try (Connection db = connect(); PreparedStatement sql = db.prepareStatement(
                "DELETE FROM mail_messages WHERE recipient = ? AND id = ANY (?)")) {
            sql.setString(1, email);
            sql.setArray(2, db.createArrayOf("bigint", ids.toArray()));
            sql.executeUpdate();
        }
    }
}
