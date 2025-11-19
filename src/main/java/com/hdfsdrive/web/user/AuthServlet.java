package com.hdfsdrive.web.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.HdfsService;
import com.hdfsdrive.core.User;
import com.hdfsdrive.core.UserDao;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.hadoop.conf.Configuration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLIntegrityConstraintViolationException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    private ObjectMapper mapper = new ObjectMapper();

    private void appendAuthLog(HttpServletRequest req, Exception e) {
        try {
            String logsDir = getServletContext().getRealPath("/WEB-INF/logs");
            if (logsDir == null) return;
            File dir = new File(logsDir);
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "auth-errors.log");
            try (FileWriter fw = new FileWriter(f, true); PrintWriter pw = new PrintWriter(fw)) {
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                pw.println("----- " + ts + " -----");
                String action = req.getParameter("action");
                pw.println("action: " + (action == null ? "(none)" : action));
                String user = req.getParameter("username");
                pw.println("username: " + (user == null ? "(none)" : user));
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                pw.println(sw.toString());
                pw.println();
            }
        } catch (Throwable ignore) {
            // do not break response if logging fails
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if ("current".equals(action)) {
            HttpSession s = req.getSession(false);
            Map<String,Object> out = new HashMap<>();
            if (s != null && s.getAttribute("userId") != null) {
                out.put("loggedIn", true);
                out.put("userId", s.getAttribute("userId"));
                out.put("username", s.getAttribute("username"));
                // include avatar if available
                Object avatar = s.getAttribute("avatar");
                if (avatar != null) out.put("avatar", avatar);
            } else {
                out.put("loggedIn", false);
            }
            sendJson(resp, out);
            return;
        }
        sendError(resp, "Unsupported action");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        if ("login".equals(action)) {
            handleLogin(req, resp);
            return;
        } else if ("logout".equals(action)) {
            HttpSession s = req.getSession(false);
            if (s != null) s.invalidate();
            Map<String,Object> out = new HashMap<>(); out.put("success", true);
            sendJson(resp, out);
            return;
        } else if ("register".equals(action)) {
            handleRegister(req, resp);
            return;
        }
        sendError(resp, "Unsupported action");
    }

    private void handleLogin(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        if (username == null || password == null) {
            sendError(resp, "username and password required");
            return;
        }
        try {
            User u = UserDao.findByUsername(username);
            if (u == null) {
                sendJson(resp, mapOf("success", false, "message", "用户不存在"));
                return;
            }
            // compare plaintext password per requirement
            if (u.getPassword() == null || !u.getPassword().equals(password)) {
                sendJson(resp, mapOf("success", false, "message", "密码错误"));
                return;
            }
            HttpSession s = req.getSession(true);
            s.setAttribute("userId", u.getId());
            s.setAttribute("username", u.getUsername());
            if (u.getAvatar() != null) s.setAttribute("avatar", u.getAvatar());
            // set session timeout to 2 hours
            s.setMaxInactiveInterval(2 * 60 * 60);

            sendJson(resp, mapOf("success", true, "userId", u.getId(), "username", u.getUsername(), "avatar", u.getAvatar()));
        } catch (Exception e) {
            // always append detailed log for later inspection
            appendAuthLog(req, e);
            // if caller requested debug, include stack trace
            String debug = req.getParameter("debug");
            if (debug != null && !debug.isEmpty()) {
                StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
                Map<String,Object> out = new HashMap<>();
                out.put("success", false);
                out.put("message", "登录失败: " + e.getMessage());
                out.put("stack", sw.toString());
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                sendJson(resp, out);
            } else {
                sendError(resp, "登录失败: " + e.getMessage());
            }
        }
    }

    private void handleRegister(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        if (username == null || password == null) {
            sendJson(resp, mapOf("success", false, "message", "用户名和密码为必填项"));
            return;
        }
        username = username.trim();
        if (username.length() < 3) {
            sendJson(resp, mapOf("success", false, "message", "用户名需至少 3 个字符"));
            return;
        }
        if (password.length() < 4) {
            sendJson(resp, mapOf("success", false, "message", "密码需至少 4 个字符"));
            return;
        }
        try {
            // record createdAt (ms since epoch)
            long createdAt = System.currentTimeMillis();
            boolean ok = UserDao.createUser(username, password, createdAt);
            if (!ok) {
                sendJson(resp, mapOf("success", false, "message", "注册失败"));
                return;
            }
            // auto-login after registration
            User u = UserDao.findByUsername(username);
            if (u != null) {
                HttpSession s = req.getSession(true);
                s.setAttribute("userId", u.getId());
                s.setAttribute("username", u.getUsername());
                if (u.getAvatar() != null) s.setAttribute("avatar", u.getAvatar());
                s.setMaxInactiveInterval(2 * 60 * 60);
            }
            // Create per-user HDFS root directory (/users/<username>) as admin (best-effort).
            // Record failure into auth-errors.log for debugging but do not fail registration.
            try {
                HdfsService h = new HdfsService("hdfs://node1:8020", "root", new Configuration());
                String userRoot = "/users/" + username;
                try {
                    if (!h.exists(userRoot)) h.mkdirs(userRoot);
                    // set owner to the user and restrict permissions to owner only (rwx------)
                    try { h.setOwner(userRoot, username, null); } catch (Exception ignore) {}
                    try { h.setPermissionOctal(userRoot, "700"); } catch (Exception ignore) {}
                } finally {
                    try { h.close(); } catch (IOException ignore) {}
                }
            } catch (Throwable t) {
                try { appendAuthLog(req, new Exception("Failed to create HDFS user root for " + username, t)); } catch (Throwable ignore) {}
            }
             // 按要求：不在 HDFS 上为每个用户创建不同的文件夹，HDFS 身份将在每次操作时使用该用户来访问
             sendJson(resp, mapOf("success", true, "username", username));
         } catch (SQLIntegrityConstraintViolationException dup) {
             sendJson(resp, mapOf("success", false, "message", "用户名已存在"));
         } catch (Exception e) {
             // always append detailed log for later inspection
             appendAuthLog(req, e);
             String debug = req.getParameter("debug");
             if (debug != null && !debug.isEmpty()) {
                 StringWriter sw = new StringWriter(); e.printStackTrace(new PrintWriter(sw));
                 Map<String,Object> out = new HashMap<>();
                 out.put("success", false);
                 out.put("message", "注册失败: " + e.getMessage());
                 out.put("stack", sw.toString());
                 resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                 sendJson(resp, out);
             } else {
                 // try to detect duplicate by message fallback
                 String msg = e.getMessage() == null ? "" : e.getMessage();
                 if (msg.toLowerCase().contains("duplicate") || msg.toLowerCase().contains("unique")) {
                     sendJson(resp, mapOf("success", false, "message", "用户名已存在"));
                 } else {
                     sendError(resp, "注册失败: " + e.getMessage());
                 }
             }
         }
     }

     private void sendJson(HttpServletResponse resp, Object data) throws IOException {
         resp.setContentType("application/json;charset=UTF-8");
         mapper.writeValue(resp.getWriter(), data);
     }

     private void sendError(HttpServletResponse resp, String msg) throws IOException {
         resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
         Map<String,Object> out = new HashMap<>();
         out.put("success", false);
         out.put("message", msg);
         sendJson(resp, out);
     }

     private Map<String,Object> mapOf(Object... kv) {
         Map<String,Object> m = new HashMap<>();
         for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i+1]);
         return m;
     }
 }
