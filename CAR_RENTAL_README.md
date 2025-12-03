# 租车系统 - Car Rental System

## 项目概述

这是一个完整的Web端租车系统，采用前后端分离架构，支持用户租车、订单管理、会员系统、营销活动等完整业务流程。

## 技术栈

### 后端
- **Java 21** - 编程语言
- **Servlet (Jakarta EE 6.1.0)** - Web框架
- **Tomcat 11** - 应用服务器
- **MySQL 8.x** - 关系型数据库
- **Maven 3.9+** - 项目构建工具
- **Jackson 2.18.0** - JSON处理
- **Druid 1.2.23** - 数据库连接池

### 前端
- **Vue 3.4** - 渐进式JavaScript框架
- **Vue Router 4.2** - 路由管理
- **Pinia 2.1** - 状态管理
- **Element Plus 2.5** - UI组件库
- **Vite 5.0** - 前端构建工具
- **Axios 1.6** - HTTP客户端

## 系统特性

### 用户端功能

#### 1. 用户中心模块
- 手机号/邮箱注册登录
- 会员等级系统（普通/黄金/钻石）
- 积分系统
- 实名认证与驾照认证
- 个人信息管理
- 常用地址管理

#### 2. 车辆预订模块
- 智能首页推荐
- 车辆分类浏览（经济型/舒适型/SUV/商务/豪华/新能源/MPV/跑车）
- 多维度筛选（价格/座位数/变速箱/能源类型）
- 车辆详情展示
- 实时库存查询
- 取还车方式选择（到店/送车上门）
- 附加服务（保险/儿童座椅/导航等）

#### 3. 订单管理模块
- 在线下单
- 订单状态跟踪
- 验车记录
- 费用明细
- 售后服务（退款/投诉/理赔）
- 订单评价

#### 4. 营销活动模块
- 优惠券领取使用
- 会员专享特权
- 积分兑换
- 促销活动

### 管理端功能

#### 1. 仪表盘
- 实时业务数据
- 经营分析图表
- 异常预警

#### 2. 车辆管理
- 车辆档案管理
- 库存管理
- 价格管理（日历价/节假日价）
- 车辆分类

#### 3. 订单管理
- 订单审核处理
- 取还车验车
- 订单查询导出
- 售后工单

#### 4. 会员管理
- 会员信息查询
- 等级管理
- 风控规则配置

#### 5. 营销管理
- 活动创建
- 优惠券管理
- 广告位管理

#### 6. 系统管理
- 权限管理
- 门店设置
- 系统配置
- 日志管理

## 数据库设计

### 核心表结构

1. **users** - 用户表（扩展字段：会员等级、积分、实名认证等）
2. **vehicles** - 车辆表
3. **vehicle_categories** - 车辆分类表
4. **vehicle_pricing** - 价格表
5. **stores** - 门店表
6. **orders** - 订单表
7. **order_addons** - 订单附加服务表
8. **payments** - 支付流水表
9. **coupons** - 优惠券表
10. **user_coupons** - 用户优惠券表
11. **promotions** - 营销活动表
12. **inspections** - 验车记录表
13. **reviews** - 评价表
14. **service_requests** - 售后工单表
15. **system_logs** - 系统日志表
16. **announcements** - 公告表
17. **shares** - 分享记录表
18. **point_logs** - 积分记录表
19. **member_levels** - 会员等级配置表
20. **user_addresses** - 用户地址表

详细的数据库结构定义见 `src/main/resources/schema.sql`

## 项目结构

