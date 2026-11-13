# 📖 安装指南

## 前置环境

### 软件要求

| 软件 | 版本要求 | 说明 |
|------|---------|------|
| JDK | 21+ | 必须OpenJDK 21 |
| Maven | 3.9+ | 构建工具 |
| Node.js | 18+ | 前端开发 |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.0+ | 缓存/会话 |
| Docker | 24.0+ | 容器化部署 |

### 硬件要求（生产环境）

| 组件 | 最低配置 | 推荐配置 |
|------|---------|---------|
| CPU | 4核 | 8核+ |
| 内存 | 8GB | 16GB+ |
| 磁盘 | 100GB | 200GB+ SSD |

---

## 安装步骤

### 步骤1：安装基础软件

```bash
# macOS (使用 Homebrew)
brew install openjdk@21 maven node redis mysql

# Ubuntu/Debian
sudo apt update
sudo apt install openjdk-21-jdk maven nodejs npm redis-server mysql-server

# CentOS/RHEL
sudo yum install java-21-openjdk maven nodejs npm redis mysql-server
```

### 步骤2：克隆项目

```bash
git clone https://github.com/nplszfl/OmniTradeERP.git
cd OmniTradeERP
```

### 步骤3：初始化数据库

```bash
# 启动 MySQL
mysql -u root -p < database/init.sql

# 或使用 Docker 启动 MySQL
docker run -d --name omnitrade-mysql \
  -e MYSQL_ROOT_PASSWORD=your_password \
  -e MYSQL_DATABASE=omnitrade \
  -p 3306:3306 \
  mysql:8.0

# 等待 MySQL 启动后执行初始化脚本
docker exec -i omnitrade-mysql mysql -u root -p < database/init.sql
```

### 步骤4：配置修改

复制配置文件模板并修改：

```bash
# 后端配置
cp erp-common/src/main/resources/application.yml.example \
   erp-common/src/main/resources/application.yml

# 修改数据库连接等配置
vim erp-common/src/main/resources/application.yml
```

### 步骤5：编译项目

```bash
# 完整编译
./mvnw clean package -DskipTests

# 或使用脚本
./local-build.sh
```

### 步骤6：启动服务

```bash
# 方式一：Docker部署（推荐）
docker-compose -f docker-compose.yml up -d

# 方式二：本地启动
./start.sh

# 方式三：IDE启动
# 在 IDEA 中打开项目，运行 erp-gateway 模块的 Application 类
```

---

## 验证安装

### 检查服务状态

```bash
# 检查端口
curl http://localhost:8080/actuator/health

# 检查数据库连接
mysql -u root -p -e "USE omnitrade; SHOW TABLES;"

# 检查 Redis
redis-cli ping
```

### 访问服务

| 服务 | 地址 | 默认账号 |
|------|------|---------|
| API | http://localhost:8080 | - |
| 前端 | http://localhost:3000 | admin/admin123 |
| Nacos | http://localhost:8848 | nacos/nacos |

---

## 常见问题

### Q: JDK 版本不对？
**A:** OmniTrade ERP 需要 JDK 21。请确认：
```bash
java -version  # 应该是 21.x
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

### Q: MySQL 连接失败？
**A:** 检查 MySQL 服务状态和密码：
```bash
mysql -u root -p  # 测试连接
# 检查是否允许 localhost 连接
```

### Q: 端口被占用？
**A:** 查看并关闭占用端口的进程：
```bash
lsof -i :8080
# 或修改 docker-compose.yml 映射到其他端口
```

---

## 下一步

- ✅ 完成安装 → [快速开始](Quick-Start.md)
- 🏗️ 了解架构 → [系统架构](Architecture.md)
- 🤖 体验AI功能 → [AI功能介绍](AI-Features.md)