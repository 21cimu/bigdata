package com.carrental.web;

import com.carrental.core.DatabaseInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vehicle API Servlet - 车辆API
 * Handles vehicle listing, search, and details
 */
@WebServlet(name = "VehicleServlet", urlPatterns = {"/api/vehicle", "/api/vehicle/*"})
public class VehicleServlet extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // List vehicles
                String category = req.getParameter("category");
                String store = req.getParameter("store");
                String status = req.getParameter("status");
                
                List<Map<String, Object>> vehicles = listVehicles(category, store, status);
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", vehicles);
                result.put("total", vehicles.size());
                
                resp.getWriter().write(mapper.writeValueAsString(result));
            } else {
                // Get vehicle by ID
                String idStr = pathInfo.substring(1);
                long id = Long.parseLong(idStr);
                
                Map<String, Object> vehicle = getVehicleById(id);
                Map<String, Object> result = new HashMap<>();
                if (vehicle != null) {
                    result.put("success", true);
                    result.put("data", vehicle);
                } else {
                    result.put("success", false);
                    result.put("message", "Vehicle not found");
                }
                
                resp.getWriter().write(mapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(mapper.writeValueAsString(error));
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        try {
            // Read JSON body
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) {
                sb.append(line);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> vehicleData = mapper.readValue(sb.toString(), Map.class);
            
            // Basic validation
            if (vehicleData.get("vehicleCode") == null || vehicleData.get("brand") == null) {
                throw new IllegalArgumentException("Required fields missing: vehicleCode, brand");
            }
            
            // Create vehicle
            long id = createVehicle(vehicleData);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("id", id);
            result.put("message", "Vehicle created successfully");
            
            resp.getWriter().write(mapper.writeValueAsString(result));
        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write(mapper.writeValueAsString(error));
        }
    }
    
    private List<Map<String, Object>> listVehicles(String category, String store, String status) throws Exception {
        List<Map<String, Object>> vehicles = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT v.*, c.category_name, s.store_name FROM vehicles v ");
        sql.append("LEFT JOIN vehicle_categories c ON v.category_id = c.id ");
        sql.append("LEFT JOIN stores s ON v.store_id = s.id WHERE 1=1");
        
        if (category != null && !category.isEmpty()) {
            sql.append(" AND v.category_id = ?");
        }
        if (store != null && !store.isEmpty()) {
            sql.append(" AND v.store_id = ?");
        }
        if (status != null && !status.isEmpty()) {
            sql.append(" AND v.status = ?");
        } else {
            sql.append(" AND v.status = 'AVAILABLE'");
        }
        
        sql.append(" ORDER BY v.id DESC LIMIT 100");
        
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            if (category != null && !category.isEmpty()) {
                try {
                    ps.setLong(paramIndex++, Long.parseLong(category));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid category ID: " + category);
                }
            }
            if (store != null && !store.isEmpty()) {
                try {
                    ps.setLong(paramIndex++, Long.parseLong(store));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid store ID: " + store);
                }
            }
            if (status != null && !status.isEmpty()) {
                ps.setString(paramIndex++, status);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> vehicle = new HashMap<>();
                    vehicle.put("id", rs.getLong("id"));
                    vehicle.put("vehicleCode", rs.getString("vehicle_code"));
                    vehicle.put("licensePlate", rs.getString("license_plate"));
                    vehicle.put("brand", rs.getString("brand"));
                    vehicle.put("model", rs.getString("model"));
                    vehicle.put("year", rs.getInt("year"));
                    vehicle.put("color", rs.getString("color"));
                    vehicle.put("seats", rs.getInt("seats"));
                    vehicle.put("transmission", rs.getString("transmission"));
                    vehicle.put("fuelType", rs.getString("fuel_type"));
                    vehicle.put("dailyPrice", rs.getBigDecimal("daily_price"));
                    vehicle.put("deposit", rs.getBigDecimal("deposit"));
                    vehicle.put("images", rs.getString("images"));
                    vehicle.put("status", rs.getString("status"));
                    vehicle.put("categoryName", rs.getString("category_name"));
                    vehicle.put("storeName", rs.getString("store_name"));
                    vehicles.add(vehicle);
                }
            }
        }
        
        return vehicles;
    }
    
    private Map<String, Object> getVehicleById(long id) throws Exception {
        String sql = "SELECT v.*, c.category_name, s.store_name FROM vehicles v " +
                    "LEFT JOIN vehicle_categories c ON v.category_id = c.id " +
                    "LEFT JOIN stores s ON v.store_id = s.id WHERE v.id = ?";
        
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, id);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> vehicle = new HashMap<>();
                    vehicle.put("id", rs.getLong("id"));
                    vehicle.put("vehicleCode", rs.getString("vehicle_code"));
                    vehicle.put("vin", rs.getString("vin"));
                    vehicle.put("licensePlate", rs.getString("license_plate"));
                    vehicle.put("categoryId", rs.getLong("category_id"));
                    vehicle.put("storeId", rs.getLong("store_id"));
                    vehicle.put("brand", rs.getString("brand"));
                    vehicle.put("model", rs.getString("model"));
                    vehicle.put("year", rs.getInt("year"));
                    vehicle.put("color", rs.getString("color"));
                    vehicle.put("seats", rs.getInt("seats"));
                    vehicle.put("transmission", rs.getString("transmission"));
                    vehicle.put("fuelType", rs.getString("fuel_type"));
                    vehicle.put("dailyPrice", rs.getBigDecimal("daily_price"));
                    vehicle.put("deposit", rs.getBigDecimal("deposit"));
                    vehicle.put("mileage", rs.getInt("mileage"));
                    vehicle.put("description", rs.getString("description"));
                    vehicle.put("images", rs.getString("images"));
                    vehicle.put("features", rs.getString("features"));
                    vehicle.put("status", rs.getString("status"));
                    vehicle.put("categoryName", rs.getString("category_name"));
                    vehicle.put("storeName", rs.getString("store_name"));
                    return vehicle;
                }
            }
        }
        
        return null;
    }
    
    private long createVehicle(Map<String, Object> data) throws Exception {
        String sql = "INSERT INTO vehicles (vehicle_code, vin, license_plate, category_id, store_id, " +
                    "brand, model, year, color, seats, transmission, fuel_type, daily_price, deposit, " +
                    "description, images, features, status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, (String) data.get("vehicleCode"));
            ps.setString(2, (String) data.get("vin"));
            ps.setString(3, (String) data.get("licensePlate"));
            
            // Use specific setters with null handling instead of setObject
            if (data.get("categoryId") != null) {
                ps.setLong(4, ((Number) data.get("categoryId")).longValue());
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            if (data.get("storeId") != null) {
                ps.setLong(5, ((Number) data.get("storeId")).longValue());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            
            ps.setString(6, (String) data.get("brand"));
            ps.setString(7, (String) data.get("model"));
            
            if (data.get("year") != null) {
                ps.setInt(8, ((Number) data.get("year")).intValue());
            } else {
                ps.setNull(8, java.sql.Types.INTEGER);
            }
            
            ps.setString(9, (String) data.get("color"));
            
            if (data.get("seats") != null) {
                ps.setInt(10, ((Number) data.get("seats")).intValue());
            } else {
                ps.setNull(10, java.sql.Types.INTEGER);
            }
            
            ps.setString(11, (String) data.get("transmission"));
            ps.setString(12, (String) data.get("fuelType"));
            
            if (data.get("dailyPrice") != null) {
                ps.setBigDecimal(13, new java.math.BigDecimal(data.get("dailyPrice").toString()));
            } else {
                ps.setNull(13, java.sql.Types.DECIMAL);
            }
            if (data.get("deposit") != null) {
                ps.setBigDecimal(14, new java.math.BigDecimal(data.get("deposit").toString()));
            } else {
                ps.setNull(14, java.sql.Types.DECIMAL);
            }
            
            ps.setString(15, (String) data.get("description"));
            ps.setString(16, (String) data.get("images"));
            ps.setString(17, (String) data.get("features"));
            ps.setString(18, (String) data.getOrDefault("status", "AVAILABLE"));
            
            ps.executeUpdate();
            
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        
        throw new Exception("Failed to create vehicle");
    }
}
