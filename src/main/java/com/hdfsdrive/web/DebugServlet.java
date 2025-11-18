package com.hdfsdrive.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet("/api/debug/echo")
public class DebugServlet extends HttpServlet {
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Map<String,Object> out = new HashMap<>();
        try {
            Map<String,String[]> params = req.getParameterMap();
            Map<String,Object> pm = new HashMap<>();
            for (Map.Entry<String,String[]> e : params.entrySet()) {
                if (e.getValue() == null) pm.put(e.getKey(), null);
                else if (e.getValue().length == 1) pm.put(e.getKey(), e.getValue()[0]);
                else pm.put(e.getKey(), e.getValue());
            }
            out.put("params", pm);
            StringBuilder sb = new StringBuilder();
            BufferedReader br = req.getReader();
            String line;
            while ((line = br.readLine()) != null) { sb.append(line).append('\n'); }
            out.put("rawBody", sb.toString());
            out.put("headers", new HashMap<String,String>());
            resp.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(resp.getWriter(), out);
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            Map<String,Object> err = new HashMap<>(); err.put("error", e.getMessage());
            resp.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(resp.getWriter(), err);
        }
    }
}

