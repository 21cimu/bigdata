package com.hdfsdrive.core;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserDao {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/bigdata?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASS = "123456";

    static {
        try {
            // ensure driver loaded
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        // create table if not exists; store password in plaintext per requirement and add avatar column
        try (Connection c = getConnection()) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("CREATE TABLE IF NOT EXISTS users (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, password VARCHAR(255) NOT NULL, avatar VARCHAR(1024) DEFAULT NULL, email VARCHAR(255) DEFAULT NULL, phone VARCHAR(50) DEFAULT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            }
            // --- BEGIN: ensure required columns exist (for compatibility with older schemas) ---
            try {
                // Ensure created_at exists (older schemas may lack it)
                try (PreparedStatement psCreated = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'created_at'")) {
                    try (ResultSet rsCreated = psCreated.executeQuery()) {
                        if (rsCreated.next() && rsCreated.getInt(1) == 0) {
                            try (Statement s2 = c.createStatement()) {
                                s2.executeUpdate("ALTER TABLE users ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP");
                            }
                        }
                    }
                } catch (SQLException ignoreCreated) {
                    // best-effort: if unable to add created_at, ignore and continue; listUsers will fallback
                }

                // check if 'password' column exists
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = ?")) {
                    ps.setString(1, "password");
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            try (Statement s = c.createStatement()) {
                                // add password column; allow NULL to be safe for existing rows
                                s.executeUpdate("ALTER TABLE users ADD COLUMN password VARCHAR(255) DEFAULT NULL");
                            }
                        }
                    }
                }
                // ensure email column
                try (PreparedStatement psEmail = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'email'")) {
                    try (ResultSet rsEmail = psEmail.executeQuery()) {
                        if (rsEmail.next() && rsEmail.getInt(1) == 0) {
                            try (Statement s = c.createStatement()) {
                                s.executeUpdate("ALTER TABLE users ADD COLUMN email VARCHAR(255) DEFAULT NULL");
                            }
                        }
                    }
                } catch (SQLException ignoreEmail) {
                    // best-effort
                }
                // ensure phone column
                try (PreparedStatement psPhone = c.prepareStatement(
                        "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'phone'")) {
                    try (ResultSet rsPhone = psPhone.executeQuery()) {
                        if (rsPhone.next() && rsPhone.getInt(1) == 0) {
                            try (Statement s = c.createStatement()) {
                                s.executeUpdate("ALTER TABLE users ADD COLUMN phone VARCHAR(50) DEFAULT NULL");
                            }
                        }
                    }
                } catch (SQLException ignorePhone) {
                    // best-effort
                }

                // check avatar column: if missing -> add, else if too small or not TEXT-like -> convert to TEXT
                try (PreparedStatement ps2 = c.prepareStatement(
                        "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'avatar'")) {
                    try (ResultSet rs = ps2.executeQuery()) {
                        if (!rs.next()) {
                            try (Statement s = c.createStatement()) {
                                s.executeUpdate("ALTER TABLE users ADD COLUMN avatar TEXT DEFAULT NULL");
                            }
                        } else {
                            String dataType = rs.getString("DATA_TYPE");
                            Long charMax = rs.getObject("CHARACTER_MAXIMUM_LENGTH") == null ? null : rs.getLong("CHARACTER_MAXIMUM_LENGTH");
                            boolean needsChange = false;
                            if (dataType == null) dataType = "";
                            String dt = dataType.toLowerCase();
                            // If current type is not text/blob and has small max length, convert to TEXT
                            if (!(dt.contains("text") || dt.contains("blob"))) {
                                if (charMax == null || charMax < 2048) {
                                    needsChange = true;
                                }
                            }
                            if (needsChange) {
                                try (Statement s = c.createStatement()) {
                                    s.executeUpdate("ALTER TABLE users MODIFY COLUMN avatar TEXT DEFAULT NULL");
                                }
                            }
                        }
                    }
                }
            } catch (SQLException ignore) {
                // best-effort; if information_schema query fails (older MySQL or restricted privileges), ignore and continue
            }
            // --- END ---
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize users table", e);
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
    }

    // Public helper to ensure avatar column can store longer values; intended to be called before updates where avatar may be long
    public static void ensureAvatarColumnIsText() throws SQLException {
        try (Connection c = getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'avatar'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        try (Statement s = c.createStatement()) {
                            s.executeUpdate("ALTER TABLE users ADD COLUMN avatar TEXT DEFAULT NULL");
                        }
                        return;
                    }
                    String dataType = rs.getString("DATA_TYPE");
                    Long charMax = rs.getObject("CHARACTER_MAXIMUM_LENGTH") == null ? null : rs.getLong("CHARACTER_MAXIMUM_LENGTH");
                    if (dataType == null) dataType = "";
                    String dt = dataType.toLowerCase();
                    if (!(dt.contains("text") || dt.contains("blob"))) {
                        if (charMax == null || charMax < 2048) {
                            try (Statement s = c.createStatement()) {
                                s.executeUpdate("ALTER TABLE users MODIFY COLUMN avatar TEXT DEFAULT NULL");
                            }
                        }
                    }
                }
            }
        }
    }

    public static User findByUsername(String username) throws SQLException {
        try (Connection c = getConnection()) {
            String sql = "SELECT id, username, password, avatar, email, phone FROM users WHERE username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User u = new User(rs.getLong("id"), rs.getString("username"), rs.getString("password"));
                        // populate avatar which was previously not set
                        u.setAvatar(rs.getString("avatar"));
                        try { u.setEmail(rs.getString("email")); } catch (SQLException ignore) { u.setEmail(null); }
                        try { u.setPhone(rs.getString("phone")); } catch (SQLException ignore) { u.setPhone(null); }
                        return u;
                    }
                }
            }
        }
        return null;
    }

    public static User findById(long id) throws SQLException {
        try (Connection c = getConnection()) {
            String sql = "SELECT id, username, password, avatar, email, phone FROM users WHERE id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User u = new User(rs.getLong("id"), rs.getString("username"), rs.getString("password"));
                        u.setAvatar(rs.getString("avatar"));
                        try { u.setEmail(rs.getString("email")); } catch (SQLException ignore) { u.setEmail(null); }
                        try { u.setPhone(rs.getString("phone")); } catch (SQLException ignore) { u.setPhone(null); }
                        return u;
                    }
                }
            }
        }
        return null;
    }

    public static boolean createUser(String username, String password) throws SQLException {
        // Delegate to the extended overload which accepts createdAt (null means use DB default CURRENT_TIMESTAMP)
        return createUser(username, password, null, null, null);
    }

    /**
     * Create a user and optionally set the created_at timestamp (milliseconds since epoch).
     * If createdAt is null or <= 0, the DB default CURRENT_TIMESTAMP will be used.
     */
    public static boolean createUser(String username, String password, Long createdAt) throws SQLException {
        return createUser(username, password, null, createdAt);
    }

    public static boolean createUser(String username, String password, String email, Long createdAt) throws SQLException {
        return createUser(username, password, email, null, createdAt);
    }

    public static boolean createUser(String username, String password, String email, String phone, Long createdAt) throws SQLException {
        try (Connection c = getConnection()) {
            String emailValue = (email == null || email.trim().isEmpty()) ? null : email.trim();
            String phoneValue = (phone == null || phone.trim().isEmpty()) ? null : phone.trim();
            if (createdAt == null || createdAt <= 0) {
                String sql = "INSERT INTO users (username, password, email, phone) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, password);
                    ps.setString(3, emailValue);
                    ps.setString(4, phoneValue);
                    int r = ps.executeUpdate();
                    return r == 1;
                }
            } else {
                String sql = "INSERT INTO users (username, password, email, phone, created_at) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, username);
                    ps.setString(2, password);
                    ps.setString(3, emailValue);
                    ps.setString(4, phoneValue);
                    ps.setTimestamp(5, new Timestamp(createdAt));
                    int r = ps.executeUpdate();
                    return r == 1;
                } catch (SQLException e) {
                    // If created_at column does not exist (older schema), fallback to insert without created_at
                    String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                    if (msg.contains("unknown column") || msg.contains("created_at")) {
                        String sql2 = "INSERT INTO users (username, password, email, phone) VALUES (?, ?, ?, ?)";
                        try (PreparedStatement ps2 = c.prepareStatement(sql2)) {
                            ps2.setString(1, username);
                            ps2.setString(2, password);
                            ps2.setString(3, emailValue);
                            ps2.setString(4, phoneValue);
                            int r2 = ps2.executeUpdate();
                            return r2 == 1;
                        }
                    }
                    throw e;
                }
             }
         }
     }

    public static boolean updateUser(long id, String newUsername, String newPassword, String avatar, String email, String phone) throws SQLException {
        try (Connection c = getConnection()) {
            String sql = "UPDATE users SET username = ?, password = ?, avatar = ?, email = ?, phone = ? WHERE id = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, newUsername);
                ps.setString(2, newPassword);
                ps.setString(3, avatar);
                ps.setString(4, email);
                ps.setString(5, phone);
                ps.setLong(6, id);
                int r = ps.executeUpdate();
                return r == 1;
            }
        }
    }

    // 列出所有用户（供管理员界面使用）
    public static List<Map<String,Object>> listUsers() throws SQLException {
        List<Map<String,Object>> out = new ArrayList<>();
        try (Connection c = getConnection()) {
            String sql = "SELECT id, username, avatar, email, phone, created_at FROM users ORDER BY id DESC";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> m = new HashMap<>();
                        m.put("id", rs.getLong("id"));
                        m.put("username", rs.getString("username"));
                        try { m.put("email", rs.getString("email")); } catch (SQLException e) { m.put("email", ""); }
                        try { m.put("phone", rs.getString("phone")); } catch (SQLException e) { m.put("phone", ""); }
                        // role: default user, special-case 'admin' username
                        String uname = rs.getString("username");
                        m.put("role", "admin".equalsIgnoreCase(uname) ? "admin" : "user");
                        Timestamp ts = null;
                        try {
                            ts = rs.getTimestamp("created_at");
                        } catch (SQLException e) {
                            // If created_at column disappears between prepare and fetch, fallback below
                            ts = null;
                        }
                        m.put("createdAt", ts == null ? 0 : ts.getTime());
                        m.put("avatar", rs.getString("avatar"));
                        out.add(m);
                    }
                }
            } catch (SQLException e) {
                // Fallback for databases without created_at column: query without it
                String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
                if (msg.contains("unknown column") || msg.contains("created_at")) {
                    String sql2 = "SELECT id, username, avatar, email, phone FROM users ORDER BY id DESC";
                    try (PreparedStatement ps2 = c.prepareStatement(sql2)) {
                        try (ResultSet rs2 = ps2.executeQuery()) {
                            while (rs2.next()) {
                                Map<String,Object> m = new HashMap<>();
                                m.put("id", rs2.getLong("id"));
                                m.put("username", rs2.getString("username"));
                                try { m.put("email", rs2.getString("email")); } catch (SQLException e2) { m.put("email", ""); }
                                try { m.put("phone", rs2.getString("phone")); } catch (SQLException e2) { m.put("phone", ""); }
                                String uname = rs2.getString("username");
                                m.put("role", "admin".equalsIgnoreCase(uname) ? "admin" : "user");
                                m.put("avatar", rs2.getString("avatar"));
                                m.put("createdAt", 0);
                                out.add(m);
                            }
                        }
                    }
                } else {
                    throw e; // rethrow unexpected SQL errors
                }
            }
         }
         return out;
     }

    // 删除用户（按用户名）
    public static boolean deleteUserByUsername(String username) throws SQLException {
        try (Connection c = getConnection()) {
            String sql = "DELETE FROM users WHERE username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, username);
                int r = ps.executeUpdate();
                return r > 0;
            }
        }
    }

    // 重置用户密码（按用户名）
    public static boolean resetPasswordByUsername(String username, String newPassword) throws SQLException {
        try (Connection c = getConnection()) {
            String sql = "UPDATE users SET password = ? WHERE username = ?";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, newPassword);
                ps.setString(2, username);
                int r = ps.executeUpdate();
                return r > 0;
            }
        }
    }
}
