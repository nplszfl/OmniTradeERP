# ❓ 常见问题

## 安装问题

### Q: 编译失败，提示找不到依赖

**A:** 可能是 Maven 仓库问题，尝试：
```bash
# 清理本地仓库缓存
./mvnw clean

# 重新下载依赖
./mvnw dependency:go-offline

# 使用阿里云镜像（国内加速）
./mvnw clean package -Dmaven.repo.remote=https://maven.aliyun.com/repository/public
```

---

### Q: Docker 启动失败，提示端口被占用

**A:** 检查端口占用或修改 docker-compose 配置：
```bash
# 查看端口占用
lsof -i :8080
lsof -i :3306

# 修改 docker-compose.yml 中的端口映射
ports:
  - "8081:8080"  # 改为其他端口
```

---

### Q: JDK 版本不对

**A:** OmniTrade ERP 需要 JDK 21：
```bash
# 检查当前 Java 版本
java -version

# macOS 使用 jenv 管理多版本
brew install jenv
jenv add /usr/libexec/java_home -v 21
jenv global 21

# Linux 下使用 update-alternatives
sudo update-alternatives --config java
```

---

## 运行问题

### Q: 服务启动后无法访问

**A:** 按以下顺序排查：
1. 检查服务状态：`docker-compose ps`
2. 查看日志：`docker-compose logs -f erp-gateway`
3. 检查端口：`lsof -i :8080`
4. 防火墙设置：`sudo ufw allow 8080`

---

### Q: 数据库连接超时

**A:** 
1. 确认 MySQL 已启动：`docker-compose ps mysql`
2. 检查连接配置：
```bash
mysql -h localhost -P 3306 -u root -p
```
3. 查看连接数限制
4. 检查 Docker 网络配置

---

### Q: AI服务调用失败

**A:** 
1. 检查 AI 服务是否启动：`docker-compose ps | grep ai`
2. 检查 API Key 配置是否正确
3. 查看 AI 服务日志：`docker-compose logs erp-ai-assistant-service`
4. 确认网络能访问 AI 服务提供商

---

## 使用问题

### Q: 登录失败，提示认证错误

**A:** 
1. 检查 Nacos 配置中心是否正常
2. 确认 JWT 密钥配置正确
3. 清除浏览器缓存后重试
4. 检查 Redis 会话存储是否正常

---

### Q: 订单/商品数据无法保存

**A:** 
1. 检查数据库连接是否正常
2. 查看是否有字段校验失败（查看服务日志）
3. 确认必填字段都已填写
4. 检查数据格式是否符合要求

---

## 性能问题

### Q: 系统运行缓慢

**A:** 性能优化建议：
1. 启用 Redis 缓存
2. 检查数据库索引
3. 调整 JVM 参数：
```bash
# 在 docker-compose.yml 中添加
environment:
  - JAVA_OPTS=-Xmx2g -Xms2g -XX:+UseG1GC
```
4. 启用异步处理（配置 RocketMQ）

---

### Q: 内存使用过高

**A:** 
1. 限制 JVM 堆大小
2. 减少 Docker 服务数量（使用 lite 版本）
3. 清理日志文件
4. 检查是否有内存泄漏

---

## 其他问题

### Q: 如何获取帮助？

**A:** 
- 📖 查看 [Wiki文档](Home.md)
- 🐛 提交 [Issue](https://github.com/nplszfl/OmniTradeERP/issues)
- 📌 本仓库**没有开启 Discussions**（`has_discussions=false`）→ 旧的 `/discussions` 链接是 **410 死链**（GitHub API 返回 `Discussions are disabled for this repo`）。

---

### Q: 如何贡献代码？

**A:** 请阅读 [CONTRIBUTING.md](https://github.com/nplszfl/OmniTradeERP/blob/master/CONTRIBUTING.md)：
1. Fork 仓库
2. 创建特性分支
3. 提交代码
4. 创建 Pull Request

---

*找不到答案？[提交Issue](https://github.com/nplszfl/OmniTradeERP/issues/new)告诉我们！*