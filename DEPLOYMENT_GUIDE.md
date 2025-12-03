# 租车系统部署指南 - Car Rental System Deployment Guide

## 快速开始

### 系统要求

- **JDK 21** (必须，不支持Java 8/11/17)
- **Apache Tomcat 11** (与Jakarta EE 6.1.0兼容)
- **MySQL 8.x**
- **Maven 3.9+**
- **Node.js 18+** (仅用于前端开发)
- **操作系统**: Linux, Windows, macOS均可

### 1. 数据库准备

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE bigdata CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 退出MySQL（数据库表会自动创建）
exit;
```

**注意：** 不需要手动导入SQL文件。应用启动时会自动执行 `src/main/resources/schema.sql` 初始化所有20个表。

### 2. 配置数据库连接

编辑 `src/main/java/com/carrental/core/DatabaseInitializer.java`:

```java
private static final String JDBC_URL = "jdbc:mysql://localhost:3306/bigdata?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowMultiQueries=true";
private static final String JDBC_USER = "root";
private static final String JDBC_PASS = "123456";  // 修改为你的密码
```

### 3. 后端构建

⚠️ **重要：必须使用Java 21**

```bash
# 进入项目目录
cd /path/to/bigdata

# 设置JAVA_HOME (Linux/Mac)
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
# 或 Windows
set JAVA_HOME=C:\Program Files\Java\jdk-21

# 清理并编译
mvn clean compile

# 打包成WAR
mvn package

# 生成的文件：target/moudle02_hdfs-1.0-SNAPSHOT.war
```

**构建失败排查：**
- 错误 "invalid target release: 21" → 使用Java 17或更老版本，请切换到Java 21
- 错误 "错误: 找不到或无法加载主类" → JAVA_HOME设置错误

### 4. 前端构建（可选）

如果需要修改前端代码：

```bash
# 进入前端目录
cd frontend

# 安装依赖（首次）
npm install

# 开发模式（热重载，端口5173）
npm run dev

# 生产构建（输出到 ../src/main/webapp/dist/）
npm run build
```

前端开发时访问 `http://localhost:5173`，后端API会自动代理到 `http://localhost:8080`。

### 5. 部署到Tomcat 11

#### 方式一：复制WAR包（推荐）

```bash
# 复制WAR到Tomcat webapps目录
cp target/moudle02_hdfs-1.0-SNAPSHOT.war $TOMCAT_HOME/webapps/

# 启动Tomcat
# Linux/Mac
$TOMCAT_HOME/bin/startup.sh

# Windows
%TOMCAT_HOME%\bin\startup.bat

# 查看日志
tail -f $TOMCAT_HOME/logs/catalina.out
```

#### 方式二：Tomcat Manager部署

1. 打开 `http://localhost:8080/manager`
2. 登录管理员账号
3. 上传 WAR 文件
4. 部署完成

### 6. 访问系统

部署成功后，访问以下地址：

- **用户前台首页**: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/`
- **车辆列表**: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/#/vehicles`
- **登录注册**: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/#/login`
- **管理后台**: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/#/admin`

**API接口端点：**
- 车辆API: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/api/vehicle`
- 订单API: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/api/order`
- 认证API: `http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/api/auth`

### 7. 验证部署

```bash
# 检查数据库表是否创建
mysql -u root -p bigdata -e "SHOW TABLES;"

# 应显示20个表：
# users, vehicles, vehicle_categories, stores, orders, 
# order_addons, payments, coupons, user_coupons, promotions,
# inspections, reviews, service_requests, system_logs,
# announcements, shares, point_logs, member_levels, 
# user_addresses, vehicle_pricing

# 测试API
curl http://localhost:8080/moudle02_hdfs-1.0-SNAPSHOT/api/vehicle
# 应返回JSON格式的车辆列表（可能为空）

# 检查会员等级初始化
mysql -u root -p bigdata -e "SELECT * FROM member_levels;"
# 应显示：普通会员(NORMAL), 黄金会员(GOLD), 钻石会员(DIAMOND)
```

## 生产环境部署

### 安全配置

1. **修改默认密码**
```java
// 修改 DatabaseInitializer.java
private static final String JDBC_PASS = "your-secure-password";
```

2. **启用HTTPS**
```xml
<!-- 编辑 $TOMCAT_HOME/conf/server.xml -->
<Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
           maxThreads="150" SSLEnabled="true">
    <SSLHostConfig>
        <Certificate certificateKeystoreFile="conf/localhost-rsa.jks"
                     type="RSA" />
    </SSLHostConfig>
</Connector>
```

3. **配置连接池大小**
```xml
<!-- 修改 pom.xml 中 Druid 配置或使用配置文件 -->
```

4. **设置JVM参数**
```bash
# 编辑 $TOMCAT_HOME/bin/setenv.sh (Linux/Mac)
export JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# Windows: setenv.bat
set JAVA_OPTS=-Xms2g -Xmx4g -XX:+UseG1GC
```

