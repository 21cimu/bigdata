package com.hdfsdrive.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.UserDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

@WebServlet(urlPatterns = {"/api/admin/users","/api/admin/users/*"})
public class AdminUsersServlet extends HttpServlet {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            List<Map<String,Object>> dbUsers = UserDao.listUsers();
            Map<String,Object> out = new HashMap<>();
            out.put("users", dbUsers);
            sendJson(resp, out);
        } catch (SQLException e) {
            // return failure payload
            // Log the error server-side for debugging
            System.err.println("AdminUsersServlet: failed to list users: " + e.getMessage());
            e.printStackTrace();
            // Return 500 status so the frontend can treat this as an error (instead of silently receiving empty data)
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendJson(resp, mapOf("success", false, "message", "查询用户失败: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // support two behaviors:
        // - POST /api/admin/users -> create new user (body JSON: username, email, password, role)
        // - POST /api/admin/users/reset?username=... -> reset password for username (body optional newPassword)
        String pathInfo = req.getPathInfo();
        if (pathInfo != null && pathInfo.startsWith("/reset")) {
            // reset password
            String username = req.getParameter("username");
            if (username == null || username.trim().isEmpty()) {
                sendJson(resp, mapOf("success", false, "message", "username required"));
                return;
            }
            try {
                Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
                String newPwd = body != null && body.get("password") != null ? String.valueOf(body.get("password")) : "123456";
                boolean ok = UserDao.resetPasswordByUsername(username, newPwd);
                sendJson(resp, mapOf("success", ok));
            } catch (SQLException e) {
                sendJson(resp, mapOf("success", false, "message", "重置密码失败: " + e.getMessage()));
            } catch (Exception e) {
                sendJson(resp, mapOf("success", false, "message", "参数解析失败: " + e.getMessage()));
            }
            return;
        }

        // create user
        try {
            Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
            String username = String.valueOf(body.getOrDefault("username", ""));
            String password = String.valueOf(body.getOrDefault("password", ""));
            String email = String.valueOf(body.getOrDefault("email", ""));
            String role = String.valueOf(body.getOrDefault("role", "user"));
            if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                sendJson(resp, mapOf("success", false, "message", "用户名和密码为必填"));
                return;
            }
            boolean ok = false;
            try {
                // optionally accept createdAt from body (milliseconds since epoch)
                Long createdAt = null;
                Object ca = body.get("createdAt");
                try {
                    if (ca instanceof Number) createdAt = ((Number) ca).longValue();
                    else if (ca instanceof String) createdAt = Long.parseLong((String) ca);
                } catch (Exception ignore) { createdAt = null; }
                ok = UserDao.createUser(username, password, createdAt);
            } catch (SQLException se) {
                sendJson(resp, mapOf("success", false, "message", "创建用户失败: " + se.getMessage()));
                return;
            }
            if (ok) {
                // return created user info (id and createdAt fetched from DB list)
                List<Map<String,Object>> all = UserDao.listUsers();
                Map<String,Object> created = all.stream().filter(u -> username.equals(u.get("username"))).findFirst().orElse(null);
                sendJson(resp, mapOf("success", true, "user", created));
            } else {
                sendJson(resp, mapOf("success", false, "message", "创建失败"));
            }
        } catch (IOException ioe) {
            sendJson(resp, mapOf("success", false, "message", "读取请求失败: " + ioe.getMessage()));
        } catch (SQLException sqe) {
            sendJson(resp, mapOf("success", false, "message", "查询用户失败: " + sqe.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        if (username == null || username.trim().isEmpty()) {
            sendJson(resp, mapOf("success", false, "message", "username required"));
            return;
        }
        try {
            boolean removed = UserDao.deleteUserByUsername(username);
            sendJson(resp, mapOf("success", removed));
        } catch (SQLException e) {
            sendJson(resp, mapOf("success", false, "message", "删除用户失败: " + e.getMessage()));
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
