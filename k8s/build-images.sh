#!/bin/bash
# 构建并推送所有 ERP 服务的 Docker 镜像
#
# 📌 **为什么改成从 k8s 清单反推**：
# 这个脚本原来只构建 **2 个** 镜像（gateway / order），
# 剩下 3 个还是 `# TODO: 添加 XXX Dockerfile` 的空壳，
# 而 k8s/*.yaml 引用了 **13 个** ERP 镜像
# → 另外 11 个镜像**从来没被构建过、也从来没被推送过**
# → 一旦真部署 → `ImagePullBackOff` → Pod 起不来。
#
# 📌 **和 CI 用同一套配方**：
# CI 的 docker-build job 用 `docker/Dockerfile.service` + `MODULE/PORT` build-arg，
# 这里用**完全相同**的调用方式 → 编排层只有一套配方，不会出现「本地能构建、CI 构建不了」。
#
# 📌 **镜像清单从 k8s/*.yaml 反推**（scripts/k8s-image-matrix.py）——
# 手写一份模块清单 = 多一个会漂移的事实来源。

set -euo pipefail

cd "$(dirname "$0")/.."

REGISTRY="${REGISTRY:-nplszfl}"
PUSH="${PUSH:-true}"          # PUSH=false 时只构建不推送（本地验证用）

echo "🔨 开始构建 Docker 镜像（镜像清单从 k8s/*.yaml 反推）..."

# 从 k8s 清单拿 module + port + image
PLAN="$(python3 scripts/k8s-image-matrix.py)"
if [ -z "$PLAN" ]; then
  echo "❌ 生成构建矩阵失败"
  exit 1
fi

# 用 python 解析 matrix，输出 module<TAB>port<TAB>image
# 📌 不用 f-string 里嵌转义引号 —— Python 3.12 以下的 f-string 不允许反斜杠
#    （GitHub runner 是 3.11，本地可能是 3.9 → 直接 SyntaxError）
ROWS="$(printf '%s' "$PLAN" | python3 -c '
import json, sys
d = json.load(sys.stdin)
for r in d.get("include", []):
    print("{}\t{}\t{}".format(r["module"], r["port"], r["image"]))
')"

count=0
while IFS=$'\t' read -r module port image; do
  [ -z "$module" ] && continue
  count=$((count + 1))
  echo ""
  echo "🔨 [$count] $module  (port=$port)  →  $image"

  docker build \
    -f docker/Dockerfile.service \
    --build-arg MODULE="$module" \
    --build-arg PORT="$port" \
    -t "$image:latest" \
    -t "$image:$(git rev-parse --short HEAD 2>/dev/null || echo latest)" \
    .

  if [ "$PUSH" = "true" ]; then
    docker push "$image:latest"
    docker push "$image:$(git rev-parse --short HEAD 2>/dev/null || echo latest)"
  else
    echo "   （PUSH=false → 只构建不推送）"
  fi
done <<< "$ROWS"

echo ""
echo "✅ 镜像构建完成！共 $count 个镜像"
echo ""
echo "📌 部署到集群（用同名脚本，会按 k8s 清单 apply）："
echo "   bash k8s/deploy.sh"
