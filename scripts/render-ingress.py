#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 k8s 清单里的占位符渲染成真实值。

📌 **为什么单独抽成脚本**（2026-11-14 03:55 实测踩到的坑）：

原来渲染逻辑是**内嵌的 heredoc**：
- `k8s/deploy.sh` 里内嵌一份 → shell 文件，没问题
- `.github/workflows/ci-cd.yml` 里内嵌一份 → **heredoc 正文必须与 `run: |` 块同缩进**
  → 少缩进 1 空格 = **YAML 块标量提前结束** → `yaml.safe_load` 报
  `could not find expected ':'` → **整个 workflow 形同虚设**
  （GitHub 会直接拒绝那个文件 → 0 个 job、0 行日志）

✅ 所以：渲染逻辑**只有一份**，放在脚本里；两条路径（CI / deploy.sh）都调用它。

用法：
    python3 scripts/render-ingress.py <host> <源清单> <目标文件>

退出码：0 = 渲染成功；1 = 失败。
"""

import sys


def main() -> int:
    if len(sys.argv) != 4:
        print("用法：render-ingress.py <host> <源清单> <目标文件>")
        return 1
    host, src, dst = sys.argv[1], sys.argv[2], sys.argv[3]

    try:
        with open(src, encoding="utf-8") as f:
            text = f.read()
    except OSError as e:
        print(f"❌ 读不到源清单 {src}：{e}")
        return 1

    placeholder = "__INGRESS_HOST__"
    if placeholder not in text:
        print(f"❌ {src} 里找不到 {placeholder} 占位符（清单被改过？）")
        return 1

    if not host:
        print("❌ host 为空 —— 绝不能用空值渲染（空 host 会让 Ingress 匹配不到任何域名）")
        return 1

    try:
        with open(dst, "w", encoding="utf-8") as f:
            f.write(text.replace(placeholder, host))
    except OSError as e:
        print(f"❌ 写不了目标文件 {dst}：{e}")
        return 1

    print(f"  → Ingress host = {host}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
