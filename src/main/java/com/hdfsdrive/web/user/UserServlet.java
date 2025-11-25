package com.hdfsdrive.web.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.LogUtil;
import com.hdfsdrive.core.User;
import com.hdfsdrive.core.UserDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/user")
public class UserServlet extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJson(resp, mapOf("success", false, "message", "未登录"));
            return;
        }
        try {
            long id = ((Number) s.getAttribute("userId")).longValue();
            User u = UserDao.findById(id);
            if (u == null) {
                sendJson(resp, mapOf("success", false, "message", "用户不存在"));
                return;
            }
            Map<String, Object> out = new HashMap<>();
            out.put("success", true);
            out.put("user", mapOf(
                    "id", u.getId(),
                    "username", u.getUsername(),
                    "avatar", u.getAvatar(),
                    "email", u.getEmail(),
                    "phone", u.getPhone()
            ));
            sendJson(resp, out);
        } catch (Exception e) {
            sendJson(resp, mapOf("success", false, "message", e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJson(resp, mapOf("success", false, "message", "未登录"));
            return;
        }
        try {
            Map<String, Object> body = mapper.readValue(req.getInputStream(), Map.class);
            long id = ((Number) s.getAttribute("userId")).longValue();
            String newUsername = body.get("username") == null ? null : body.get("username").toString();
            String newPassword = body.get("password") == null ? null : body.get("password").toString();
            String avatar = body.get("avatar") == null ? null : body.get("avatar").toString();
            String email = body.get("email") == null ? null : body.get("email").toString();
            String phone = body.get("phone") == null ? null : body.get("phone").toString();
            String currentPassword = body.get("currentPassword") == null ? null : body.get("currentPassword").toString();

            if (newUsername == null || newUsername.trim().isEmpty()) {
                newUsername = (String) s.getAttribute("username");
            }

            User existingUser = UserDao.findById(id);
            if (existingUser == null) {
                sendJson(resp, mapOf("success", false, "message", "用户不存在"));
                return;
            }

            if (newPassword != null && newPassword.trim().isEmpty()) newPassword = null;
            if (newPassword == null) {
                newPassword = existingUser.getPassword();
            } else if (currentPassword != null && !currentPassword.isEmpty()) {
                if (existingUser.getPassword() != null && !existingUser.getPassword().equals(currentPassword)) {
                    sendJson(resp, mapOf("success", false, "message", "当前密码不正确"));
                    return;
                }
            }

            if (email == null || email.trim().isEmpty()) email = existingUser.getEmail();
            if (phone == null || phone.trim().isEmpty()) phone = existingUser.getPhone();
            // if avatar not provided, keep existing avatar
            if (avatar == null || avatar.trim().isEmpty()) {
                avatar = existingUser.getAvatar();
            }
            String ctxPath = req.getContextPath() == null ? "" : req.getContextPath();
            String savedAvatarPath = null;
            String previousAvatar = existingUser.getAvatar();

            if (avatar != null && avatar.startsWith("data:")) {
                try {
                    int comma = avatar.indexOf(',');
                    if (comma > 0) {
                        String meta = avatar.substring(5, comma);
                        String data = avatar.substring(comma + 1);
                        String mime = meta.split(";")[0];
                        String ext = "png";
                        if (mime.contains("jpeg") || mime.contains("jpg")) ext = "jpg";
                        else if (mime.contains("gif")) ext = "gif";
                        else if (mime.contains("bmp")) ext = "bmp";
                        byte[] bytes = Base64.getDecoder().decode(data);
                        String avatarsDir = getServletContext().getRealPath("/avatars");
                        if (avatarsDir == null) avatarsDir = System.getProperty("java.io.tmpdir") + File.separator + "avatars";
                        File dir = new File(avatarsDir);
                        if (!dir.exists()) dir.mkdirs();
                        String filename = "avatar_" + id + "_" + System.currentTimeMillis() + "." + ext;
                        File outFile = new File(dir, filename);
                        try (FileOutputStream fos = new FileOutputStream(outFile)) { fos.write(bytes); }
                        savedAvatarPath = ctxPath + "/avatars/" + filename;
                        avatar = savedAvatarPath;
                        try {
                            if (previousAvatar != null && previousAvatar.startsWith(ctxPath + "/avatars/") && !previousAvatar.equals(savedAvatarPath)) {
                                String prevName = previousAvatar.substring((ctxPath + "/avatars/").length());
                                File prevFile = new File(dir, prevName);
                                if (prevFile.exists()) prevFile.delete();
                            }
                        } catch (Throwable ignore) {}
                    }
                } catch (Exception e) {
                    avatar = previousAvatar;
                }
            }

            try { UserDao.ensureAvatarColumnIsText(); } catch (Exception ignore) {}
            try {
                if (avatar != null && avatar.length() > 2000) {
                    if (savedAvatarPath != null && savedAvatarPath.length() < 2000) avatar = savedAvatarPath;
                    else avatar = null;
                }
            } catch (Throwable ignore) {}

            boolean ok;
            try {
                ok = UserDao.updateUser(id, newUsername, newPassword, avatar, email, phone);
            } catch (java.sql.SQLException sqe) {
                try {
                    ok = UserDao.updateUser(id, newUsername, newPassword, savedAvatarPath, email, phone);
                } catch (Exception ex2) {
                    sendJson(resp, mapOf("success", false, "message", "保存失败: " + sqe.getMessage()));
                    return;
                }
            }

            if (ok) {
                s.setAttribute("username", newUsername);
                s.setAttribute("avatar", avatar);
                sendJson(resp, mapOf("success", true, "avatar", avatar, "email", email, "phone", phone));
                LogUtil.log(getServletContext(), newUsername, "update-user-info", "用户信息已更新");
            } else {
                sendJson(resp, mapOf("success", false, "message", "更新失败"));
            }
        } catch (Exception e) {
            sendJson(resp, mapOf("success", false, "message", e.getMessage()));
        }
    }

    private void sendJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(resp.getWriter(), data);
    }

    private Map<String,Object> mapOf(Object... kv) {
        Map<String,Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i+1]);
        return m;
    }
}
