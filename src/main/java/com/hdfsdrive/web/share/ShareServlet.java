package com.hdfsdrive.web.share;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.LogUtil;
import com.hdfsdrive.core.ShareService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@WebServlet("/api/share/*")
public class ShareServlet extends HttpServlet {
    private ShareService shareService;
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void init() throws ServletException {
        try {
            String storePath = getServletContext().getRealPath("/WEB-INF/share.json");
            shareService = new ShareService(storePath);
        } catch (Exception e) {
            throw new ServletException("Failed to init ShareService", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getParameter("action");
        if ("list".equals(action)) {
            handleList(req, resp);
        } else {
            sendError(resp, "Invalid action");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String action = req.getParameter("action");
        if ("create".equals(action)) {
            handleCreate(req, resp);
        } else if ("remove".equals(action)) {
            handleRemove(req, resp);
        } else {
            sendError(resp, "Invalid action");
        }
    }

    private void handleList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            List<ShareService.Entry> entries = shareService.list();
            Map<String,Object> out = new HashMap<>();
            out.put("success", true);
            out.put("items", entries);
            sendJson(resp, out);
        } catch (Exception e) {
            sendError(resp, "Failed to list shares: " + e.getMessage());
        }
    }

    private void handleCreate(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getParameter("path");
        String name = req.getParameter("name");
        String daysStr = req.getParameter("days");
        if (path == null || path.isEmpty()) {
            sendError(resp, "path is required");
            return;
        }
        long expireAt = 0;
        try {
            int days = Integer.parseInt(daysStr);
            if (days > 0) expireAt = System.currentTimeMillis() + (long)days * 24L * 3600L * 1000L;
        } catch (Exception ignore) {}
        try {
            ShareService.Entry e = shareService.add(path, name == null ? "" : name, expireAt);
            Map<String,Object> out = new HashMap<>();
            out.put("success", true);
            out.put("item", e);
            // also provide a public link path
            String ctx = req.getContextPath();
            String link = ctx + "/api/share/public?id=" + e.id;
            // include absolute URL fallback
            String origin = req.getScheme() + "://" + req.getServerName() + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort());
            out.put("link", origin + link);
            sendJson(resp, out);
            // admin log
            LogUtil.log(getServletContext(), getSessionUsername(req), "share-create", path, "id=" + e.id + (name != null ? ", name=" + name : ""));
        } catch (Exception e) {
            sendError(resp, "Create share failed: " + e.getMessage());
        }
    }

    private void handleRemove(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = req.getParameter("id");
        if (id == null || id.isEmpty()) { sendError(resp, "id required"); return; }
        try {
            boolean ok = shareService.remove(id);
            Map<String,Object> out = new HashMap<>();
            out.put("success", ok);
            sendJson(resp, out);
            LogUtil.log(getServletContext(), getSessionUsername(req), "share-remove", id, ok ? "成功" : "失败");
        } catch (Exception e) {
            sendError(resp, "Remove failed: " + e.getMessage());
        }
    }

    private String getSessionUsername(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) {
            Object user = session.getAttribute("username");
            if (user != null) {
                return user.toString();
            }
        }
        return null;
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
}
