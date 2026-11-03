#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫「静默失败」型缺陷 —— 本项目反复踩的四类坑。

本项目已确认发作过的（详见 MEMORY.md）：
  1. **空壳方法**：`createInventoryCheck` 方法体只有一行 `log.info`，
     接口返回「成功」但什么都没做。
  2. **静默返回空**：`queryForecasts()` / `queryPlans()` 直接 `return null`，
     `GET /purchase/forecasts` 返回空数据，没有任何报错。
  3. **catch 吞异常**：catch 里 `return 0` / `return null` / 空集合，
     异常被完全吞掉 → 页面显示「全是 0」而**没有任何报错**。
     （analytics 的每个查询都这么干，单测全绿也发现不了）
  4. **占位符默认值**：`${VAR:-default}` → Spring 解析成 `-default`。

本脚本扫 1/2/3；4 由 check-placeholder-syntax.py 负责。

用法：python3 scripts/check-silent-failures.py
退出码：0 = 干净，1 = 有可疑项（需人工确认，不一定都是缺陷）
"""
import os
import re
import sys

SKIP_DIRS = {'target', 'node_modules', '.git', 'dist', 'build', 'test'}

# 正常的类型转换 fallback（不是缺陷）
BENIGN_RETURNERS = {
    'toLong', 'asLong', 'toInt', 'asInt', 'toDouble', 'parseDate',
    'parseDateTime', 'toLocationStatus', 'toWarehouseStatus',
}


def strip_comments(body):
    return '\n'.join(
        l.strip() for l in body.split('\n')
        if l.strip()
        and not l.strip().startswith('//')
        and not l.strip().startswith('*')
        and not l.strip().startswith('/*')
    )


def method_bodies(src, pattern):
    """返回 [(name, return_type, body_lines, lineno)]"""
    out = []
    for m in pattern.finditer(src):
        start = m.end()
        depth, i = 1, start
        while i < len(src) and depth:
            if src[i] == '{':
                depth += 1
            elif src[i] == '}':
                depth -= 1
            i += 1
        body = strip_comments(src[start:i - 1])
        out.append((m.group(2), m.group(1).strip(), body,
                    src[:m.start()].count('\n') + 1))
    return out


# 📌 修饰符要覆盖 private / static / final —— 否则 private 方法查不到归属，
#    白名单会「静默失效」（比误报更危险）。
METHOD_PAT = re.compile(
    r'(?:public|private|protected)\s+(?:static\s+)?(?:final\s+)?'
    r'([\w<>,\.\[\]\s]+?)\s+(\w+)\s*\([^)]*\)\s*\{')

CATCH_PAT = re.compile(r'catch\s*\(\s*(?:[\w\.]+\s+)?(\w+)\s*\)\s*\{')


def method_ranges(src):
    """返回 [(name, start_offset, end_offset)]，用于定位某段代码所属的方法。

    📌 为什么需要：规则 3（catch 吞异常）要判断「这个 catch 属于哪个方法」，
    才能套用 BENIGN_RETURNERS 白名单。按行号硬编码路径是 brittle 的 ——
    文件一改行号就失效，白名单会**静默失效**（比误报更危险）。
    """
    out = []
    for m in METHOD_PAT.finditer(src):
        start = m.end()
        depth, i = 1, start
        while i < len(src) and depth:
            if src[i] == '{':
                depth += 1
            elif src[i] == '}':
                depth -= 1
            i += 1
        out.append((m.group(2), start, i))
    return out


def enclosing_method(ranges, pos):
    """返回包含 pos 的最内层方法名（没有则 None）"""
    best = None
    for name, start, end in ranges:
        if start <= pos <= end:
            if best is None or (end - start) < best[1]:
                best = (name, end - start)
    return best[0] if best else None


def scan_file(path, findings):
    src = open(path, encoding='utf-8', errors='ignore').read()
    rel = path.replace('./', '')
    ranges = method_ranges(src)

    for name, ret, body, lineno in method_bodies(src, METHOD_PAT):
        lines = [l for l in body.split('\n') if l]
        if not lines:
            continue
        meaningful = [l for l in lines
                      if not re.match(r'^(log|logger|LOG)\.\w+\(', l)]

        # 1) 空壳：方法体只有日志
        if not meaningful and lines:
            findings.append(('空壳方法(只有日志)', rel, lineno, f'{ret} {name}()'))

        # 2) 静默返回空：方法体只有一句 return null
        if (len(meaningful) == 1
                and re.match(r'^return\s+null\s*;$', meaningful[0])
                and name not in BENIGN_RETURNERS):
            findings.append(('静默 return null', rel, lineno, f'{ret} {name}()'))

    # 3) catch 吞异常：不记录就返回零值
    for m in CATCH_PAT.finditer(src):
        var = m.group(1)
        # 📌 归属方法在白名单里 → 属类型转换 fallback（正常语义），跳过
        owner = enclosing_method(ranges, m.start())
        if owner in BENIGN_RETURNERS:
            continue
        start = m.end()
        depth, i = 1, start
        while i < len(src) and depth:
            if src[i] == '{':
                depth += 1
            elif src[i] == '}':
                depth -= 1
            i += 1
        body = strip_comments(src[start:i - 1])
        lines = [l for l in body.split('\n') if l]
        if not lines or len(lines) > 6:
            continue
        joined = ' '.join(lines)
        uses_var = re.search(r'\b' + re.escape(var) + r'\b', joined)
        returns_zero = re.search(
            r'return\s+(0\b|0\.0\b|""|null\b|Collections\.emptyList\(\)'
            r'|new\s+ArrayList<|Collections\.emptyMap\(\)|Map\.of\(\))', joined)
        if returns_zero and not uses_var:
            lineno = src[:m.start()].count('\n') + 1
            findings.append(('catch 吞异常返零值', rel, lineno, lines[-1][:70]))


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    findings = []
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if not fn.endswith('.java'):
                continue
            path = os.path.join(dirpath, fn)
            if os.sep + 'test' + os.sep in path:
                continue
            try:
                scan_file(path, findings)
            except Exception:
                pass

    if not findings:
        print('✅ 未发现静默失败型缺陷（空壳方法 / 静默 return null / catch 吞异常）')
        return 0

    print(f'⚠️  发现 {len(findings)} 处可疑项（需人工确认）：\n')
    by_kind = {}
    for kind, path, lineno, detail in findings:
        by_kind.setdefault(kind, []).append((path, lineno, detail))
    for kind, items in by_kind.items():
        print(f'── {kind} ({len(items)}) ──')
        for path, lineno, detail in items:
            print(f'   {path}:{lineno}  {detail}')
        print()
    print('提示：catch 里的类型转换 fallback（toLong/parseDate 等）是正常语义，')
    print('     不算缺陷；确认后可在 BENIGN_RETURNERS 里加白名单。')
    return 1


if __name__ == '__main__':
    sys.exit(main())
