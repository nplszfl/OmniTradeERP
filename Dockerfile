# 基础镜像
FROM node:18-alpine AS builder

# 设置工作目录
WORKDIR /app

# 复制package.json和依赖
COPY erp-web/package*.json ./erp-web/

# 安装依赖
# 📌 **超时保护**（2026-11-10 21:15）：
#    `npm install` 拉 npm registry 时**网络挂起**（不是 502 快速失败 → 不触发重试）
#    → npm 默认无超时 → **无限等待** → docker build 挂到 runner 上限
#    → 这是 `docker/Dockerfile.service`（mvn 挂起卡死 CI 50 分钟）的**同款兄弟分支**，
#      只是命令从 mvn 换成了 npm（规则 107：同一缺陷的兄弟分支只修一条 = 白修）。
#    ✅ 三层：`timeout 600`（挂起也会被杀）+ 重试 3 次 + npm 自身的超时参数。
RUN cd erp-web && \
    attempt=1; \
    while [ "$attempt" -le 3 ]; do \
      if timeout 600 npm install \
           --fetch-timeout=120000 \
           --fetch-retries=2 \
           --fetch-retry-mintimeout=10000 \
           --fetch-retry-maxtimeout=60000; then break; fi; \
      echo "::warning::npm install 第 $attempt 次失败/超时（单次上限 600s），30s 后重试"; \
      attempt=$((attempt + 1)); \
      sleep 30; \
    done; \
    if [ "$attempt" -gt 3 ]; then \
      echo "::error::npm install 重试 3 次仍失败"; \
      exit 1; \
    fi

# 复制源代码
COPY erp-web ./erp-web/

# 构建前端
RUN cd erp-web && npm run build

# Nginx镜像
FROM nginx:alpine

# 复制构建产物到Nginx
COPY --from=builder /app/erp-web/dist /usr/share/nginx/html

# 复制Nginx配置
COPY docker/nginx.conf /etc/nginx/nginx.conf

# 暴露端口
EXPOSE 80

# 启动Nginx
CMD ["nginx", "-g", "daemon off;"]
