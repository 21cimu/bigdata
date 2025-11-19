package com.hdfsdrive.web.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.HdfsService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Base servlet providing shared helpers for HDFS access, per-user path mapping,
 * JSON responses and simple admin logging. Feature servlets (file, directory,
 * share, etc.) should extend this class to avoid duplication.
 */
public abstract class AbstractHdfsServlet extends HttpServlet {
    protected static final String DEFAULT_HDFS_URI = "hdfs://node1:8020";
    protected static final String DEFAULT_ADMIN_USER = "root";
    protected static final String USER_ROOT = "/users";

    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected HdfsService createHdfsService(HttpServletRequest req) throws Exception {
        HttpSession s = req.getSession(false);
        String user = DEFAULT_ADMIN_USER;
        if (s != null && s.getAttribute("username") != null) {
            user = String.valueOf(s.getAttribute("username"));
        }
        return new HdfsService(DEFAULT_HDFS_URI, user, new Configuration());
    }

    protected HdfsService createAdminHdfsService() throws Exception {
        return new HdfsService(DEFAULT_HDFS_URI, DEFAULT_ADMIN_USER, new Configuration());
    }

    // --- helpers for per-user path mapping and authorization ---
    protected String getSessionUsername(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return null;
        Object o = s.getAttribute("username");
        return o == null ? null : String.valueOf(o);
    }

    protected boolean isAdmin(HttpServletRequest req) {
        String u = getSessionUsername(req);
        return u != null && DEFAULT_ADMIN_USER.equals(u);
    }

    protected String actualRootForUser(String username) {
        return USER_ROOT + "/" + username;
    }

    /**
     * Resolve a virtual path (as provided by the frontend) to an actual HDFS path.
     * For non-admin users, virtual paths are relative to /users/<username>.
     * For admin users the path is used as-is.
     * Throws SecurityException if a non-admin tries to access another user's path.
     */
    protected String resolveToActualPath(HttpServletRequest req, String virtualPath) throws SecurityException {
        if (virtualPath == null || virtualPath.isEmpty()) virtualPath = "/";
        // special virtual namespaces are not mapped
        if (virtualPath.startsWith("/.type/") || virtualPath.startsWith("/.trash")) return virtualPath;
        if (isAdmin(req)) {
            return virtualPath;
        }
        String user = getSessionUsername(req);
        if (user == null) throw new SecurityException("Not logged in");
        String actualRoot = actualRootForUser(user);
        // if user passed an absolute HDFS path under /users, ensure it's their own
        if (virtualPath.startsWith(USER_ROOT + "/")) {
            if (virtualPath.equals(actualRoot) || virtualPath.startsWith(actualRoot + "/")) {
                return virtualPath;
            }
            throw new SecurityException("Access denied");
        }
        // treat virtual '/' as the user's root
        if (virtualPath.equals("/")) return actualRoot;
        // otherwise prepend user's root
        if (!virtualPath.startsWith("/")) virtualPath = "/" + virtualPath;
        return actualRoot + virtualPath;
    }

    /**
     * Convert an actual HDFS path back to the virtual path visible to the current user.
     * For admin users, return as-is.
     */
    protected String toVirtualPath(HttpServletRequest req, String actualPath) {
        if (actualPath == null) return null;
        if (isAdmin(req)) return actualPath;
        String user = getSessionUsername(req);
        if (user == null) return actualPath;
        String actualRoot = actualRootForUser(user);
        if (actualPath.equals(actualRoot)) return "/";
        if (actualPath.startsWith(actualRoot + "/")) {
            return actualPath.substring(actualRoot.length());
        }
        // not under user's root -> hide real path
        return actualPath;
    }

    // --- JSON helpers and simple error handling ---

    protected void sendJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(resp.getWriter(), data);
    }

    protected void sendError(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> m = new HashMap<>();
        m.put("success", false);
        m.put("message", message);
        sendJson(resp, m);
    }

    /**
     * Append an admin operation log entry under WEB-INF/logs/admin-operations.log.
     */
    protected void appendAdminLog(HttpServletRequest req, String action, String detail, String result) {
        try {
            String logsDir = getServletContext().getRealPath("/WEB-INF/logs");
            if (logsDir == null) return;
            java.io.File dir = new java.io.File(logsDir);
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "admin-operations.log");
            String user = getSessionUsername(req);
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String line = String.format(
                    "%s\tuser=%s\taction=%s\tpath=%s\tinfo=%s\n",
                    ts,
                    user == null ? "(anon)" : user,
                    action,
                    detail == null ? "(none)" : detail,
                    result == null ? "" : result.replace('\n', ' ')
            );
            try (java.io.FileWriter fw = new java.io.FileWriter(f, true);
                 java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                pw.print(line);
            }
        } catch (Throwable ignore) {
        }
    }
}
