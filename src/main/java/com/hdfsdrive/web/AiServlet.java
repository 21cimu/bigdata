package com.hdfsdrive.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Simple proxy servlet that forwards chat requests to Aliyun Dashscope (通义千问)
 * in compatible mode and streams the remote response back to the client.
 *
 * Expects a POST to /api/ai/chat with a JSON body compatible with OpenAI Chat Completions.
 * The servlet will add (or respect) the "stream" flag and proxy the streaming response.
 *
 * Configure API key with environment variable DASHSCOPE_API_KEY or servlet context param "dashtscope.api.key".
 * Configure base URL with DASHSCOPE_BASE_URL (default: https://dashscope.aliyuncs.com/compatible-mode/v1)
 */
@WebServlet("/api/ai/*")
public class AiServlet extends HttpServlet {
    private ObjectMapper mapper = new ObjectMapper();

    private String getApiKey() {
        String k = System.getenv("DASHSCOPE_API_KEY");
        if (k != null && !k.isEmpty()) return k;
        String ctx = getServletContext().getInitParameter("DASHSCOPE_API_KEY");
        if (ctx != null && !ctx.isEmpty()) return ctx;
        return null;
    }

    private String getBaseUrl() {
        String b = System.getenv("DASHSCOPE_BASE_URL");
        if (b != null && !b.isEmpty()) return b;
        String ctx = getServletContext().getInitParameter("DASHSCOPE_BASE_URL");
        if (ctx != null && !ctx.isEmpty()) return ctx;
        return "https://dashscope.aliyuncs.com/compatible-mode/v1";
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo(); // e.g. /chat
        if (path == null) path = "/";
        if (path.equals("/chat") || path.equals("/chat/")) {
            proxyChat(req, resp);
            return;
        }
        // fallback: if no path or unknown action, treat as chat
        proxyChat(req, resp);
    }

    private void proxyChat(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json;charset=UTF-8");
            Map<String,Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", "DASHSCOPE_API_KEY not configured on server");
            mapper.writeValue(resp.getWriter(), err);
            return;
        }

        String baseUrl = getBaseUrl();
        String target = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

        // read incoming body
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (InputStream in = req.getInputStream()) {
            byte[] b = new byte[8192];
            int r;
            while ((r = in.read(b)) != -1) buf.write(b, 0, r);
        }
        String body = new String(buf.toByteArray(), StandardCharsets.UTF_8);
        if (body == null || body.trim().isEmpty()) {
            // require a messages array at least
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json;charset=UTF-8");
            Map<String,Object> err2 = new HashMap<>();
            err2.put("success", false);
            err2.put("message", "Request body is required");
            mapper.writeValue(resp.getWriter(), err2);
            return;
        }

        // ensure stream=true is set in the proxied payload so server streams
        Map<String, Object> jsonMap;
        try {
            jsonMap = mapper.readValue(body, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
        } catch (Exception e) {
            // If body is not JSON, forward as-is (best-effort)
            jsonMap = null;
        }
        if (jsonMap != null) {
            Object streamFlag = jsonMap.get("stream");
            if (!(streamFlag instanceof Boolean) || !((Boolean) streamFlag)) {
                // enable streaming by default
                jsonMap.put("stream", true);
            }
            body = mapper.writeValueAsString(jsonMap);
        }

        HttpURLConnection con = null;
        try {
            URL u = new URL(target);
            con = (HttpURLConnection) u.openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setConnectTimeout(15000);
            con.setReadTimeout(0); // infinite read (stream)
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
            con.setRequestProperty("Accept", "text/event-stream, application/json, */*");

            // send request body
            try (OutputStream out = con.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }

            int status = con.getResponseCode();
            InputStream remoteIn = (status >= 200 && status < 400) ? con.getInputStream() : con.getErrorStream();
            if (remoteIn == null) remoteIn = new ByteArrayInputStream(("{\"success\":false,\"message\":\"Empty response from remote (status=" + status + ")\"}").getBytes(StandardCharsets.UTF_8));

            // Stream and parse SSE "data: ..." events, extract JSON and send only delta.content text
            resp.setStatus(status);
            resp.setContentType("text/plain;charset=UTF-8");
            resp.setHeader("Cache-Control", "no-cache");
            resp.setHeader("X-Proxy-By", "AiServlet");

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(remoteIn, StandardCharsets.UTF_8));
                 OutputStreamWriter writer = new OutputStreamWriter(resp.getOutputStream(), StandardCharsets.UTF_8)) {

                String line;
                StringBuilder eventBuf = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    // SSE format: lines starting with "data: " carry payload. blank line indicates event dispatch
                    if (line.startsWith("data: ")) {
                        String dataPart = line.substring(6);
                        eventBuf.append(dataPart).append('\n');
                    } else if (line.trim().isEmpty()) {
                        // dispatch event
                        String eventData = eventBuf.toString().trim();
                        eventBuf.setLength(0);
                        if (eventData.length() == 0) {
                            continue;
                        }
                        if ("[DONE]".equals(eventData) || "[DONE]".equals(eventData.trim())) {
                            // signal end of stream to client and break
                            writer.write("[DONE]");
                            writer.flush();
                            break;
                        }
                        // try parse eventData as JSON and extract delta content(s)
                        try {
                            JsonNode node = mapper.readTree(eventData);
                            // node could be an object with choices array
                            if (node.has("choices") && node.get("choices").isArray()) {
                                Iterator<JsonNode> it = node.get("choices").elements();
                                while (it.hasNext()) {
                                    JsonNode choice = it.next();
                                    JsonNode delta = choice.get("delta");
                                    if (delta != null && delta.has("content")) {
                                        String content = delta.get("content").asText();
                                        if (content != null && !content.isEmpty()) {
                                            writer.write(content);
                                            writer.flush();
                                        }
                                    }
                                }
                            } else {
                                // fallback: if top-level has content field
                                if (node.has("content")) {
                                    String content = node.get("content").asText();
                                    if (content != null && !content.isEmpty()) {
                                        writer.write(content);
                                        writer.flush();
                                    }
                                } else {
                                    // if structure unknown, forward raw eventData
                                    writer.write(eventData);
                                    writer.flush();
                                }
                            }
                        } catch (Exception ex) {
                            // not json — forward raw text
                            writer.write(eventData);
                            writer.flush();
                        }
                    } else {
                        // lines not starting with data: might be comments or other prefixes; ignore or accumulate
                        // ignore for now
                    }
                }

            }

        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json;charset=UTF-8");
            Map<String,Object> err3 = new HashMap<>();
            err3.put("success", false);
            err3.put("message", "Proxy failed: " + e.getMessage());
            mapper.writeValue(resp.getWriter(), err3);
        } finally {
            if (con != null) con.disconnect();
        }
    }
}