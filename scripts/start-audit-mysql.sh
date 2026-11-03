#!/usr/bin/env bash
# 启动「审计用」MySQL 容器，供需要查**线上结构**的巡检脚本使用。
#
# 📌 **为什么要独立于 docker-compose**：
#    compose 里的 mysql 挂的是 database/*.sql，但容器名/端口/数据卷都不一样，
#    而巡检脚本通过 `docker exec <容器名> mysql ...` 取数
#    → 容器名必须固定 → 单独起一个，名字固定为 erp-fresh-mysql。
#
# 📌 **挂载规则与本地一致**（关键）：
#    docker-entrypoint-initdb.d 按**文件名字母序**执行（不是挂载顺序），
#    所以种子脚本必须用 `zz-` 前缀强制排到最后 ——
#    否则 user-service.sql（u）会排在 seed-data.sql（s）之后，
#    把 t_permission 重建空表 → **整棵 RBAC 权限树被清零**。
#
# 用法：
#   scripts/start-audit-mysql.sh [端口]     # 默认 3411
#   scripts/stop-audit-mysql.sh
set -euo pipefail

CONTAINER="${MYSQL_CONTAINER:-erp-fresh-mysql}"
PORT="${1:-3411}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SQL_DIR="$ROOT/database"

if [ ! -d "$SQL_DIR" ]; then
  echo "❌ 找不到 $SQL_DIR" >&2
  exit 1
fi

# 已存在就先清掉（保证是**全新初始化**，否则 init 脚本不会重跑）
if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER"; then
  echo "→ 移除旧的 $CONTAINER"
  docker rm -f "$CONTAINER" >/dev/null
fi

# 📌 逐个挂载 .sql —— 用 -v 而不是挂整个目录，
#    因为种子文件要改名成 zz- 前缀（字母序强制最后）
args=()
for f in "$SQL_DIR"/*.sql; do
  base="$(basename "$f")"
  case "$base" in
    seed-*) target="zz-${base}" ;;   # 种子排最后
    *)      target="$base" ;;
  esac
  args+=(-v "$f:/docker-entrypoint-initdb.d/$target:ro")
done

echo "→ 启动 MySQL 8.0（容器名 $CONTAINER，端口 $PORT）"
docker run -d \
  --name "$CONTAINER" \
  -e MYSQL_ROOT_PASSWORD=root \
  -e LANG=C.UTF-8 \
  -e LC_ALL=C.UTF-8 \
  -p "${PORT}:3306" \
  "${args[@]}" \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  >/dev/null

echo "→ 等待 MySQL 就绪并跑完 init 脚本（可能需要 1-2 分钟）"
ready=0
dbs=0
# 📌 **就绪信号 = docker-entrypoint 自己说「init 跑完了」**
#
#    原来这里等的是「SHOW DATABASES 的行数 >= 20」——
#    📌 **这是错的**，而且是个**必现的竞态**：
#    docker-entrypoint 按**文件名字母序**执行 init 脚本，
#    而 `erp_user` 的表（t_permission / t_role / t_user）**有两份 DDL**：
#    - `init.sql`（老 schema，**没有** component 列）—— 字母序第 1 批
#    - `user-service.sql`（新 schema，**有** component 列）—— 第 18 个
#    → 「库数量够 20」的时刻，可能正好落在两份 DDL **中间**
#    → 巡检脚本查到的是**老 schema** → 报「实体映射到列 component，
#      但线上表里没有这一列」→ **误报**
#
#    📌 这不是「表还没建」，而是「表建了，但是**旧版**的表」——
#       两份 DDL 谁生效，取决于**文件名字母序**（极其脆弱）。
#
#    📌 实测：CI 的 Schema Audit job 间歇性挂，报的正是
#       [erp_user.t_permission] 实体映射到列 `component`，但线上表里没有这一列
#       [erp_user.t_role]       实体映射到列 `sort_order`，但线上表里没有这一列
#       [erp_user.t_user]       实体映射到列 `last_login_ip`，但线上表里没有这一列
#       —— 这 3 个列在 user-service.sql 的 DDL 里**都有**，只是当时表还没建好。
#
#    ✅ 正确信号：官方镜像在**所有 init 脚本跑完后**会打这一行：
#       [Note] [Entrypoint]: MySQL init process done. Ready for start up.
#    📌 而且必须在 **MySQL 已经能接受连接之后**才有这行 → 两个条件都要满足。
for i in $(seq 1 90); do
  if docker exec "$CONTAINER" mysql -uroot -proot -e "SELECT 1" >/dev/null 2>&1; then
    if docker logs "$CONTAINER" 2>&1 | grep -q "MySQL init process done"; then
      dbs=$(docker exec "$CONTAINER" mysql -uroot -proot -e "SHOW DATABASES;" 2>/dev/null | wc -l)
      ready=1
      break
    fi
  fi
  sleep 2
done

if [ "$ready" -ne 1 ]; then
  echo "❌ 超时：MySQL 没能在 180 秒内就绪" >&2
  docker logs "$CONTAINER" 2>&1 | tail -30 >&2 || true
  exit 1
fi

# 📌 **init 报错 = 种子链被腰斩**（踩过 1 次才补上）
#
#    docker-entrypoint 遇到 .sql 里的报错会**中止整个 init**，
#    但**容器照样起来** → 看起来「初始化成功」，实际上排在后面的
#    种子文件（zz-seed-live-data / zz-seed-tenant / zz-seed-warehouse）
#    **一个都没执行** → 静默失败。
#
#    实测踩到过：zz-seed-data.sql 里 t_role 主键重复
#    → ERROR 1062 Duplicate entry '1' for key 't_role.PRIMARY'
#    → 后面 3 个种子文件全被跳过。
#
#    所以这里**必须扫日志**，不能只看「容器起来了、库建出来了」。
if docker logs "$CONTAINER" 2>&1 | grep -qE "^ERROR |ERROR [0-9]{4} "; then
  echo "❌ init 脚本里有报错（种子链可能被腰斩）：" >&2
  docker logs "$CONTAINER" 2>&1 | grep -E "^ERROR |ERROR [0-9]{4} " | head -20 >&2
  exit 1
fi

echo "✅ 就绪（$dbs 个库，init 日志无报错）"
exit 0