```
bigdata/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       ├── carrental/          # 租车系统核心代码
│   │   │       │   ├── core/           # 核心类（数据库初始化等）
│   │   │       │   ├── web/            # Servlet层（API接口）
│   │   │       │   └── service/        # 业务逻辑层
│   │   │       └── hdfsdrive/          # 原有HDFS云盘代码（保留）
│   │   ├── resources/
│   │   │   └── schema.sql              # 数据库结构
│   │   └── webapp/
│   │       ├── WEB-INF/
│   │       │   └── web.xml
│   │       ├── dist/                   # 前端构建输出目录
│   │       └── (原有静态资源)
│   └── test/                           # 测试代码
├── frontend/                           # Vue.js前端项目
│   ├── src/
│   │   ├── components/                 # 可复用组件
│   │   ├── views/                      # 页面组件
│   │   │   ├── Home.vue                # 首页
│   │   │   ├── Login.vue               # 登录注册
│   │   │   ├── Vehicles.vue            # 车辆列表
│   │   │   ├── VehicleDetail.vue       # 车辆详情
│   │   │   ├── Booking.vue             # 预订页面
│   │   │   ├── Orders.vue              # 订单列表
│   │   │   ├── UserCenter.vue          # 用户中心
│   │   │   └── admin/                  # 管理后台页面
│   │   │       └── Dashboard.vue
│   │   ├── router/                     # 路由配置
│   │   ├── stores/                     # 状态管理
│   │   ├── api/                        # API封装
│   │   ├── utils/                      # 工具函数
│   │   ├── App.vue                     # 根组件
│   │   └── main.js                     # 入口文件
│   ├── index.html
│   ├── package.json
│   └── vite.config.js
├── pom.xml
└── README.md
```

## 安装与部署

### 1. 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8.x
- Node.js 18+ (用于前端开发)
- Tomcat 11

### 2. 数据库配置

创建数据库：

```sql
CREATE DATABASE bigdata CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

数据库连接配置在 `com.carrental.core.DatabaseInitializer` 中：

```java
private static final String JDBC_URL = "jdbc:mysql://localhost:3306/bigdata?...";
private static final String JDBC_USER = "root";
private static final String JDBC_PASS = "123456";
```

系统启动时会自动执行 `schema.sql` 初始化数据库结构。

### 3. 后端构建

```bash
# 进入项目根目录
cd /home/runner/work/bigdata/bigdata

# 清理并构建
mvn clean package

# 生成的WAR包位于 target/moudle02_hdfs-1.0-SNAPSHOT.war
```

### 4. 前端构建

```bash
# 进入前端目录
cd frontend

# 安装依赖
npm install

# 开发模式（带热重载）
npm run dev

# 生产构建（输出到 ../src/main/webapp/dist/）
npm run build
```

### 5. 部署到Tomcat

```bash
# 复制WAR包到Tomcat
cp target/moudle02_hdfs-1.0-SNAPSHOT.war $TOMCAT_HOME/webapps/

# 启动Tomcat
$TOMCAT_HOME/bin/startup.sh

# 访问系统
# 用户前台: http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/
# 管理后台: http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/#/admin
```

## API接口文档

### 用户认证

#### POST /api/auth
用户登录/注册

**参数：**
```json
{
  "action": "login|register",
  "username": "string",
  "password": "string",
  "phone": "string (可选)",
  "email": "string (可选)"
}
```

**返回：**
```json
{
  "success": true,
  "user": {
    "id": 1,
    "username": "testuser"
  }
}
```

### 车辆管理

#### GET /api/vehicle
获取车辆列表

**参数：**
- `category`: 车辆分类ID（可选）
- `store`: 门店ID（可选）
- `status`: 车辆状态（可选，默认AVAILABLE）

**返回：**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "vehicleCode": "V001",
      "brand": "丰田",
      "model": "凯美瑞",
      "dailyPrice": 200,
      "deposit": 1000,
      ...
    }
  ],
  "total": 10
}
```

#### GET /api/vehicle/{id}
获取车辆详情

#### POST /api/vehicle
创建车辆（需管理员权限）

### 订单管理

#### GET /api/order
获取用户订单列表（需登录）

**参数：**
- `status`: 订单状态筛选（可选）

#### GET /api/order/{id}
获取订单详情（需登录）

