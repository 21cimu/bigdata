package com.hdfsdrive.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@WebServlet(urlPatterns = {"/api/admin/announcements", "/api/admin/announcements/*"})
public class AdminAnnouncementsServlet extends HttpServlet {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final List<Map<String,Object>> items = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong idGen = new AtomicLong(1000);

    static {
        Map<String,Object> m = new HashMap<>();
        m.put("id", 1);
        m.put("title", "欢迎使用管理员控制台（后端示例）");
        m.put("author", "系统");
        m.put("content", "这是后端示例的公告，前端请求成功。");
        m.put("createdAt", System.currentTimeMillis());
        items.add(m);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String,Object> out = new HashMap<>();
        out.put("items", items);
        sendJson(resp, out);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // create new announcement from JSON body
        try {
            Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
            Map<String,Object> m = new HashMap<>();
            long id = idGen.incrementAndGet();
            m.put("id", id);
            m.put("title", body.getOrDefault("title", "(无标题)"));
            m.put("author", body.getOrDefault("author", "管理员"));
            m.put("content", body.getOrDefault("content", ""));
            m.put("createdAt", System.currentTimeMillis());
            items.add(0, m);
            sendJson(resp, mapOf("success", true, "item", m));
        } catch (Exception e) {
            sendJson(resp, mapOf("success", false, "message", e.getMessage()));
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // path: /api/admin/announcements/{id}
        String path = req.getPathInfo();
        if (path == null || path.length() <= 1) { sendJson(resp, mapOf("success", false, "message", "缺少 id")); return; }
        String idStr = path.substring(1).split("/")[0];
        try {
            long id = Long.parseLong(idStr);
            Map<String,Object> body = mapper.readValue(req.getInputStream(), Map.class);
            synchronized (items) {
                for (int i=0;i<items.size();i++){
                    Map<String,Object> it = items.get(i);
                    Object oid = it.get("id");
                    if (oid != null && String.valueOf(oid).equals(String.valueOf(id))) {
                        it.put("title", body.getOrDefault("title", it.get("title")));
                        it.put("content", body.getOrDefault("content", it.get("content")));
                        sendJson(resp, mapOf("success", true, "item", it));
                        return;
                    }
                }
            }
            sendJson(resp, mapOf("success", false, "message", "未找到公告"));
        } catch (Exception e) {
            sendJson(resp, mapOf("success", false, "message", e.getMessage()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo();
        if (path == null || path.length() <= 1) { sendJson(resp, mapOf("success", false, "message", "缺少 id")); return; }
        String idStr = path.substring(1).split("/")[0];
        try {
            long id = Long.parseLong(idStr);
            synchronized (items) {
                boolean removed = items.removeIf(m -> String.valueOf(m.get("id")).equals(String.valueOf(id)));
                sendJson(resp, mapOf("success", removed));
                return;
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

