package com.hdfsdrive.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.HdfsService;
import com.hdfsdrive.core.TrashService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Servlet for directory operations: list, create, delete directories in HDFS
 */
@WebServlet("/api/directory/*")
public class DirectoryServlet extends HttpServlet {
    // Do not keep a long-lived HdfsService: create per-request using session username
    private static final String DEFAULT_HDFS_URI = "hdfs://node1:8020";
    private static final String DEFAULT_ADMIN_USER = "root";
    // per-user root in HDFS where each user's files reside
    private static final String USER_ROOT = "/users";

    private TrashService trashService;
    private ObjectMapper objectMapper = new ObjectMapper();
    // scheduler to purge expired trash entries periodically
    private ScheduledExecutorService purgeScheduler;
    // mapping from type key to list of extensions (lowercase, without dot)
    private Map<String, List<String>> typeExts = new HashMap<>();

    @Override
    public void init() throws ServletException {
        try {
            String storePath = getServletContext().getRealPath("/WEB-INF/trash.json");
            trashService = new TrashService(storePath);
            // initialize default type extension lists
            initDefaultTypeExts();

            // start scheduled purge using admin HDFS connection: run periodic purge as admin/root
            purgeScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "trash-purge-thread");
                t.setDaemon(true);
                return t;
            });
            // schedule initial run after 1 minute and then every 5 minutes (more responsive purge)
            purgeScheduler.scheduleAtFixedRate(() -> {
                HdfsService h = null;
                try {
                    h = createAdminHdfsService();
                    java.util.List<String> purged = trashService.purgeExpired(h);
                    if (purged != null && !purged.isEmpty()) {
                        System.out.println("Purged expired trash entries: " + purged);
                    }
                } catch (Exception e) {
                    System.err.println("Error while purging expired trash: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (h != null) try { h.close(); } catch (IOException ignore) {}
                }
            }, 1, 5, TimeUnit.MINUTES);

            // optionally try to read `格式.txt` from webapp root to override/extend these lists
            try {
                String formatsPath = getServletContext().getRealPath("/格式.txt");
                if (formatsPath != null) {
                    java.io.File f = new java.io.File(formatsPath);
                    if (f.exists()) {
                        parseFormatsFile(f);
                    }
                }
            } catch (Exception e) {
                // ignore format file parse errors and keep defaults
                System.err.println("Failed to load formats file: " + e.getMessage());
            }
        } catch (Exception e) {
            throw new ServletException("Failed to initialize DirectoryServlet", e);
        }
    }

    @Override
    public void destroy() {
        if (purgeScheduler != null) {
            try {
                purgeScheduler.shutdownNow();
            } catch (Exception e) { /* ignore */ }
        }
        super.destroy();
    }

    private HdfsService createHdfsService(HttpServletRequest req) throws Exception {
        HttpSession s = req.getSession(false);
        String user = DEFAULT_ADMIN_USER;
        if (s != null && s.getAttribute("username") != null) {
            user = String.valueOf(s.getAttribute("username"));
        }
        return new HdfsService(DEFAULT_HDFS_URI, user, new Configuration());
    }

    private HdfsService createAdminHdfsService() throws Exception {
        return new HdfsService(DEFAULT_HDFS_URI, DEFAULT_ADMIN_USER, new Configuration());
    }

    // --- helpers for per-user path mapping and authorization ---
    private String getSessionUsername(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null) return null;
        Object o = s.getAttribute("username");
        return o == null ? null : String.valueOf(o);
    }

    private boolean isAdmin(HttpServletRequest req) {
        String u = getSessionUsername(req);
        return u != null && DEFAULT_ADMIN_USER.equals(u);
    }

    private String actualRootForUser(String username) {
        return USER_ROOT + "/" + username;
    }

    /**
     * Resolve a virtual path (as provided by the frontend) to an actual HDFS path.
     * For non-admin users, virtual paths are relative to /users/<username>.
     * For admin users the path is used as-is.
     * Throws SecurityException if a non-admin tries to access another user's path.
     */
    private String resolveToActualPath(HttpServletRequest req, String virtualPath) throws SecurityException {
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
    private String toVirtualPath(HttpServletRequest req, String actualPath) {
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

    private void initDefaultTypeExts() {
        // images
        typeExts.put("images", asList("bmp","jpg","jpeg","png","tif","tiff","gif","pcx","tga","exif","fpx","svg","psd","pcd","dxf","ufo","eps","ai","raw","wmf","webp","avif","apng"));
        // videos
        typeExts.put("videos", asList("mp4","mov","wmv","flv","avi","avchd","webm","mkv"));
        // archives
        typeExts.put("archives", asList("7z","rar","zip","tgz","tar","gz"));
        // documents
        typeExts.put("documents", asList("pdf","doc","docx","xls","xlsx","log","txt","ppt","pptx"));
    }

    private List<String> asList(String... vals) {
        List<String> out = new ArrayList<>();
        for (String v : vals) out.add(v);
        return out;
    }

    private void parseFormatsFile(java.io.File f) throws IOException {
        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // Try to split like "图片格式： bmp，jpg，png"
            String[] parts = line.split("[:：]", 2);
            if (parts.length < 2) continue;
            String keyPart = parts[0].trim();
            String valsPart = parts[1].trim();
            String key = null;
            if (keyPart.contains("图片")) key = "images";
            else if (keyPart.contains("视频")) key = "videos";
            else if (keyPart.contains("压缩")) key = "archives";
            else if (keyPart.contains("文档")) key = "documents";
            if (key == null) continue;
            // split vals by non-alphanumeric characters
            String[] toks = valsPart.split("[^A-Za-z0-9]+");
            List<String> exts = new ArrayList<>();
            for (String t : toks) {
                if (t == null) continue;
                t = t.trim();
                if (t.isEmpty()) continue;
                exts.add(t.toLowerCase());
            }
            if (!exts.isEmpty()) {
                typeExts.put(key, exts);
            }
        }
        br.close();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        
        if ("list".equals(action)) {
            handleList(req, resp);
        } else if ("search".equals(action)) {
            handleSearch(req, resp);
        } else if ("purge".equals(action)) {
            handlePurge(req, resp);
        } else {
            sendError(resp, "Invalid action");
        }
    }

    private void handlePurge(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            List<String> purged = trashService.purgeExpired(createAdminHdfsService());
            Map<String,Object> response = new HashMap<>();
            response.put("success", true);
            response.put("purged", purged);
            sendJson(resp, response);
        } catch (Exception e) {
            sendError(resp, "Purge failed: " + e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        
        if ("create".equals(action)) {
            handleCreate(req, resp);
        } else if ("restore".equals(action)) {
            handleRestore(req, resp);
        } else if ("restoreBatch".equals(action)) {
            handleRestoreBatch(req, resp);
        } else {
            sendError(resp, "Invalid action");
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleDelete(req, resp);
    }

    private void handleList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        if (path == null || path.isEmpty()) {
            path = "/";
        }

        HdfsService hdfs = null;
        HdfsService adminHdfs = null;
        String actualPath = null;
        try {
            // resolve virtual path to actual HDFS path based on user permissions first (no HdfsService needed)
            try {
                actualPath = resolveToActualPath(req, path);
            } catch (SecurityException se) {
                sendError(resp, "Access denied");
                return;
            }

             // If path indicates a virtual type listing like '/.type/images', perform extension-based search
             if (path != null && path.startsWith("/.type/")) {
                String[] parts = path.split("/+ ".trim());
                String typeKey = parts.length >= 3 ? parts[2] : null;
                if (typeKey != null && typeExts.containsKey(typeKey)) {
                    List<String> exts = typeExts.get(typeKey);
                    // search from admin root for admins, otherwise from user's actual root
                    String startDir = isAdmin(req) ? "/" : actualRootForUser(getSessionUsername(req));
                    try {
                        hdfs = createHdfsService(req);
                    } catch (Exception ce) {
                        // failed to create per-user HDFS client, attempt admin client if allowed
                        try { hdfs = createAdminHdfsService(); } catch (Exception ex) { sendError(resp, "Failed to connect to HDFS: " + ex.getMessage()); return; }
                    }
                    List<HdfsService.FileEntry> results = hdfs.searchByExtensions(startDir, exts);
                     List<Map<String, Object>> items = new ArrayList<>();
                     for (HdfsService.FileEntry fe : results) {
                         String name = fe.path.substring(fe.path.lastIndexOf('/') + 1);
                         Map<String,Object> item = new HashMap<>();
                        item.put("name", name);
                        item.put("path", toVirtualPath(req, fe.path));
                         item.put("isDirectory", fe.isDirectory);
                         item.put("type", fe.isDirectory ? "directory" : "file");
                         item.put("size", fe.size);
                         item.put("modificationTime", fe.modificationTime);
                         items.add(item);
                     }
                     Map<String, Object> response = new HashMap<>();
                     response.put("success", true);
                    response.put("path", path);
                     response.put("items", items);
                     sendJson(resp, response);
                     return;
                 } else {
                     sendError(resp, "Unknown type: " + typeKey);
                     return;
                 }
             }

             // If listing the UI trash view, return entries from TrashService instead of HDFS
             if (path != null && path.startsWith("/.trash")) {
                 // purge expired entries first so they won't be returned (use admin HDFS client for purge)
                 try {
                     try { adminHdfs = createAdminHdfsService(); } catch (Exception exx) { adminHdfs = null; }
                     if (adminHdfs != null) {
                         try { trashService.purgeExpired(adminHdfs); } catch (Exception ignore) {}
                     }
                 } catch (Throwable ignore) {}
                 List<TrashService.Entry> trashEntries = trashService.list();
                 List<Map<String, Object>> items = new ArrayList<>();
                 // default retention used when entry.expireAt==0 (ms)
                 long defaultRetention = 30L * 24L * 60L * 60L * 1000L;
                for (TrashService.Entry e : trashEntries) {
                    // non-admin users should only see their own trashed entries
                    if (!isAdmin(req)) {
                        String user = getSessionUsername(req);
                        String actualRoot = actualRootForUser(user);
                        if (e.path == null || !(e.path.equals(actualRoot) || e.path.startsWith(actualRoot + "/"))) continue;
                    }
                     Map<String, Object> item = new HashMap<>();
                     item.put("name", e.name);
                    item.put("path", toVirtualPath(req, e.path));
                     item.put("isDirectory", e.isDirectory);
                     item.put("type", e.isDirectory ? "directory" : "file");
                     item.put("originalPath", e.path);
                     // include effective expireAt (if not set, treat as deletedAt + defaultRetention)
                     long effectiveExpire = (e.expireAt > 0) ? e.expireAt : (e.deletedAt + defaultRetention);
                     item.put("expireAt", effectiveExpire);
                     // try to include size/mtime if available
                     try {
                         // use adminHdfs if available, else try per-user hdfs
                         HdfsService probe = null;
                         try {
                             if (adminHdfs != null) probe = adminHdfs; else probe = createHdfsService(req);
                             if (!e.isDirectory && probe.exists(e.path)) {
                                 String parent = e.path.substring(0, e.path.lastIndexOf('/'));
                                 List<HdfsService.FileEntry> metaList = probe.listDirWithMeta(parent.isEmpty() ? "/" : parent);
                                 for (HdfsService.FileEntry fe : metaList) {
                                    if (fe.path.equals(e.path)) {
                                         item.put("size", fe.size);
                                         item.put("modificationTime", fe.modificationTime);
                                         break;
                                    }
                                 }
                             }
                         } finally {
                             if (probe != null && probe != adminHdfs) try { probe.close(); } catch (IOException ignore) {}
                         }
                     } catch (Exception ignore) {}
                     items.add(item);
                }

                // sort by expireAt ascending (earliest expiration first). Treat missing as max.
                items.sort(Comparator.comparingLong(m -> {
                    Object o = m.get("expireAt");
                    if (o instanceof Number) return ((Number)o).longValue();
                    return Long.MAX_VALUE;
                }));

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("path", path);
                response.put("items", items);
                sendJson(resp, response);
                return;
            }

            // For normal listings, filter out items that are present in trash metadata
            List<TrashService.Entry> trashEntries = trashService.list();
            Set<String> trashedPaths = new HashSet<>();
            for (TrashService.Entry e : trashEntries) trashedPaths.add(e.path);

            // create per-user HDFS client (delay creation until needed)
            try {
                hdfs = createHdfsService(req);
            } catch (Exception ce) {
                sendError(resp, "Failed to connect to HDFS as user: " + ce.getMessage());
                return;
            }
            // Ensure user's root exists before listing to avoid FileNotFoundException for new users
            List<HdfsService.FileEntry> entries = new ArrayList<>();
            try {
                boolean exists = false;
                if (actualPath != null) {
                    try { exists = hdfs.exists(actualPath); } catch (Exception ex) { exists = false; }
                }
                // If this is a per-user root path and it doesn't exist, attempt to create it (best-effort)
                if (!exists && actualPath != null && actualPath.startsWith(USER_ROOT + "/")) {
                    try { hdfs.mkdirs(actualPath); } catch (Exception ignore) { /* best-effort */ }
                    try { exists = hdfs.exists(actualPath); } catch (Exception ignore) { exists = false; }
                }
                if (exists) {
                    entries = hdfs.listDirWithMeta(actualPath);
                } else {
                    // leave entries empty for new user or non-existent path so UI sees an empty folder
                    entries = new ArrayList<>();
                }
            } catch (Exception e) {
                // bubble up to be handled by outer catch which can return debug info if requested
                throw e;
            }
            List<Map<String, Object>> items = new ArrayList<>();

            for (HdfsService.FileEntry fe : entries) {
                String fullPath = fe.path;

                // skip if this path has been moved to UI trash
                if (trashedPaths.contains(fullPath)) continue;

                Map<String, Object> item = new HashMap<>();
                String name = fullPath.substring(fullPath.lastIndexOf('/') + 1);

                item.put("name", name);
                item.put("path", toVirtualPath(req, fullPath));
                item.put("isDirectory", fe.isDirectory);
                item.put("type", fe.isDirectory ? "directory" : "file");
                item.put("size", fe.size);
                item.put("modificationTime", fe.modificationTime);
                items.add(item);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("path", path);
            response.put("items", items);
            sendJson(resp, response);

        } catch (Exception e) {
            // If caller asked for debug, include stacktrace in response to aid debugging
            String debugParam = req.getParameter("debug");
            if (debugParam != null && !debugParam.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Failed to list directory: " + e.getMessage());
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                error.put("stack", sw.toString());
                sendJson(resp, error);
            } else {
                sendError(resp, "Failed to list directory: " + e.getMessage());
            }
        } finally {
            if (hdfs != null) try { hdfs.close(); } catch (IOException ignore) {}
        }
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        if (path == null || path.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

        try {
            String actualPath;
            try {
                actualPath = resolveToActualPath(req, path);
            } catch (SecurityException se) {
                sendError(resp, "Access denied");
                return;
            }
            boolean created = false;
            try {
                created = createHdfsService(req).mkdirs(actualPath);
            } catch (Exception e) {
                // If creation failed due to permission and caller is not admin, try admin fallback
                if (!isAdmin(req)) {
                    try {
                        HdfsService admin = createAdminHdfsService();
                        try {
                            created = admin.mkdirs(actualPath);
                            String sessionUser = getSessionUsername(req);
                            if (created && sessionUser != null) {
                                try { admin.setOwner(actualPath, sessionUser, null); } catch (Exception ignore) {}
                                try { admin.setPermissionOctal(actualPath, "700"); } catch (Exception ignore) {}
                            }
                        } finally { try { admin.close(); } catch (IOException ignore) {} }
                    } catch (Exception adminEx) {
                        // swallow and report below
                    }
                }
            }

             Map<String, Object> response = new HashMap<>();
             response.put("success", created);
             response.put("message", created ? "Directory created successfully" : "Directory already exists or creation failed");
             response.put("path", path);
             sendJson(resp, response);

             // admin log
             appendAdminLog(req, "mkdir", actualPath, created ? "成功" : "失败");

        } catch (Exception e) {
            sendError(resp, "Failed to create directory: " + e.getMessage());
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        if (path == null || path.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

        try {
            boolean recursive = Boolean.parseBoolean(req.getParameter("recursive"));
            boolean permanent = Boolean.parseBoolean(req.getParameter("permanent"));
            Map<String, Object> response = new HashMap<>();

            String actualPath;
            try {
                actualPath = resolveToActualPath(req, path);
            } catch (SecurityException se) {
                sendError(resp, "Access denied");
                return;
            }

            if (permanent) {
                boolean deleted = createHdfsService(req).delete(actualPath, recursive);
                try { trashService.remove(actualPath); } catch (Exception ignore) {}
                response.put("success", deleted);
                response.put("message", deleted ? "Directory permanently deleted" : "Directory not found");
                sendJson(resp, response);
                appendAdminLog(req, "delete-permanent", actualPath, deleted ? "成功" : "失败");
            } else {
                // UI-only trash
                boolean isDir = false;
                try { isDir = createHdfsService(req).isDirectory(actualPath); } catch (Exception ignore) {}
                long expireAt = 0L;
                String daysParam = req.getParameter("days");
                if (daysParam != null && !daysParam.isEmpty()) {
                    try { long days = Long.parseLong(daysParam); expireAt = System.currentTimeMillis() + days * 24L*60L*60L*1000L; } catch (Exception ignore) {}
                }
                trashService.add(actualPath, isDir, expireAt);
                response.put("success", true);
                response.put("message", "Moved to trash (UI)");
                sendJson(resp, response);
                appendAdminLog(req, "delete-to-trash", actualPath, "已移至回收站");
            }

        } catch (Exception e) {
            sendError(resp, "Failed to delete: " + e.getMessage());
        }
    }

    private void handleRestore(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String trashPath = req.getParameter("path");
        if (trashPath == null || trashPath.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

        try {
            String actualPath;
            try { actualPath = resolveToActualPath(req, trashPath); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }

            if (!isAdmin(req)) {
                String user = getSessionUsername(req);
                String actualRoot = actualRootForUser(user);
                if (!(actualPath.equals(actualRoot) || actualPath.startsWith(actualRoot + "/"))) {
                    sendError(resp, "Access denied");
                    return;
                }
            }
            boolean removed = trashService.remove(actualPath);
            Map<String,Object> response = new HashMap<>();
            response.put("success", removed);
            response.put("message", removed ? "Restored successfully (UI)" : "Item not found in trash metadata");
            sendJson(resp, response);
            appendAdminLog(req, "restore", actualPath, removed ? "成功" : "失败");
        } catch (Exception e) {
            sendError(resp, "Restore failed: " + e.getMessage());
        }
    }

    private void handleRestoreBatch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Expect JSON body: { "paths": ["/a","/b"] }
        try {
            Map<String,Object> body = objectMapper.readValue(req.getInputStream(), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            Object pathsObj = body.get("paths");
            List<String> paths = new ArrayList<>();
            if (pathsObj instanceof List) {
                for (Object p : (List<?>)pathsObj) {
                    if (p != null) paths.add(p.toString());
                }
            }

            Map<String,Boolean> results = new HashMap<>();
            for (String p : paths) {
                try {
                    String actualPath;
                    try {
                        actualPath = resolveToActualPath(req, p);
                    } catch (SecurityException se) {
                        results.put(p, false);
                        continue;
                    }
                    if (!isAdmin(req)) {
                        String user = getSessionUsername(req);
                        String actualRoot = actualRootForUser(user);
                        if (!(actualPath.equals(actualRoot) || actualPath.startsWith(actualRoot + "/"))) {
                            results.put(p, false);
                            continue;
                        }
                    }
                    boolean removed = trashService.remove(actualPath);
                    results.put(p, removed);
                } catch (Exception ex) {
                    results.put(p, false);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("results", results);
            sendJson(resp, response);

        } catch (Exception e) {
            sendError(resp, "Restore batch failed: " + e.getMessage());
        }
    }

    private void handleSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String startDir = req.getParameter("startDir");
        String nameContains = req.getParameter("name");
        if (startDir == null || startDir.isEmpty()) startDir = "/";
        if (nameContains == null || nameContains.isEmpty()) {
            sendError(resp, "Name parameter is required for search");
            return;
        }
        try {
            String actualStart;
            try { actualStart = resolveToActualPath(req, startDir); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }
            HdfsService hdfs = null;
            try {
                hdfs = createHdfsService(req);
                java.util.List<HdfsService.FileEntry> results = hdfs.search(actualStart, nameContains);
                java.util.List<Map<String,Object>> items = new ArrayList<>();
                for (HdfsService.FileEntry fe : results) {
                    Map<String,Object> item = new HashMap<>();
                    String name = fe.path.substring(fe.path.lastIndexOf('/') + 1);
                    item.put("name", name);
                    item.put("path", toVirtualPath(req, fe.path));
                    item.put("isDirectory", fe.isDirectory);
                    item.put("type", fe.isDirectory ? "directory" : "file");
                    item.put("size", fe.size);
                    item.put("modificationTime", fe.modificationTime);
                    items.add(item);
                }
                Map<String,Object> out = new HashMap<>();
                out.put("success", true);
                out.put("results", items);
                out.put("count", items.size());
                sendJson(resp, out);
                return;
            } finally { if (hdfs != null) try { hdfs.close(); } catch (IOException ignore) {} }
        } catch (Exception e) {
            sendError(resp, "Search failed: " + e.getMessage());
        }
    }

    private void sendJson(HttpServletResponse resp, Object data) throws IOException {
        resp.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(resp.getWriter(), data);
    }

    private void sendError(HttpServletResponse resp, String message) throws IOException {
        resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        sendJson(resp, error);
    }

    // Append an admin operation log entry under WEB-INF/logs/admin-operations.log
    private void appendAdminLog(HttpServletRequest req, String action, String path, String extra) {
        try {
            String logsDir = getServletContext().getRealPath("/WEB-INF/logs");
            if (logsDir == null) return;
            java.io.File dir = new java.io.File(logsDir);
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, "admin-operations.log");
            String user = getSessionUsername(req);
            String ts = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
            String line = String.format("%s\tuser=%s\taction=%s\tpath=%s\tinfo=%s\n", ts, user == null ? "(anon)" : user, action, path == null ? "(none)" : path, extra == null ? "" : extra.replace('\n',' '));
            try (java.io.FileWriter fw = new java.io.FileWriter(f, true); java.io.PrintWriter pw = new java.io.PrintWriter(fw)) {
                pw.print(line);
            }
        } catch (Throwable ignore) {}
    }

}
