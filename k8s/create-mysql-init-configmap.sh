#!/usr/bin/env bash
# 📌 **生成 K8s 的 `mysql-init-script` ConfigMap（数据库初始化 SQL）**
#
# 🔴 **为什么必须有这个脚本**（2026-11-19 实测定位）：
#   `k8s/mysql.yaml` 的 StatefulSet 挂了 ConfigMap `mysql-init-script`
#   → 但**全仓从未定义它**（`k8s/*.yaml` 里没有、也没有任何脚本 `kubectl create configmap` 它）
#   → Pod 卡在 `ContainerCreating`：`MountVolume.SetUp failed ... configmap "mysql-init-script" not found`
#   → `k8s/deploy.sh` 的 `kubectl wait --for=condition=ready pod -l app=mysql --timeout=300s`
#      **超时 300 秒** → 脚本有 `set -euo pipefail` → **整条部署链中止**
#   → Redis / Nacos / 所有业务服务**一个都部署不了**。
#   📌 **CI 抓不到这个**：CI 的 deploy step 因 `KUBE_CONFIG` 未配置而**整段跳过**（规则 101/146）。
#
# 📌 **为什么要序号前缀**（Docker init 的真实行为，规则 61）：
#   MySQL entrypoint **按文件名字母序**执行 `/docker-entrypoint-initdb.d/*`（不是 compose 挂载顺序）。
#   而 `database/` 的字母序是：
#     ... reporting-service.sql(14) → **seed-data(15) / seed-live-data(16) / seed-tenant(17) / seed-warehouse(18)**
#     → social-commerce(19) / supplier(20) / tenant(21) / user(22) / warehouse(23)
#   → **种子文件排在建表文件前面** → 种子引用还没建的表
#     （实测：`seed-live-data.sql` 要 `douyin_shop_config`（social-commerce 建）
#      · `seed-tenant.sql` 要 `tenants`（tenant-service 建）
#      · `seed-warehouse.sql` 要 `wms_*`（warehouse-service 建））
#   → init 遇到第一个错误就**中止** → 后面全不跑。
#   ✅ **修法**：**schema 先、seed 后**，用序号前缀强制字母序
#     `1xx-*.sql`（schema，按原字母序）→ `2xx-*.sql`（seed，按原字母序）
#     （`1xx` < `2xx` → 字母序天然满足）
#
# 📌 **单一真源**：SQL 内容直接取 `database/*.sql`，**不在这里复制一份**
#    → 改 schema 不用同步改这里（规则 33：手写清单会漂移）。
#
# 📌 **大小限制**：ConfigMap 上限 1MiB。当前 `database/*.sql` 合计 ≈ 400KB → 还有余量；
#    逼近上限时本脚本会**明确报错**（而不是让 kubectl 截断/失败）。
set -euo pipefail

# 📌 `--print-only`：只输出 ConfigMap YAML（**不 apply**）→ 本机无集群 / CI 闸门都能用
PRINT_ONLY=0
for arg in "$@"; do
  case "${arg}" in
    --print-only) PRINT_ONLY=1 ;;
    *) echo "未知参数: ${arg}" >&2; exit 2 ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DB_DIR="${REPO_ROOT}/database"
CM_NAME="mysql-init-script"
NAMESPACE="${NAMESPACE:-erp}"
MAX_BYTES=$((1024 * 1024))

if [ ! -d "${DB_DIR}" ]; then
  echo "❌ 找不到数据库脚本目录: ${DB_DIR}" >&2
  exit 1
fi

shopt -s nullglob
SQL_FILES=("${DB_DIR}"/*.sql)
if [ ${#SQL_FILES[@]} -eq 0 ]; then
  echo "❌ ${DB_DIR} 里一个 *.sql 都没有 → 建出来的库是空的" >&2
  exit 1
fi

# ---- 组装 --from-file 参数：**schema 先、seed 后**，各段内保持原字母序 ----
FROM_ARGS=()
idx=0
# ① schema（文件名不以 seed- 开头）
for f in $(printf '%s\n' "${SQL_FILES[@]}" | grep -vE '/seed-' | LC_ALL=C sort); do
  idx=$((idx + 1))
  FROM_ARGS+=(--from-file="$(printf '1%02d-%s' "${idx}" "$(basename "${f}")")=${f}")
done
schema_count=${idx}
# ② seed（文件名以 seed- 开头）—— 必须排在所有 schema 之后
idx=0
for f in $(printf '%s\n' "${SQL_FILES[@]}" | grep -E '/seed-' | LC_ALL=C sort); do
  idx=$((idx + 1))
  FROM_ARGS+=(--from-file="$(printf '2%02d-%s' "${idx}" "$(basename "${f}")")=${f}")
done
seed_count=${idx}

total_bytes=$(cat "${SQL_FILES[@]}" | wc -c | tr -d ' ')
if [ "${total_bytes}" -ge "${MAX_BYTES}" ]; then
  echo "❌ database/*.sql 合计 ${total_bytes} 字节 ≥ ConfigMap 上限 ${MAX_BYTES}" >&2
  echo "   → 不能再全塞进一个 ConfigMap；改成 Job/initContainer 分批执行" >&2
  exit 1
fi

echo "📦 生成 ConfigMap ${CM_NAME}：schema ${schema_count} 个 + seed ${seed_count} 个 = $((schema_count + seed_count)) 个 SQL（合计 ${total_bytes} 字节 / 上限 ${MAX_BYTES}）" >&2

if [ "${PRINT_ONLY}" -eq 1 ]; then
  # 📌 只打印 YAML（不连集群）→ 本机/闸门可验证
  kubectl -n "${NAMESPACE}" create configmap "${CM_NAME}" \
    "${FROM_ARGS[@]}" \
    --dry-run=client -o yaml
  exit 0
fi

kubectl -n "${NAMESPACE}" create configmap "${CM_NAME}" \
  "${FROM_ARGS[@]}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "✅ ConfigMap ${CM_NAME} 已创建（namespace=${NAMESPACE}）"
