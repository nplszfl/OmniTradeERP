#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""检查 Spring 占位符默认值语法。

🔴 本项目踩过的坑：
   Spring 的占位符语法是 `${VAR:default}` —— **冒号**分隔。
   但很多人在 shell / docker-compose 里写惯了 `${VAR:-default}`，
   于是在 application.yml 里也这么写，结果默认值被解析成 `-8848`：

       server-addr: ${NACOS_HOST:xxx}:${NACOS_PORT:-8848}
                                              ↑ 实际变成 -8848

   报错长这样（极难联想到占位符）：
       java.net.URISyntaxException: Illegal character in port number
           at index 42: //mse-xxx-nacos-ans.mse.aliyuncs.com:-7848

   **为什么长期没人发现**：docker-compose.yml 里恰好设了 NACOS_PORT=8848
   环境变量 → 占位符走「有值」分支，语法错误被完全掩盖。
   只有在**本地直接 java -jar**（没设环境变量）时才会炸。

   本脚本扫描所有配置文件，出现 `${VAR:-default}` 形式即失败。

用法：python3 scripts/check-placeholder-syntax.py
退出码：0 = 全部合规，1 = 有问题
"""
import os
import re
import sys

# ${VAR:-default}  →  Spring 会把 default 解析成 "-default"
BAD = re.compile(r'\$\{([A-Za-z_][A-Za-z0-9_]*):-([^{}]*)\}')

# 只扫这些类型：yml / yaml / properties
EXTS = ('.yml', '.yaml', '.properties')

# 排除目录
SKIP_DIRS = {'target', 'node_modules', '.git', 'dist', 'build'}

# ${VAR:default} 是合规的，用于排除误报（如 http:// 里的 ${...}）
OK_SAMPLE = re.compile(r'\$\{[A-Za-z_][A-Za-z0-9_]*:[^{}]*\}')


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    bad = []
    checked = 0

    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if not fn.endswith(EXTS):
                continue
            path = os.path.join(dirpath, fn)
            try:
                with open(path, encoding='utf-8') as f:
                    lines = f.readlines()
            except (UnicodeDecodeError, OSError):
                continue
            checked += 1
            for lineno, line in enumerate(lines, 1):
                # 排除注释行
                stripped = line.strip()
                if stripped.startswith('#') or stripped.startswith('--'):
                    continue
                m = BAD.search(line)
                if m:
                    bad.append((os.path.relpath(path, root), lineno,
                                m.group(0), stripped[:110]))

    print(f"=== 扫描 {checked} 个配置文件 ===")
    if not bad:
        print("✅ 未发现 ${VAR:-default} 形式的占位符")
        print("   （Spring 语法应为 ${VAR:default}，冒号分隔）")
        return 0

    print(f"❌ 发现 {len(bad)} 处错误的占位符默认值语法：\n")
    for path, lineno, token, text in bad:
        print(f"  {path}:{lineno}")
        print(f"    {token}   ← 冒号前多了一个 '-'")
        print(f"    {text}")
    print()
    print("修复方式： ${VAR:-default}  →  ${VAR:default}")
    print("  （Spring 用冒号分隔默认值；'-' 是 shell/docker-compose 的写法）")
    print()
    print("⚠️ 为什么难发现：compose 里设了环境变量时会走「有值」分支，")
    print("   语法错误被掩盖；只有本地 java -jar 直接跑才炸。")
    return 1


if __name__ == '__main__':
    sys.exit(main())