#### POST /api/order
创建订单（需登录）

**请求体：**
```json
{
  "vehicleId": 1,
  "pickupTime": "2024-01-01T10:00:00",
  "returnTime": "2024-01-05T10:00:00",
  "pickupType": "到店取车",
  "returnType": "到店还车",
  "pickupStoreId": 1,
  "returnStoreId": 1,
  "storeId": 1,
  "remark": "航班号XXX"
}
```

## 业务规则

### 1. 订单流程

```
下单 → 资质校验 → 支付押金/租金 → 取车验车 → 用车中 → 还车验车 → 结算 → 完成
```

### 2. 价格计算

- 基础租金 = 日租金 × 租期天数
- 服务费 = 根据取还车方式计算
- 保险费 = 根据保险套餐计算
- 优惠折扣 = 根据会员等级和优惠券计算
- 总金额 = 基础租金 + 服务费 + 保险费 + 附加费 - 优惠折扣

### 3. 押金策略

- 根据车型、用户信用等级动态计算
- 会员可享受押金减免
- 支持信用免押（需实名认证）

### 4. 会员等级

| 等级 | 条件 | 权益 |
|------|------|------|
| 普通会员 | 注册即享 | 基础租车服务 |
| 黄金会员 | 累计消费5000元 | 95折优惠，优先取车 |
| 钻石会员 | 累计消费20000元 | 9折优惠，免押金，VIP通道 |

### 5. 库存锁定

- 用户下单后锁定车辆库存
- 未支付订单15分钟后自动释放
- 支付完成后锁定至还车日期

## 开发指南

### 添加新的Servlet

```java
package com.carrental.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;

@WebServlet(name = "MyServlet", urlPatterns = {"/api/my-endpoint"})
public class MyServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        // 处理GET请求
    }
}
```

### 添加Vue页面

1. 在 `frontend/src/views/` 创建 `.vue` 文件
2. 在 `frontend/src/router/index.js` 添加路由配置
3. 使用Axios调用后端API

## 安全建议

⚠️ **生产环境注意事项：**

1. **密码加密**：当前示例使用明文存储，生产环境必须使用BCrypt等加密算法
2. **HTTPS**：启用SSL/TLS保护数据传输
3. **SQL注入**：已使用PreparedStatement防护，继续保持
4. **XSS防护**：前端输入需要验证和转义
5. **CSRF防护**：添加CSRF令牌验证
6. **会话管理**：配置安全的session参数
7. **API限流**：防止恶意请求
8. **敏感数据**：数据库密码等应使用环境变量或配置中心

## 测试

```bash
# 后端单元测试
mvn test

# 前端测试（如有配置）
cd frontend
npm test
```

## 性能优化

1. 启用数据库连接池（已使用Druid）
2. 添加Redis缓存热门数据
3. 前端资源CDN加速
4. 图片懒加载和压缩
5. 数据库索引优化
6. SQL查询优化

## 常见问题

### Q: 启动时数据库连接失败？
A: 检查MySQL服务是否启动，数据库连接配置是否正确。

### Q: 前端页面无法访问后端API？
A: 检查Vite代理配置（vite.config.js）和Tomcat端口。

### Q: 订单创建失败提示"未登录"？
A: 确保已通过 `/api/auth` 登录并且session有效。

### Q: 车辆图片无法显示？
A: 当前使用占位符，生产环境需要配置图片上传和存储服务。

## 后续规划

- [ ] 支付接口集成（支付宝/微信支付）
- [ ] 短信验证码服务
- [ ] 实名认证接口对接
- [ ] 地图导航集成
- [ ] 移动端适配优化
- [ ] 小程序版本
- [ ] 消息推送系统
- [ ] 数据分析报表
- [ ] AI客服
- [ ] 区块链存证

## 许可证

MIT License

## 联系方式

项目地址：https://github.com/21cimu/bigdata

---

**感谢使用租车系统！**
