package com.hdfsdrive.web;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJson(resp, mapOf("success", false, "message", "未登录"));
            return;
        }
        try {
            long id = ((Number)s.getAttribute("userId")).longValue();
            User u = UserDao.findById(id);
            if (u == null) {
                sendJson(resp, mapOf("success", false, "message", "用户不存在"));
                return;
            }
            Map<String,Object> out = new HashMap<>();
            out.put("success", true);
            out.put("user", mapOf("id", u.getId(), "username", u.getUsername(), "avatar", u.getAvatar()));
            sendJson(resp, out);
        } catch (Exception e) {
            sendJson(resp, mapOf("success", false, "message", e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // update user fields: expect JSON body { username, password, avatar }
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            sendJson(resp, mapOf("success", false, "message", "未登录"));
            return;
        }
        try {
            Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
            long id = ((Number)s.getAttribute("userId")).longValue();
            String newUsername = body.get("username") == null ? null : body.get("username").toString();
            String newPassword = body.get("password") == null ? null : body.get("password").toString();
            String avatar = body.get("avatar") == null ? null : body.get("avatar").toString();
            if (newUsername == null || newUsername.trim().isEmpty()) newUsername = (String)s.getAttribute("username");
            // Treat empty password string as "no change" so frontend can submit "" to mean keep existing password
            if (newPassword != null) {
                if (newPassword.trim().isEmpty()) {
                    newPassword = null;
                }
            }
            if (newPassword == null) {
                // keep existing password
                User u = UserDao.findById(id);
                if (u != null) newPassword = u.getPassword();
            }

            // If avatar is a data URL (base64), decode and write to /avatars directory and set avatar to relative path
            String ctxPath = req.getContextPath() == null ? "" : req.getContextPath();
            String savedAvatarPath = null;
            String previousAvatar = null;
            try {
                User u = UserDao.findById(id);
                if (u != null) previousAvatar = u.getAvatar();
            } catch (Exception ignore) {}

            if (avatar != null && avatar.startsWith("data:")) {
                try {
                    int comma = avatar.indexOf(',');
                    if (comma > 0) {
                        String meta = avatar.substring(5, comma); // e.g. image/png;base64
                        String data = avatar.substring(comma + 1);
                        String mime = meta.split(";")[0]; // e.g. image/png
                        String ext = "png";
                        if (mime.contains("jpeg") || mime.contains("jpg")) ext = "jpg";
                        else if (mime.contains("gif")) ext = "gif";
                        else if (mime.contains("png")) ext = "png";
                        else if (mime.contains("bmp")) ext = "bmp";
                        // decode base64
                        byte[] bytes = Base64.getDecoder().decode(data);
                        // write to webapp avatars folder so it can be served as static resource
                        String avatarsDir = getServletContext().getRealPath("/avatars");
                        if (avatarsDir == null) avatarsDir = System.getProperty("java.io.tmpdir") + File.separator + "avatars";
                        File dir = new File(avatarsDir);
                        if (!dir.exists()) dir.mkdirs();
                        String filename = "avatar_" + id + "_" + System.currentTimeMillis() + "." + ext;
                        File outFile = new File(dir, filename);
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            fos.write(bytes);
                        }
                        savedAvatarPath = ctxPath + "/avatars/" + filename;
                        avatar = savedAvatarPath;
                        // try to delete previous avatar file if it was in /avatars and different
                        try {
                            if (previousAvatar != null && previousAvatar.startsWith(ctxPath + "/avatars/") && !previousAvatar.equals(savedAvatarPath)) {
                                String prevName = previousAvatar.substring((ctxPath + "/avatars/").length());
                                File prevFile = new File(dir, prevName);
                                if (prevFile.exists()) prevFile.delete();
                            }
                        } catch (Throwable ignore) {}
                    }
                } catch (Exception e) {
                    // on failure, fallback to keeping previous avatar value
                    avatar = previousAvatar;
                }
            }

            // ensure DB column can store avatar path/long values before attempting update
            try {
                com.hdfsdrive.core.UserDao.ensureAvatarColumnIsText();
            } catch (Exception ignore) {
                // best-effort: if ensure fails, we'll still attempt to update and will return error to client
            }

            // sanitize avatar: ensure not excessively long. If savedAvatarPath exists prefer it.
            try {
                if (avatar != null && avatar.length() > 2000) {
                    if (savedAvatarPath != null && savedAvatarPath.length() < 2000) {
                        avatar = savedAvatarPath;
                    } else {
                        // too long and no saved short path -> drop avatar to avoid DB truncation
                        avatar = null;
                    }
                }
            } catch (Throwable ignore) {}

            // attempt update with fallback on SQL failure
            boolean ok = false;
            try {
                ok = UserDao.updateUser(id, newUsername, newPassword, avatar);
            } catch (java.sql.SQLException sqe) {
                // on data truncation or similar, try to fallback to savedAvatarPath or null
                try {
                    if (savedAvatarPath != null) {
                        ok = UserDao.updateUser(id, newUsername, newPassword, savedAvatarPath);
                    } else {
                        ok = UserDao.updateUser(id, newUsername, newPassword, null);
                    }
                } catch (Exception ex2) {
                    // return original SQL error message
                    sendJson(resp, mapOf("success", false, "message", "保存失败: " + sqe.getMessage()));
                    return;
                }
            }

             if (ok) {
                 // update session
                 s.setAttribute("username", newUsername);
                 s.setAttribute("avatar", avatar);
                 // return avatar so client can update header with server-stored path (if changed)
                 sendJson(resp, mapOf("success", true, "avatar", avatar));
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
