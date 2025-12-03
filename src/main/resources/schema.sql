-- Car Rental System Database Schema
-- 租车系统数据库结构

-- 1. 用户表 (扩展)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    phone VARCHAR(50) UNIQUE,
    email VARCHAR(255),
    avatar TEXT,
    nickname VARCHAR(100),
    real_name VARCHAR(100),
    id_card VARCHAR(50),
    id_card_verified BOOLEAN DEFAULT FALSE,
    driver_license VARCHAR(50),
    driver_license_verified BOOLEAN DEFAULT FALSE,
    driver_license_expiry DATE,
    face_verified BOOLEAN DEFAULT FALSE,
    member_level VARCHAR(20) DEFAULT 'NORMAL',
    points INT DEFAULT 0,
    balance DECIMAL(10,2) DEFAULT 0.00,
    deposit_limit DECIMAL(10,2) DEFAULT 0.00,
    credit_score INT DEFAULT 100,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_phone (phone),
    INDEX idx_email (email),
    INDEX idx_member_level (member_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 2. 用户地址表
CREATE TABLE IF NOT EXISTS user_addresses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    address_type VARCHAR(20) DEFAULT 'OTHER',
    contact_name VARCHAR(100),
    contact_phone VARCHAR(50),
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    address VARCHAR(500),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    is_default BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户地址表';

-- 3. 门店表
CREATE TABLE IF NOT EXISTS stores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    store_name VARCHAR(200) NOT NULL,
    store_code VARCHAR(50) UNIQUE,
    province VARCHAR(50),
    city VARCHAR(50),
    district VARCHAR(50),
    address VARCHAR(500),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    phone VARCHAR(50),
    business_hours VARCHAR(200),
    support_pickup BOOLEAN DEFAULT TRUE,
    support_return BOOLEAN DEFAULT TRUE,
    support_delivery BOOLEAN DEFAULT FALSE,
    delivery_range INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_city (city),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店表';

-- 4. 车辆分类表
CREATE TABLE IF NOT EXISTS vehicle_categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL,
    category_code VARCHAR(50) UNIQUE,
    parent_id BIGINT,
    description VARCHAR(500),
    icon_url VARCHAR(500),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_parent_id (parent_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆分类表';

-- 5. 车辆表
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_code VARCHAR(50) UNIQUE NOT NULL,
    vin VARCHAR(50) UNIQUE,
    license_plate VARCHAR(20) UNIQUE,
    category_id BIGINT,
    store_id BIGINT,
    brand VARCHAR(100),
    model VARCHAR(100),
    year INT,
    color VARCHAR(50),
    seats INT,
    transmission VARCHAR(20),
    fuel_type VARCHAR(20),
    daily_price DECIMAL(10,2),
    deposit DECIMAL(10,2),
    mileage INT DEFAULT 0,
    purchase_date DATE,
    insurance_expiry DATE,
    inspection_expiry DATE,
    description TEXT,
    images TEXT,
    features TEXT,
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (category_id) REFERENCES vehicle_categories(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_category (category_id),
    INDEX idx_store (store_id),
    INDEX idx_status (status),
    INDEX idx_brand (brand)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆表';

-- 6. 价格表
CREATE TABLE IF NOT EXISTS vehicle_pricing (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    vehicle_id BIGINT,
    category_id BIGINT,
    store_id BIGINT,
    pricing_type VARCHAR(20) DEFAULT 'DAILY',
    price_date DATE,
    start_date DATE,
    end_date DATE,
    price DECIMAL(10,2) NOT NULL,
    weekly_price DECIMAL(10,2),
    monthly_price DECIMAL(10,2),
    overtime_price DECIMAL(10,2),
    overmileage_price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES vehicle_categories(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_date (price_date),
    INDEX idx_date_range (start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆定价表';

-- 7. 订单表
CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT NOT NULL,
    store_id BIGINT,
    pickup_store_id BIGINT,
    return_store_id BIGINT,
    pickup_time DATETIME,
    return_time DATETIME,
    actual_pickup_time DATETIME,
    actual_return_time DATETIME,
    rental_days INT,
    daily_rate DECIMAL(10,2),
    rental_amount DECIMAL(10,2),
    service_fee DECIMAL(10,2) DEFAULT 0.00,
    insurance_fee DECIMAL(10,2) DEFAULT 0.00,
    addon_fee DECIMAL(10,2) DEFAULT 0.00,
    discount_amount DECIMAL(10,2) DEFAULT 0.00,
    total_amount DECIMAL(10,2),
    deposit_amount DECIMAL(10,2),
    deposit_status VARCHAR(20) DEFAULT 'UNPAID',
    payment_status VARCHAR(20) DEFAULT 'UNPAID',
    order_status VARCHAR(20) DEFAULT 'PENDING',
    pickup_type VARCHAR(20),
    return_type VARCHAR(20),
    pickup_address VARCHAR(500),
    return_address VARCHAR(500),
    remark TEXT,
    cancel_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    FOREIGN KEY (pickup_store_id) REFERENCES stores(id),
    FOREIGN KEY (return_store_id) REFERENCES stores(id),
    INDEX idx_user (user_id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (order_status),
    INDEX idx_pickup_time (pickup_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- 8. 订单附加服务表
CREATE TABLE IF NOT EXISTS order_addons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    addon_type VARCHAR(50),
    addon_name VARCHAR(200),
    quantity INT DEFAULT 1,
    unit_price DECIMAL(10,2),
    total_price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单附加服务表';

-- 9. 支付流水表
CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_no VARCHAR(50) UNIQUE NOT NULL,
    order_id BIGINT,
    user_id BIGINT NOT NULL,
    payment_type VARCHAR(20),
    payment_method VARCHAR(20),
    amount DECIMAL(10,2),
    status VARCHAR(20) DEFAULT 'PENDING',
    transaction_id VARCHAR(200),
    payment_time DATETIME,
    remark TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_order (order_id),
    INDEX idx_user (user_id),
    INDEX idx_payment_no (payment_no),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';

-- 10. 优惠券表
CREATE TABLE IF NOT EXISTS coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_code VARCHAR(50) UNIQUE,
    coupon_name VARCHAR(200),
    coupon_type VARCHAR(20),
    discount_type VARCHAR(20),
    discount_value DECIMAL(10,2),
    min_amount DECIMAL(10,2) DEFAULT 0.00,
    max_discount DECIMAL(10,2),
    total_quantity INT,
    used_quantity INT DEFAULT 0,
    per_user_limit INT DEFAULT 1,
    valid_from DATETIME,
    valid_to DATETIME,
    applicable_categories TEXT,
    applicable_stores TEXT,
    applicable_vehicles TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_code (coupon_code),
    INDEX idx_status (status),
    INDEX idx_valid_period (valid_from, valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券表';

-- 11. 用户优惠券表
CREATE TABLE IF NOT EXISTS user_coupons (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    coupon_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'UNUSED',
    used_order_id BIGINT,
    used_time DATETIME,
    expire_time DATETIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (coupon_id) REFERENCES coupons(id),
    FOREIGN KEY (used_order_id) REFERENCES orders(id),
    INDEX idx_user (user_id),
    INDEX idx_coupon (coupon_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';

-- 12. 营销活动表
CREATE TABLE IF NOT EXISTS promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    promotion_name VARCHAR(200),
    promotion_type VARCHAR(20),
    description TEXT,
    start_time DATETIME,
    end_time DATETIME,
    discount_rules TEXT,
    applicable_categories TEXT,
    applicable_stores TEXT,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_time_range (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销活动表';

-- 13. 验车记录表
CREATE TABLE IF NOT EXISTS inspections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    inspection_type VARCHAR(20),
    vehicle_id BIGINT NOT NULL,
    inspector_id BIGINT,
    mileage INT,
    fuel_level INT,
    damage_notes TEXT,
    images TEXT,
    videos TEXT,
    inspection_time DATETIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    FOREIGN KEY (inspector_id) REFERENCES users(id),
    INDEX idx_order (order_id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_type (inspection_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验车记录表';

-- 14. 评价表
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    vehicle_id BIGINT,
    store_id BIGINT,
    rating INT,
    vehicle_rating INT,
    service_rating INT,
    content TEXT,
    images TEXT,
    reply_content TEXT,
    reply_time DATETIME,
    status VARCHAR(20) DEFAULT 'PUBLISHED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (vehicle_id) REFERENCES vehicles(id),
    FOREIGN KEY (store_id) REFERENCES stores(id),
    INDEX idx_order (order_id),
    INDEX idx_user (user_id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评价表';

-- 15. 售后工单表
CREATE TABLE IF NOT EXISTS service_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    request_no VARCHAR(50) UNIQUE NOT NULL,
    order_id BIGINT,
    user_id BIGINT NOT NULL,
    request_type VARCHAR(20),
    subject VARCHAR(200),
    content TEXT,
    images TEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    handler_id BIGINT,
    reply_content TEXT,
    resolved_time DATETIME,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (handler_id) REFERENCES users(id),
    INDEX idx_request_no (request_no),
    INDEX idx_order (order_id),
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='售后工单表';

-- 16. 系统日志表
CREATE TABLE IF NOT EXISTS system_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    action VARCHAR(100),
    module VARCHAR(50),
    ip_address VARCHAR(50),
    user_agent TEXT,
    request_data TEXT,
    response_data TEXT,
    status VARCHAR(20),
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_action (action),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统日志表';

-- 17. 公告表
CREATE TABLE IF NOT EXISTS announcements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200),
    content TEXT,
    announcement_type VARCHAR(20),
    target_audience VARCHAR(20) DEFAULT 'ALL',
    priority INT DEFAULT 0,
    start_time DATETIME,
    end_time DATETIME,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (created_by) REFERENCES users(id),
    INDEX idx_status (status),
    INDEX idx_time_range (start_time, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 18. 分享记录表
CREATE TABLE IF NOT EXISTS shares (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    share_type VARCHAR(20),
    content_id BIGINT,
    title VARCHAR(200),
    description TEXT,
    url VARCHAR(500),
    views INT DEFAULT 0,
    expire_time DATETIME,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分享记录表';

-- 19. 积分记录表
CREATE TABLE IF NOT EXISTS point_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    points INT NOT NULL,
    balance INT,
    source VARCHAR(50),
    reference_id BIGINT,
    description VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user (user_id),
    INDEX idx_source (source)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分记录表';

-- 20. 会员等级配置表
CREATE TABLE IF NOT EXISTS member_levels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    level_name VARCHAR(50) NOT NULL,
    level_code VARCHAR(20) UNIQUE NOT NULL,
    min_points INT DEFAULT 0,
    min_orders INT DEFAULT 0,
    min_amount DECIMAL(10,2) DEFAULT 0.00,
    discount_rate DECIMAL(5,2) DEFAULT 100.00,
    benefits TEXT,
    icon_url VARCHAR(500),
    sort_order INT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员等级配置表';

-- Insert default member levels
INSERT INTO member_levels (level_name, level_code, min_points, min_orders, min_amount, discount_rate, benefits) VALUES
('普通会员', 'NORMAL', 0, 0, 0.00, 100.00, '基础租车服务'),
('黄金会员', 'GOLD', 1000, 5, 5000.00, 95.00, '95折优惠,优先取车,专属客服'),
('钻石会员', 'DIAMOND', 5000, 20, 20000.00, 90.00, '9折优惠,免押金,免费升级,VIP通道');

-- Insert default vehicle categories
INSERT INTO vehicle_categories (category_name, category_code, description, sort_order) VALUES
('经济型', 'ECONOMY', '经济实惠，适合日常代步', 1),
('舒适型', 'COMFORT', '舒适宽敞，适合商务出行', 2),
('SUV', 'SUV', '空间大，适合家庭出游', 3),
('商务型', 'BUSINESS', '高端商务，彰显品味', 4),
('豪华型', 'LUXURY', '奢华体验，尊贵之选', 5),
('新能源', 'EV', '环保节能，绿色出行', 6),
('MPV', 'MPV', '7座大空间，多人出行', 7),
('跑车', 'SPORTS', '激情驾驶，极致体验', 8);
