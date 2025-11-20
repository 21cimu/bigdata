package com.hdfsdrive.web.file;

import com.hdfsdrive.core.HdfsService;
import com.hdfsdrive.core.LogUtil;
import com.hdfsdrive.core.TrashService;
import com.hdfsdrive.web.common.AbstractHdfsServlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for file operations: upload, download, delete files in HDFS
 */
@WebServlet("/api/file/*")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 2,  // 2MB
    maxFileSize = 1024 * 1024 * 100,       // 100MB
    maxRequestSize = 1024 * 1024 * 100     // 100MB
)
public class FileServlet extends AbstractHdfsServlet {
    private TrashService trashService;

    @Override
    public void init() throws ServletException {
        try {
            String storePath = getServletContext().getRealPath("/WEB-INF/trash.json");
            trashService = new TrashService(storePath);
        } catch (Exception e) {
            throw new ServletException("Failed to initialize FileServlet", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        
        if ("download".equals(action)) {
            handleDownload(req, resp);
        } else {
            sendError(resp, "Invalid action");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String action = req.getParameter("action");
        
        if ("upload".equals(action)) {
            handleUpload(req, resp);
        } else if ("move".equals(action)) {
            handleMove(req, resp);
        } else if ("restore".equals(action)) {
            handleRestore(req, resp);
        } else if ("copy".equals(action)) {
            handleCopy(req, resp);
        } else if ("save".equals(action)) {
            handleSave(req, resp);
        } else {
            sendError(resp, "Invalid action");
        }
    }

    private void handleMove(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String src = req.getParameter("src");
        String dst = req.getParameter("dst");
        if (src == null || dst == null || src.isEmpty() || dst.isEmpty()) {
            sendError(resp, "src and dst parameters are required");
            return;
        }

        String actualSrc;
        String actualDst;
        try { actualSrc = resolveToActualPath(req, src); actualDst = resolveToActualPath(req, dst); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }

        try {
            boolean ok = false;
            HdfsService hdfsService = null;
            try {
                hdfsService = createHdfsService(req);
                ok = hdfsService.move(actualSrc, actualDst);
            } catch (Exception e) {
                // admin fallback
                String sessionUser = getSessionUsername(req);
                if (sessionUser != null && !isAdmin(req)) {
                    try {
                        ensureUserRootOwnedByAdmin(sessionUser);
                        HdfsService admin = createAdminHdfsService();
                        try {
                            ok = admin.move(actualSrc, actualDst);
                            if (ok) {
                                try { admin.setOwner(actualDst, sessionUser, null); } catch (Exception ignore) {}
                            }
                        } finally { try { admin.close(); } catch (IOException ignore) {} }
                    } catch (Exception adminEx) {
                        // ignore
                    }
                }
            } finally { if (hdfsService != null) try { hdfsService.close(); } catch (IOException ignore) {} }

            Map<String, Object> response = new HashMap<>();
            response.put("success", ok);
            response.put("message", ok ? "Moved successfully" : "Move failed");
            sendJson(resp, response);

            // log admin action
            LogUtil.log(getServletContext(), getSessionUsername(req), "move", actualSrc + " -> " + actualDst, ok ? "成功" : "失败");
        } catch (Exception e) {
            sendError(resp, "Move failed: " + e.getMessage());
        }
    }

    private void handleCopy(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String src = req.getParameter("src");
        String dst = req.getParameter("dst");
        if (src == null || dst == null || src.isEmpty() || dst.isEmpty()) {
            sendError(resp, "src and dst parameters are required");
            return;
        }

        String actualSrc;
        String actualDst;
        try { actualSrc = resolveToActualPath(req, src); actualDst = resolveToActualPath(req, dst); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }

        try {
            boolean ok = false;
            HdfsService hdfsService = null;
            try {
                hdfsService = createHdfsService(req);
                ok = hdfsService.copy(actualSrc, actualDst);
            } catch (Exception e) {
                String sessionUser = getSessionUsername(req);
                if (sessionUser != null && !isAdmin(req)) {
                    try {
                        ensureUserRootOwnedByAdmin(sessionUser);
                        HdfsService admin = createAdminHdfsService();
                        try {
                            ok = admin.copy(actualSrc, actualDst);
                            if (ok) {
                                try { admin.setOwner(actualDst, sessionUser, null); } catch (Exception ignore) {}
                                try { admin.setPermissionOctal(actualDst, "600"); } catch (Exception ignore) {}
                            }
                        } finally { try { admin.close(); } catch (IOException ignore) {} }
                    } catch (Exception adminEx) {
                        // ignore
                    }
                }
            } finally { if (hdfsService != null) try { hdfsService.close(); } catch (IOException ignore) {} }

            Map<String, Object> response = new HashMap<>();
            response.put("success", ok);
            response.put("message", ok ? "Copied successfully" : "Copy failed");
            sendJson(resp, response);

            // log admin action
            LogUtil.log(getServletContext(), getSessionUsername(req), "copy", actualSrc + " -> " + actualDst, ok ? "成功" : "失败");
        } catch (Exception e) {
            sendError(resp, "Copy failed: " + e.getMessage());
        }
    }

    private void handleRestore(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String trashPath = req.getParameter("path");
        if (trashPath == null || trashPath.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

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

        try {
            boolean removed = trashService.remove(actualPath);
            Map<String,Object> response = new HashMap<>();
            response.put("success", removed);
            response.put("message", removed ? "Restored successfully (UI)" : "Item not found in trash metadata");
            sendJson(resp, response);

            // log admin action
            LogUtil.log(getServletContext(), getSessionUsername(req), "restore", actualPath, removed ? "成功" : "失败");
        } catch (Exception e) {
            sendError(resp, "Restore failed: " + e.getMessage());
        }
    }

    // Save text content to an HDFS file (overwrite).
    private void handleSave(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Expect JSON body: { "path": "/path/to/file", "content": "..." }
        try {
            Map<String, Object> body = objectMapper.readValue(req.getInputStream(), new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            Object pathObj = body.get("path");
            Object contentObj = body.get("content");
            String remotePath = pathObj == null ? null : pathObj.toString();
            String content = contentObj == null ? "" : contentObj.toString();

            if (remotePath == null || remotePath.isEmpty()) {
                sendError(resp, "Path parameter is required");
                return;
            }

            String actualPath;
            try {
                actualPath = resolveToActualPath(req, remotePath);
            } catch (SecurityException se) {
                sendError(resp, "Access denied");
                return;
            }

            // prevent writing directories
            HdfsService hdfsServiceCheck = null;
            try {
                hdfsServiceCheck = createHdfsService(req);
                if (hdfsServiceCheck.isDirectory(actualPath)) {
                    sendError(resp, "目标路径是目录，无法写入文件");
                    return;
                }
            } catch (Exception ignore) {
            } finally {
                if (hdfsServiceCheck != null) {
                    try { hdfsServiceCheck.close(); } catch (IOException ignore) {}
                }
            }

            // write bytes using UTF-8. Overwrite existing file.
            HdfsService hdfsService = null;
            try {
                hdfsService = createHdfsService(req);
                hdfsService.createFile(actualPath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
                Map<String,Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "保存成功");
                sendJson(resp, response);
                LogUtil.log(getServletContext(), getSessionUsername(req), "save-file", actualPath, "成功");
                return;
            } catch (Exception e) {
                // If save failed due to permission, attempt admin fallback: write as admin then chown to session user
                boolean fallbackDone = false;
                String sessionUser = getSessionUsername(req);
                if (sessionUser != null && !isAdmin(req)) {
                    try {
                        // try to ensure user's root ownership first
                        ensureUserRootOwnedByAdmin(sessionUser);
                        HdfsService admin = createAdminHdfsService();
                        try {
                            admin.createFile(actualPath, content.getBytes(java.nio.charset.StandardCharsets.UTF_8), true);
                            try { admin.setOwner(actualPath, sessionUser, null); } catch (Exception ignore) {}
                            try { admin.setPermissionOctal(actualPath, "600"); } catch (Exception ignore) {}
                            fallbackDone = true;
                        } finally { try { admin.close(); } catch (IOException ignore) {} }
                    } catch (Exception adminEx) {
                        // fallback failed
                    }
                }
                if (fallbackDone) {
                    Map<String,Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "保存成功（通过管理员回退）");
                    sendJson(resp, response);
                    LogUtil.log(getServletContext(), getSessionUsername(req), "save-file", actualPath, "成功 (admin fallback)");
                    return;
                }
                sendError(resp, "保存失败: " + e.getMessage());
                return;
            } finally {
                if (hdfsService != null) {
                    try { hdfsService.close(); } catch (IOException ignore) {}
                }
            }
        } catch (Exception e) {
            sendError(resp, "解析请求失败: " + e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handleDelete(req, resp);
    }

    private void handleUpload(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String remotePath = req.getParameter("path");
        if (remotePath == null || remotePath.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

        Part filePart = req.getPart("file");
        if (filePart == null) {
            sendError(resp, "File is required");
            return;
        }

        try {
            // Save to temporary file first
            Path tempFile = Files.createTempFile("hdfs-upload-", ".tmp");
            try (InputStream inputStream = filePart.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            // resolve path
            String actualPath;
            try { actualPath = resolveToActualPath(req, remotePath); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }

            // Safety: if resolve returned '/' for a non-admin user for any reason, map it to user's root
            try {
                if (!isAdmin(req)) {
                    String sessionUser = getSessionUsername(req);
                    if (sessionUser != null && "/".equals(actualPath)) {
                        actualPath = actualRootForUser(sessionUser);
                    }
                }
            } catch (Throwable ignore) {}

             // Upload to HDFS
             String fileName = getFileName(filePart);
             String targetPath = actualPath.endsWith("/") ? actualPath + fileName : actualPath + "/" + fileName;
             HdfsService hdfsService = null;
             try {
                 hdfsService = createHdfsService(req);
                // Ensure parent exists before uploading to avoid writing to '/' by mistake
                try {
                    String parent = targetPath.substring(0, targetPath.lastIndexOf('/'));
                    if (parent == null || parent.isEmpty()) parent = "/";
                    if (!hdfsService.exists(parent)) {
                        // attempt to create user root/parent directories (best-effort)
                        if (parent.startsWith(USER_ROOT + "/")) {
                            try { hdfsService.mkdirs(parent); } catch (Exception ignore) { /* best-effort */ }
                        }
                    }
                } catch (Exception ignore) {}
                try {
                    hdfsService.upload(tempFile.toString(), targetPath);
                } catch (Exception uploadEx) {
                    // If upload failed due to permission and current user is non-admin, try admin fallback:
                    boolean fallbackDone = false;
                    String sessionUser = getSessionUsername(req);
                    if (sessionUser != null && !isAdmin(req)) {
                        try {
                            HdfsService admin = createAdminHdfsService();
                            try {
                                // ensure parent exists
                                String parent = targetPath.substring(0, targetPath.lastIndexOf('/'));
                                if (parent == null || parent.isEmpty()) parent = "/";
                                if (!admin.exists(parent)) {
                                    try { admin.mkdirs(parent); } catch (Exception ignore) {}
                                }
                                // upload as admin then chown to user
                                admin.upload(tempFile.toString(), targetPath);
                                try { admin.setOwner(targetPath, sessionUser, null); } catch (Exception ignore) {}
                                try { admin.setPermissionOctal(targetPath, "600"); } catch (Exception ignore) {}
                                fallbackDone = true;
                            } finally { try { admin.close(); } catch (IOException ignore) {} }
                        } catch (Exception adminEx) {
                            // record but continue to throw original uploadEx
                        }
                    }
                    if (!fallbackDone) throw uploadEx; // rethrow original if fallback didn't work
                }
             } finally {
                 if (hdfsService != null) try { hdfsService.close(); } catch (IOException ignore) {}
             }

            // Clean up temp file
            Files.delete(tempFile);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "File uploaded successfully");
            response.put("path", toVirtualPath(req, targetPath));
            sendJson(resp, response);

            // log admin action
            LogUtil.log(getServletContext(), getSessionUsername(req), "upload", targetPath, "成功");
        } catch (Exception e) {
            sendError(resp, "Upload failed: " + e.getMessage());
        }
    }

    private void handleDownload(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String remotePath = req.getParameter("path");
        if (remotePath == null || remotePath.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

        String actualPath;
        try { actualPath = resolveToActualPath(req, remotePath); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }

        try {
            // Create temporary file for download
            Path tempFile = Files.createTempFile("hdfs-download-", ".tmp");
            HdfsService hdfsService = null;
            try {
                hdfsService = createHdfsService(req);
                hdfsService.download(actualPath, tempFile.toString());
            } finally { if (hdfsService != null) try { hdfsService.close(); } catch (IOException ignore) {} }

            // Set response headers
            String fileName = actualPath.substring(actualPath.lastIndexOf('/') + 1);
            resp.setContentType("application/octet-stream");
            resp.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            resp.setContentLengthLong(Files.size(tempFile));

            // Stream file to response
            try (InputStream in = Files.newInputStream(tempFile);
                 OutputStream out = resp.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            // Clean up temp file
            Files.delete(tempFile);

        } catch (Exception e) {
            sendError(resp, "Download failed: " + e.getMessage());
        }
    }

    private void handleDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String remotePath = req.getParameter("path");
        if (remotePath == null || remotePath.isEmpty()) {
            sendError(resp, "Path parameter is required");
            return;
        }

        String actualPath;
        try { actualPath = resolveToActualPath(req, remotePath); } catch (SecurityException se) { sendError(resp, "Access denied"); return; }

        try {
            boolean recursive = Boolean.parseBoolean(req.getParameter("recursive"));
            boolean permanent = Boolean.parseBoolean(req.getParameter("permanent"));

            Map<String, Object> response = new HashMap<>();

            if (permanent) {
                HdfsService hdfsService = null;
                try {
                    boolean deleted = false;
                    try {
                        hdfsService = createHdfsService(req);
                        deleted = hdfsService.delete(actualPath, recursive);
                    } catch (Exception e) {
                        // try admin fallback if permission denied
                        String sessionUser = getSessionUsername(req);
                        if (sessionUser != null && !isAdmin(req)) {
                            try {
                                HdfsService admin = createAdminHdfsService();
                                try {
                                    deleted = admin.delete(actualPath, recursive);
                                } finally { try { admin.close(); } catch (IOException ignore) {} }
                            } catch (Exception adminEx) {
                                // ignore, will report error below
                            }
                        } else {
                            throw e;
                        }
                    } finally { if (hdfsService != null) try { hdfsService.close(); } catch (IOException ignore) {} }

                    // also remove from trash metadata if present
                    try { trashService.remove(actualPath); } catch (Exception ignore) {}
                    response.put("success", deleted);
                    response.put("message", deleted ? "File permanently deleted" : "File not found");
                } finally { /* nothing */ }
            } else {
                // UI-only trash: add entry to trash metadata, do NOT move files in HDFS
                try {
                    // determine if path is directory by asking HDFS
                    boolean isDir = false;
                    HdfsService hdfsService = null;
                    try { hdfsService = createHdfsService(req); isDir = hdfsService.listDir(actualPath) != null; } catch (Exception e) {}
                    final boolean isDirectory = isDir;

                    // add to trash metadata
                    String trashPath = null;
                    try {
                        // TrashService.add returns void; record actualPath as trashPath for UI
                        trashService.add(actualPath, isDirectory);
                        trashPath = actualPath;
                    } catch (Exception e) {
                        sendError(resp, "移动到回收站失败: " + e.getMessage());
                        return;
                    }

                    response.put("success", true);
                    response.put("message", "已移至回收站");
                    response.put("trashPath", toVirtualPath(req, trashPath));
                } catch (Exception e) {
                    sendError(resp, "删除失败: " + e.getMessage());
                }
            }

            // Send JSON response
            sendJson(resp, response);
            LogUtil.log(getServletContext(), getSessionUsername(req), "delete-file", actualPath, "成功");

        } catch (Exception e) {
            sendError(resp, "删除失败: " + e.getMessage());
        }
    }

    // Ensure the user's HDFS root (/users/<username>) is owned by that user — run as admin.
    private void ensureUserRootOwnedByAdmin(String sessionUser) {
        if (sessionUser == null || sessionUser.isEmpty()) return;
        String userRoot = actualRootForUser(sessionUser);
        try {
            HdfsService admin = createAdminHdfsService();
            try {
                try { admin.setOwner(userRoot, sessionUser, null); } catch (Exception ignore) {}
                try { admin.setPermissionOctal(userRoot, "700"); } catch (Exception ignore) {}
            } finally { try { admin.close(); } catch (IOException ignore) {} }
        } catch (Exception e) {
            // ignore — best-effort
        }
    }

    private String getFileName(Part part) {
        String contentDisposition = part.getHeader("content-disposition");
        if (contentDisposition == null) return "unknown";
        String[] tokens = contentDisposition.split(";" );
        for (String token : tokens) {
            if (token.trim().startsWith("filename")) {
                return token.substring(token.indexOf('=') + 1).trim().replace("\"", "");
            }
        }
        return "unknown";
    }

}
