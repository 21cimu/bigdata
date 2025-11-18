package com.hdfsdrive.web;

import com.hdfsdrive.core.ShareService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Public share viewer: given id, returns basic JSON info or redirects to file download/preview.
 * For simplicity this returns JSON with path and name so UI can build a preview page.
 */
@WebServlet("/api/share/public")
public class SharePublicServlet extends HttpServlet {
    private ShareService shareService;

    @Override
    public void init() throws ServletException {
        try {
            String storePath = getServletContext().getRealPath("/WEB-INF/share.json");
            shareService = new ShareService(storePath);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String id = req.getParameter("id");
        if (id == null || id.isEmpty()) {
            resp.setStatus(400);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"success\":false,\"message\":\"id required\"}");
            return;
        }

        try {
            ShareService.Entry e = shareService.getById(id);
            if (e == null) {
                resp.setStatus(404);
                resp.setContentType("application/json;charset=UTF-8");
                resp.getWriter().write("{\"success\":false,\"message\":\"share not found or expired\"}");
                return;
            }

            // If the client prefers HTML (normal browser navigation) or explicitly asked for a page, redirect
            String accept = req.getHeader("Accept");
            String showParam = req.getParameter("show");
            String xreq = req.getHeader("X-Requested-With");
            boolean wantsHtml = (showParam != null && "page".equals(showParam)) || (accept != null && accept.contains("text/html"));
            boolean isAjax = (xreq != null && xreq.equalsIgnoreCase("XMLHttpRequest")) || (accept != null && accept.contains("application/json"));
            if (wantsHtml && !isAjax) {
                // redirect to friendly share page which will call this API to get JSON
                String ctx = req.getContextPath();
                resp.sendRedirect(ctx + "/share.html?id=" + id);
                return;
            }

            // Otherwise return JSON representing the share entry
            resp.setContentType("application/json;charset=UTF-8");
            com.fasterxml.jackson.databind.ObjectMapper _m = new com.fasterxml.jackson.databind.ObjectMapper();
            resp.getWriter().write("{\"success\":true,\"item\":" + _m.writeValueAsString(e) + "}");
        } catch (Exception ex) {
            resp.setStatus(500);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"success\":false,\"message\":\"server error\"}");
        }
    }
}