package com.carrental.core;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * Database schema initializer - reads and executes schema.sql
 */
public class DatabaseInitializer {
    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/bigdata?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowMultiQueries=true";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASS = "123456";
    
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            initializeSchema();
        } catch (Exception e) {
            System.err.println("Failed to initialize database schema: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void initializeSchema() {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS)) {
            InputStream is = DatabaseInitializer.class.getClassLoader().getResourceAsStream("schema.sql");
            if (is == null) {
                System.out.println("schema.sql not found, skipping database initialization");
                return;
            }
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder sql = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--")) {
                    continue;
                }
                sql.append(line).append(" ");
            }
            
            String[] statements = sql.toString().split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String statement : statements) {
                    String trimmed = statement.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (Exception e) {
                            System.err.println("Failed to execute statement: " + trimmed.substring(0, Math.min(100, trimmed.length())));
                            System.err.println("Error: " + e.getMessage());
                        }
                    }
                }
            }
            
            System.out.println("Database schema initialized successfully");
        } catch (Exception e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static Connection getConnection() throws Exception {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASS);
    }
}
