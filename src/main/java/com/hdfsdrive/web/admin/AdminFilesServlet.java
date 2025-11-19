package com.hdfsdrive.web.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hdfsdrive.core.HdfsService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hadoop.conf.Configuration;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@WebServlet(urlPatterns = {"/api/admin/files"})
public class AdminFilesServlet extends HttpServlet {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final List<Map<String,Object>> files = Collections.synchronizedList(new ArrayList<>());
    static {
        files.add(mapOf("path","/user1/docs/readme.txt","owner","user1","size",1024,"mtime",System.currentTimeMillis()-3600*1000));
        files.add(mapOf("path","/user2/photos/img1.jpg","owner","user2","size",204800,"mtime",System.currentTimeMillis()-86400*1000));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // try HDFS first (best-effort). HDFS URI and user are hard-coded for now; in production read from config.
        try {
            HdfsService hs = new HdfsService("hdfs://node1:8020", "root", new Configuration());
            try {
                // list root /users or configurable path; use "/" if not available
                String start = req.getParameter("path");
                if (start == null || start.trim().isEmpty()) start = "/";
                List<HdfsService.FileEntry> entries = hs.listDirWithMeta(start);
                List<Map<String,Object>> out = entries.stream().map(fe -> {
                    Map<String,Object> m = new HashMap<>();
                    m.put("path", fe.path);
                    // derive owner from path (/users/...) if possible
                    String owner = "";
                    try { String[] parts = fe.path.split("/"); if (parts.length > 1 && parts[1] != null) owner = parts[1]; } catch (Exception ignore) { }
                    m.put("owner", owner);
                    m.put("size", fe.size);
                    m.put("isDirectory", fe.isDirectory);
                    m.put("mtime", fe.modificationTime);
                    return m;
                }).collect(Collectors.toList());
                sendJson(resp, mapOf("items", out));
            } finally {
                try { hs.close(); } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            // HDFS access failed -> fallback to in-memory sample
            sendJson(resp, mapOf("items", files));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getParameter("path");
        if (path == null) { sendJson(resp, mapOf("success", false, "message", "path required")); return; }
        // try HDFS delete first
        try {
            HdfsService hs = new HdfsService("hdfs://node1:8020", "root", new Configuration());
            try {
                boolean ok = hs.delete(path, false);
                sendJson(resp, mapOf("success", ok));
            } finally {
                try { hs.close(); } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            // fallback to in-memory
            boolean removed = files.removeIf(f -> path.equals(f.get("path")));
            sendJson(resp, mapOf("success", removed));
        }
    }

    private static Map<String,Object> mapOf(Object... kv){ Map<String,Object> m=new HashMap<>(); for(int i=0;i+1<kv.length;i+=2) m.put(String.valueOf(kv[i]), kv[i+1]); return m; }
    private void sendJson(HttpServletResponse resp, Object data) throws IOException { resp.setContentType("application/json;charset=UTF-8"); mapper.writeValue(resp.getWriter(), data); }
}
