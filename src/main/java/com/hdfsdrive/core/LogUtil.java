package com.hdfsdrive.core;

import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogUtil {

    public static void log(ServletContext context, String username, String action, String detail) {
        log(context, username, action, detail, null);
    }

    public static void log(ServletContext context, String username, String action, String path, String info) {
        if (context == null) {
            System.err.println("LogUtil: ServletContext is null, cannot write log.");
            return;
        }
        try {
            String logsDir = context.getRealPath("/WEB-INF/logs");
            if (logsDir == null) {
                System.err.println("LogUtil: Could not resolve /WEB-INF/logs path.");
                return;
            }
            File dir = new File(logsDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File logFile = new File(dir, "admin-operations.log");
            try (FileWriter fw = new FileWriter(logFile, true); PrintWriter pw = new PrintWriter(fw)) {
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                String userPart = "user=" + (username == null ? "unknown" : username);
                String actionPart = "action=" + (action == null ? "" : action);

                StringBuilder sb = new StringBuilder();
                sb.append(ts).append("\t").append(userPart).append("\t").append(actionPart);

                if (path != null) {
                    sb.append("\tpath=").append(path);
                }
                if (info != null) {
                    sb.append("\tinfo=").append(info);
                }

                pw.println(sb.toString());
            }
        } catch (IOException e) {
            System.err.println("LogUtil: Failed to write to log file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