### 性能优化

1. **数据库索引** - 已在schema.sql中定义
2. **Tomcat线程池**
```xml
<!-- server.xml -->
<Connector port="8080" protocol="HTTP/1.1"
           maxThreads="200" minSpareThreads="25"
           connectionTimeout="20000"
           enableLookups="false" />
```

3. **前端CDN加速** - 将静态资源上传至CDN

### 监控和日志

```bash
# 实时查看Tomcat日志
tail -f $TOMCAT_HOME/logs/catalina.out

# 查看系统日志（记录在数据库）
mysql -u root -p bigdata -e "SELECT * FROM system_logs ORDER BY created_at DESC LIMIT 20;"

# 监控JVM
jconsole # 或使用 VisualVM

# 监控数据库连接
SHOW PROCESSLIST;
```

## Docker部署（可选）

创建 `Dockerfile`:

```dockerfile
FROM tomcat:11-jdk21-temurin

# 复制WAR文件
COPY target/moudle02_hdfs-1.0-SNAPSHOT.war /usr/local/tomcat/webapps/

# 暴露端口
EXPOSE 8080

# 启动Tomcat
CMD ["catalina.sh", "run"]
```

构建和运行：

```bash
# 构建镜像
docker build -t car-rental-system .

# 运行容器
docker run -d -p 8080:8080 \
  -e MYSQL_HOST=host.docker.internal \
  -e MYSQL_DATABASE=bigdata \
  -e MYSQL_USER=root \
  -e MYSQL_PASSWORD=123456 \
  --name car-rental \
  car-rental-system

# 查看日志
docker logs -f car-rental
```

使用Docker Compose (创建 `docker-compose.yml`):

```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: bigdata
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
      
  tomcat:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    environment:
      MYSQL_HOST: mysql
      MYSQL_DATABASE: bigdata
      MYSQL_USER: root
      MYSQL_PASSWORD: 123456

volumes:
  mysql-data:
```

启动：
```bash
docker-compose up -d
```

## 常见问题

### Q1: 启动时提示 "ClassNotFoundException: jakarta.servlet.http.HttpServlet"
**A:** Tomcat版本过低。必须使用Tomcat 11或支持Jakarta EE 10的容器。

### Q2: 前端页面空白
**A:** 检查前端是否已构建并输出到 `src/main/webapp/dist/`。运行 `npm run build`。

### Q3: API返回404
**A:** 检查WAR包是否正确部署，URL路径是否正确包含应用上下文 `/moudle02_hdfs-1.0-SNAPSHOT`。

### Q4: 数据库连接失败
**A:** 
- 检查MySQL是否运行: `systemctl status mysql`
- 检查连接参数是否正确
- 检查防火墙是否允许3306端口

### Q5: 订单创建时提示"未登录"
**A:** 需要先通过 `/api/auth?action=login` 登录获取session。使用浏览器自带的Cookie机制。

### Q6: 车辆图片无法显示
**A:** 当前使用占位符。生产环境需要实现图片上传功能并配置文件存储服务。

### Q7: Maven编译错误 "invalid target release: 21"
**A:** Java版本不对。确保使用Java 21：
```bash
java -version  # 应显示 "21.x.x"
export JAVA_HOME=/path/to/jdk-21
```

## 数据库迁移和备份

### 备份数据库

```bash
# 导出完整数据库
mysqldump -u root -p bigdata > backup_$(date +%Y%m%d).sql

# 仅导出结构
mysqldump -u root -p --no-data bigdata > schema_$(date +%Y%m%d).sql

# 仅导出数据
mysqldump -u root -p --no-create-info bigdata > data_$(date +%Y%m%d).sql
```

### 恢复数据库

```bash
# 从备份恢复
mysql -u root -p bigdata < backup_20241203.sql
```

### 重置数据库

```bash
# 删除并重建数据库（会丢失所有数据！）
mysql -u root -p -e "DROP DATABASE IF EXISTS bigdata; CREATE DATABASE bigdata CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 重启应用，schema.sql会自动执行
```

## 升级指南

### 从开发版升级到生产版

1. 修改数据库连接配置为生产环境
2. 启用HTTPS和安全配置
3. 配置生产级JVM参数
4. 设置日志级别和监控
5. 配置备份策略
6. 执行性能测试和压力测试

### 应用更新流程

1. 备份数据库和旧版本WAR包
2. 编译新版本：`mvn clean package`
3. 停止Tomcat
4. 替换WAR包
5. 清理缓存：`rm -rf $TOMCAT_HOME/work/Catalina/localhost/moudle02_hdfs-1.0-SNAPSHOT`
6. 启动Tomcat
7. 验证功能
8. 监控日志

## 技术支持

如有问题，请查阅：
- 项目README: `CAR_RENTAL_README.md`
- GitHub Issues: https://github.com/21cimu/bigdata/issues
- 数据库schema: `src/main/resources/schema.sql`

---

**部署愉快！**
