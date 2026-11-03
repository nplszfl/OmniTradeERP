#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""巡检「Map<String, T> 注入的 key 语义」缺陷。

本项目已确认发作过的（详见 MEMORY.md 2026-11-03）：
  **Spring 注入 `Map<String, T>` 时，key 是 bean 名，不是业务 key。**

  原代码三处注入 `Map<String, PlatformOrderSync> platformSyncMap`，
  却用 `platformSyncMap.get(platform.toLowerCase())` 查 —— 平台码是
  `amazon`，bean 名是 `amazonOrderSync` → **永远 null**。
  后果：手动同步 / 测试连接 / 定时同步三个入口全部失效，且大部分是静默的。

  📌 单测全绿的原因：测试里**手工** `map.put("amazon", mock)`，
  人工修正了 Spring 的真实行为。

本脚本扫「注入了 Map<String,T> 但代码里没有对应 bean 定义」的注入点，
以及「用非 bean 名做 key 去 get」的查表点。

用法：python3 scripts/check-map-injection-keys.py
退出码：0 = 干净，1 = 有可疑项
"""
import os
import re
import sys

SKIP_DIRS = {'target', 'node_modules', '.git', 'dist', 'build', 'out'}

# 注入 Map<String, X> 的字段声明
INJECT_RE = re.compile(
    r'private\s+(?:final\s+)?Map<\s*String\s*,\s*([A-Za-z0-9_<>,\s\.]+?)\s*>\s+(\w+)\s*;')

# 只有在 Spring 管理的类（@Component/@Service/@Configuration/@RestController）
# 里，Map<String,T> 字段才可能是「注入的 Map」；DTO 里的 Map 是普通数据字段。
SPRING_COMPONENT_RE = re.compile(
    r'@(Component|Service|Configuration|RestController|Repository|Controller)\b')

# 嵌套类（static class / 内部 DTO）里的 Map 是普通数据字段
NESTED_CLASS_RE = re.compile(r'\b(?:static\s+)?class\s+\w+')

# 定义 Map<String, X> bean 的方法
BEAN_RE = re.compile(r'@Bean[\s\S]{0,200}?Map<\s*String\s*,')

# 查表：xxxMap.get("literal") 或 .get(someVar.toLowerCase())
GET_LITERAL_RE = re.compile(r'(\w*[Mm]ap\w*)\.get\(\s*"([^"]+)"\s*\)')
GET_LOWER_RE = re.compile(r'(\w*[Mm]ap\w*)\.get\(\s*(\w+)\.toLowerCase\(\)\s*\)')


def java_files(root):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith('.java'):
                yield os.path.join(dirpath, fn)


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    suspicious = []

    for path in java_files(root):
        rel = os.path.relpath(path, root)
        with open(path, encoding='utf-8', errors='ignore') as f:
            src = f.read()
        lines = src.split('\n')

        # 不是 Spring 组件 → DTO / 内部类，Map 就是普通数据字段，跳过
        if not SPRING_COMPONENT_RE.search(src):
            continue
        # 文件里有 @Bean Map 定义 → key 由自己定义，不算风险
        if BEAN_RE.search(src):
            continue

        # 统计嵌套类数量：若 Map 字段出现在嵌套类里 → 跳过
        nested_count = len(NESTED_CLASS_RE.findall(src))

        for i, line in enumerate(lines):
            m = INJECT_RE.search(line)
            if not m:
                continue
            # 出现在嵌套类之后 → 是嵌套类的字段
            if nested_count > 1:
                prefix = '\n'.join(lines[:i])
                if len(NESTED_CLASS_RE.findall(prefix)) >= 1:
                    continue
            field = m.group(2)
            # 只有「看起来是注入的」（非 new 出来的）才算
            # new HashMap<> 这类在下一行或同行声明
            if re.search(r'=\s*new\s+\w+', line):
                continue
            # 找赋值来源：constructor param / field init
            has_bean_def = False
            suspicious.append({
                'file': rel,
                'line': i + 1,
                'field': field,
                'type': m.group(1).strip(),
                'has_bean_def': has_bean_def,
                'text': line.strip(),
            })

    if not suspicious:
        print("✅ 未发现 Map<String,T> 注入点")
        return 0

    print("⚠️  发现 %d 处 Map<String,T> 注入点，请逐个确认 key 语义：" % len(suspicious))
    for s in suspicious:
        flag = "" if s['has_bean_def'] else "  ⚠️ 全项目找不到对应的 @Bean Map 定义 → key 必然是 bean 名！"
        print("── %s:%d%s" % (s['file'], s['line'], flag))
        print("   %s" % s['text'])
        # 顺带报出该 map 的查表点
        with open(os.path.join(root, s['file']), encoding='utf-8', errors='ignore') as f:
            src = f.read()
        for gm in GET_LITERAL_RE.finditer(src):
            if gm.group(1) == s['field']:
                print("   → 查表: %s.get(\"%s\")" % (gm.group(1), gm.group(2)))
        for gm in GET_LOWER_RE.finditer(src):
            if gm.group(1) == s['field']:
                print("   → 查表: %s.get(%s.toLowerCase())  ⚠️ 业务 key 不是 bean 名！" % gm.groups())

    return 1


if __name__ == '__main__':
    sys.exit(main())
