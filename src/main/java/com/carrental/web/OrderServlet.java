package com.carrental.web;

import com.carrental.core.DatabaseInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Order API Servlet - 订单API
 * Handles order creation, listing, and management
 */
@WebServlet(name = "OrderServlet", urlPatterns = {"/api/order", "/api/order/*"})
public class OrderServlet extends HttpServlet {
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first");
            resp.getWriter().write(mapper.writeValueAsString(error));
            return;
        }
        
        Long userId = (Long) session.getAttribute("userId");
        String pathInfo = req.getPathInfo();
        
        try {
            if (pathInfo == null || pathInfo.equals("/")) {
                // List user orders
                String status = req.getParameter("status");
                List<Map<String, Object>> orders = listUserOrders(userId, status);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("data", orders);
                result.put("total", orders.size());
                
                resp.getWriter().write(mapper.writeValueAsString(result));
            } else {
                // Get order by ID
                String idStr = pathInfo.substring(1);
                long orderId = Long.parseLong(idStr);
                
                Map<String, Object> order = getOrderById(orderId, userId);
                Map<String, Object> result = new HashMap<>();
                if (order != null) {
                    result.put("success", true);
                    result.put("data", order);
                } else {
                    result.put("success", false);
                    result.put("message", "Order not found");
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
        
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Please login first");
            resp.getWriter().write(mapper.writeValueAsString(error));
            return;
        }
        
        Long userId = (Long) session.getAttribute("userId");
        
        try {
            // Read JSON body
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = req.getReader().readLine()) != null) {
                sb.append(line);
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> orderData = mapper.readValue(sb.toString(), Map.class);
            orderData.put("userId", userId);
            
            // Create order
            Map<String, Object> order = createOrder(orderData);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", order);
            result.put("message", "Order created successfully");
            
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
    
    private List<Map<String, Object>> listUserOrders(Long userId, String status) throws Exception {
        List<Map<String, Object>> orders = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT o.*, v.brand, v.model, v.license_plate, s.store_name ");
        sql.append("FROM orders o ");
        sql.append("LEFT JOIN vehicles v ON o.vehicle_id = v.id ");
        sql.append("LEFT JOIN stores s ON o.store_id = s.id ");
        sql.append("WHERE o.user_id = ?");
        
        if (status != null && !status.isEmpty()) {
            sql.append(" AND o.order_status = ?");
        }
        
        sql.append(" ORDER BY o.created_at DESC LIMIT 100");
        
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            
            int paramIndex = 1;
            ps.setLong(paramIndex++, userId);
            
            if (status != null && !status.isEmpty()) {
                ps.setString(paramIndex++, status);
            }
            
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", rs.getLong("id"));
                    order.put("orderNo", rs.getString("order_no"));
                    order.put("vehicleId", rs.getLong("vehicle_id"));
                    order.put("brand", rs.getString("brand"));
                    order.put("model", rs.getString("model"));
                    order.put("licensePlate", rs.getString("license_plate"));
                    order.put("storeName", rs.getString("store_name"));
                    order.put("pickupTime", rs.getTimestamp("pickup_time"));
                    order.put("returnTime", rs.getTimestamp("return_time"));
                    order.put("rentalDays", rs.getInt("rental_days"));
                    order.put("totalAmount", rs.getBigDecimal("total_amount"));
                    order.put("depositAmount", rs.getBigDecimal("deposit_amount"));
                    order.put("orderStatus", rs.getString("order_status"));
                    order.put("paymentStatus", rs.getString("payment_status"));
                    order.put("createdAt", rs.getTimestamp("created_at"));
                    orders.add(order);
                }
            }
        }
        
        return orders;
    }
    
    private Map<String, Object> getOrderById(long orderId, Long userId) throws Exception {
        String sql = "SELECT o.*, v.brand, v.model, v.license_plate, v.images, " +
                    "s.store_name, s.address as store_address, s.phone as store_phone " +
                    "FROM orders o " +
                    "LEFT JOIN vehicles v ON o.vehicle_id = v.id " +
                    "LEFT JOIN stores s ON o.store_id = s.id " +
                    "WHERE o.id = ? AND o.user_id = ?";
        
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setLong(1, orderId);
            ps.setLong(2, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> order = new HashMap<>();
                    order.put("id", rs.getLong("id"));
                    order.put("orderNo", rs.getString("order_no"));
                    order.put("userId", rs.getLong("user_id"));
                    order.put("vehicleId", rs.getLong("vehicle_id"));
                    order.put("storeId", rs.getLong("store_id"));
                    order.put("brand", rs.getString("brand"));
                    order.put("model", rs.getString("model"));
                    order.put("licensePlate", rs.getString("license_plate"));
                    order.put("vehicleImages", rs.getString("images"));
                    order.put("storeName", rs.getString("store_name"));
                    order.put("storeAddress", rs.getString("store_address"));
                    order.put("storePhone", rs.getString("store_phone"));
                    order.put("pickupTime", rs.getTimestamp("pickup_time"));
                    order.put("returnTime", rs.getTimestamp("return_time"));
                    order.put("actualPickupTime", rs.getTimestamp("actual_pickup_time"));
                    order.put("actualReturnTime", rs.getTimestamp("actual_return_time"));
                    order.put("rentalDays", rs.getInt("rental_days"));
                    order.put("dailyRate", rs.getBigDecimal("daily_rate"));
                    order.put("rentalAmount", rs.getBigDecimal("rental_amount"));
                    order.put("serviceFee", rs.getBigDecimal("service_fee"));
                    order.put("insuranceFee", rs.getBigDecimal("insurance_fee"));
                    order.put("addonFee", rs.getBigDecimal("addon_fee"));
                    order.put("discountAmount", rs.getBigDecimal("discount_amount"));
                    order.put("totalAmount", rs.getBigDecimal("total_amount"));
                    order.put("depositAmount", rs.getBigDecimal("deposit_amount"));
                    order.put("depositStatus", rs.getString("deposit_status"));
                    order.put("paymentStatus", rs.getString("payment_status"));
                    order.put("orderStatus", rs.getString("order_status"));
                    order.put("pickupType", rs.getString("pickup_type"));
                    order.put("returnType", rs.getString("return_type"));
                    order.put("pickupAddress", rs.getString("pickup_address"));
                    order.put("returnAddress", rs.getString("return_address"));
                    order.put("remark", rs.getString("remark"));
                    order.put("createdAt", rs.getTimestamp("created_at"));
                    return order;
                }
            }
        }
        
        return null;
    }
    
    private Map<String, Object> createOrder(Map<String, Object> data) throws Exception {
        String orderNo = "ORD" + System.currentTimeMillis();
        
        // Parse and validate dates
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime pickupTime;
        LocalDateTime returnTime;
        
        try {
            pickupTime = LocalDateTime.parse((String) data.get("pickupTime"), formatter);
            returnTime = LocalDateTime.parse((String) data.get("returnTime"), formatter);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format. Use ISO-8601 format (e.g., 2024-01-01T10:00:00)");
        }
        
        // Validate return time is after pickup time
        if (returnTime.isBefore(pickupTime) || returnTime.isEqual(pickupTime)) {
            throw new IllegalArgumentException("Return time must be after pickup time");
        }
        
        // Calculate rental days
        long days = java.time.Duration.between(pickupTime, returnTime).toDays();
        if (days == 0) days = 1;
        
        // Get vehicle price
        Long vehicleId = ((Number) data.get("vehicleId")).longValue();
        BigDecimal dailyRate = getVehicleDailyPrice(vehicleId);
        BigDecimal deposit = getVehicleDeposit(vehicleId);
        
        // Calculate amounts
        BigDecimal rentalAmount = dailyRate.multiply(BigDecimal.valueOf(days));
        BigDecimal serviceFee = new BigDecimal(data.getOrDefault("serviceFee", "0").toString());
        BigDecimal insuranceFee = new BigDecimal(data.getOrDefault("insuranceFee", "0").toString());
        BigDecimal addonFee = new BigDecimal(data.getOrDefault("addonFee", "0").toString());
        BigDecimal discountAmount = new BigDecimal(data.getOrDefault("discountAmount", "0").toString());
        
        BigDecimal totalAmount = rentalAmount.add(serviceFee).add(insuranceFee).add(addonFee).subtract(discountAmount);
        
        String sql = "INSERT INTO orders (order_no, user_id, vehicle_id, store_id, pickup_store_id, " +
                    "return_store_id, pickup_time, return_time, rental_days, daily_rate, rental_amount, " +
                    "service_fee, insurance_fee, addon_fee, discount_amount, total_amount, deposit_amount, " +
                    "order_status, payment_status, deposit_status, pickup_type, return_type, pickup_address, " +
                    "return_address, remark) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            
            ps.setString(1, orderNo);
            ps.setLong(2, ((Number) data.get("userId")).longValue());
            ps.setLong(3, vehicleId);
            // Use setLong with proper null handling instead of setObject
            if (data.get("storeId") != null) {
                ps.setLong(4, ((Number) data.get("storeId")).longValue());
            } else {
                ps.setNull(4, java.sql.Types.BIGINT);
            }
            if (data.get("pickupStoreId") != null) {
                ps.setLong(5, ((Number) data.get("pickupStoreId")).longValue());
            } else {
                ps.setNull(5, java.sql.Types.BIGINT);
            }
            if (data.get("returnStoreId") != null) {
                ps.setLong(6, ((Number) data.get("returnStoreId")).longValue());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setTimestamp(7, Timestamp.valueOf(pickupTime));
            ps.setTimestamp(8, Timestamp.valueOf(returnTime));
            ps.setInt(9, (int) days);
            ps.setBigDecimal(10, dailyRate);
            ps.setBigDecimal(11, rentalAmount);
            ps.setBigDecimal(12, serviceFee);
            ps.setBigDecimal(13, insuranceFee);
            ps.setBigDecimal(14, addonFee);
            ps.setBigDecimal(15, discountAmount);
            ps.setBigDecimal(16, totalAmount);
            ps.setBigDecimal(17, deposit);
            ps.setString(18, "PENDING");
            ps.setString(19, "UNPAID");
            ps.setString(20, "UNPAID");
            ps.setString(21, (String) data.get("pickupType"));
            ps.setString(22, (String) data.get("returnType"));
            ps.setString(23, (String) data.get("pickupAddress"));
            ps.setString(24, (String) data.get("returnAddress"));
            ps.setString(25, (String) data.get("remark"));
            
            ps.executeUpdate();
            
            long orderId = 0;
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    orderId = rs.getLong(1);
                }
            }
            
            // Return created order
            Map<String, Object> result = new HashMap<>();
            result.put("id", orderId);
            result.put("orderNo", orderNo);
            result.put("totalAmount", totalAmount);
            result.put("depositAmount", deposit);
            result.put("orderStatus", "PENDING");
            result.put("paymentStatus", "UNPAID");
            
            return result;
        }
    }
    
    private BigDecimal getVehicleDailyPrice(Long vehicleId) throws Exception {
        String sql = "SELECT daily_price FROM vehicles WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("daily_price");
                }
            }
        }
        throw new Exception("Vehicle not found");
    }
    
    private BigDecimal getVehicleDeposit(Long vehicleId) throws Exception {
        String sql = "SELECT deposit FROM vehicles WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("deposit");
                }
            }
        }
        throw new Exception("Vehicle not found");
    }
}
