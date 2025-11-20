package com.hdfsdrive.web.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {"/api/admin/logs"})
public class AdminLogsServlet extends HttpServlet {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final List<Map<String,Object>> logs = Collections.synchronizedList(new ArrayList<>());
    static {
        logs.add(mapOf("time", System.currentTimeMillis()-60000, "user", "user1", "action", "上传", "detail", "/user1/docs/readme.txt"));
        logs.add(mapOf("time", System.currentTimeMillis()-3600000, "user", "user2", "action", "删除", "detail", "/user2/old.zip"));
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        // Attempt to read WEB-INF/logs/admin-operations.log
        List<Map<String,Object>> out = new ArrayList<>();
        boolean loadedFromFile = false;
        try {
            String logsDir = getServletContext().getRealPath("/WEB-INF/logs");
            if (logsDir != null) {
                java.io.File f = new java.io.File(logsDir, "admin-operations.log");
                if (f.exists() && f.canRead()) {
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            if (line.trim().isEmpty()) continue; // skip blank lines
                            // expect format: yyyy-MM-dd HH:mm:ss\tuser=...\taction=...\tpath=...\tinfo=...
                            String[] parts = line.split("\t");
                            Map<String,Object> m = new HashMap<>();
                            try {
                                m.put("raw", line);
                                // parse timestamp
                                if (parts.length > 0) {
                                    m.put("timeText", parts[0]);
                                    try {
                                        m.put("time", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(parts[0]).getTime());
                                    } catch (java.text.ParseException e) {
                                        // if parsing fails, this log entry might not be sorted correctly
                                    }
                                }
                                for (int i = 1; i < parts.length; i++) {
                                    String p = parts[i];
                                    int eq = p.indexOf('=');
                                    if (eq > 0) {
                                        String k = p.substring(0, eq);
                                        String v = p.substring(eq + 1);
                                        m.put(k, v);
                                    }
                                }
                                // basic filter: ignore lines that do not have action
                                if (!m.containsKey("action")) continue;
                                out.add(m);
                            } catch (Exception ignore) {}
                        }
                    }
                    loadedFromFile = true;
                }
            }
        } catch (Throwable t) {
            // ignore and fall back to default mock
        }

        if (!loadedFromFile) {
            out.addAll(logs);
        }

        // Sort logs by time descending, gracefully handling missing time field
        out.sort((a, b) -> {
            Object timeAObj = a.get("time");
            Object timeBObj = b.get("time");
            long timeA = (timeAObj instanceof Long) ? (Long) timeAObj : 0;
            long timeB = (timeBObj instanceof Long) ? (Long) timeBObj : 0;
            return Long.compare(timeB, timeA);
        });

        sendJson(resp, mapOf("items", out));
    }

    private static Map<String,Object> mapOf(Object... kv){ Map<String,Object> m=new HashMap<>(); for(int i=0;i+1<kv.length;i+=2) m.put(String.valueOf(kv[i]), kv[i+1]); return m; }
    private void sendJson(HttpServletResponse resp, Object data) throws IOException { resp.setContentType("application/json;charset=UTF-8"); mapper.writeValue(resp.getWriter(), data); }
}
