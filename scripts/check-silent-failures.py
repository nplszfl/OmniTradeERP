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

📌 **判据覆盖面**（2026-11-10 扩）：「返零值」现在认全了所有等价写法 ——
   `0` / `0L` / `0.0` / `0.0f` / `false` / `""` / `null` /
   `Collections.emptyList/Set/Map()` / `new ArrayList<>` 等 /
   `List.of()` / `Set.of()` / `Map.of()` / `Optional.empty()`。
   ⚠️ 之前只认 `Collections.emptyList()` / `Map.of()`，**漏掉 `List.of()`** ——
   而全仓有 **10 处** `catch` → `return List.of()`（AnalyticsRepository）→ **完全失明**。
   → 这就是规则 100：「同一个 API 的等价写法必须一起认」，**不对称 = 双向失明**。

📌 **「不提异常」才算吞**（规则 12 的本质）：catch 里写了 log.warn / log.error
   → 异常**有交代**，降级是有意为之（规则 11 / 43），不算静默失败。
   ⚠️ 只看「返零值」会误伤一整类合法代码 → **假阳性比假阴性危险**（规则 99）。
📌 剩下的「预期失败路径」用 `BENIGN_CATCH_OWNERS` **显式白名单 + 逐条写清理由** ——
   判据无法从「catch 里返零值」推断出「这个失败是不是预期的」。

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
    # 📌 2026-11-14 14:56 补：文本 → 数字 的解析型 helper。
    #    `catch (NumberFormatException) { return null; }` 在这类方法里是
    #    **正确语义**（「这段文本里没有这个数字」→ null），不是静默失败。
    #    - toBigDecimal：erp-pricing 的竞品价格解析、erp-order 的统计解析（两处都是纯转换）
    #    - parseReviewCount：从评论文本里抽数字，抽不到就是 null
    'toBigDecimal', 'parseReviewCount',
}

# 📌 **「预期失败路径」白名单**（规则 12 / 99）
#
#   有些 catch 捕获的异常是**这个方法本来就在等的** —— 比如校验 token 有效性，
#   签名不对就**应该**返回 false，而不是抛出去。这类代码不是「吞异常」。
#
#   ⚠️ 为什么要单独列：判据无法从「catch 里返零值」推断出「这个失败是不是预期的」
#      —— 判据太宽会误伤一整类合法代码（假阳性比假阴性危险，规则 99）。
#      → 只能**显式白名单 + 逐条写清理由**。
BENIGN_CATCH_OWNERS = {
    'validateToken': 'JWT 签名/过期校验失败本就应返回 false，不是静默失败',
    'convertToEntityAttribute': '脏数据降级成空列表（注释已说明「不该让整个查询失败」）',
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
# 📌 取出 catch 的**异常类型**（CATCH_PAT 只抓了变量名）
CATCH_TYPE_PAT = re.compile(r'catch\s*\(\s*(?:([\w\.]+)\s+)?(\w+)\s*\)')


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
            # 📌 **白名单只对「窄类型 catch」生效**（2026-11-14 14:56 补）
            #    白名单是按**方法名**豁免的 → 那个方法以后把 catch 改成
            #    `catch (Exception e)` 也会被**一并放过** → **真·吞异常被豁免掉了**（实测）。
            #    ✅ 收紧：只有 catch 的是**具体异常类型**（NumberFormatException 等）时才豁免；
            #    catch 的是 Exception / RuntimeException / Throwable → **不豁免**。
            tm = CATCH_TYPE_PAT.match(src, m.start())
            exc_type = tm.group(1) if tm else None
            broad = (exc_type is None
                     or exc_type.split('.')[-1] in ('Exception', 'RuntimeException',
                                                   'Throwable', 'Error'))
            if not broad:
                continue
        # 📌 预期失败路径豁免（catch 里的异常是这个方法本来就在等的）
        if owner in BENIGN_CATCH_OWNERS:
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
            r'return\s+(0[Ll]?\b|0\.0[fFdD]?\b|false\b|""|null\b'
            r'|Collections\.empty(?:List|Set|Map)\(\)'
            r'|new\s+(?:ArrayList|HashMap|HashSet|LinkedList|LinkedHashMap|LinkedHashSet)<'
            r'|(?:List|Set|Map)\.of\(\)|Optional\.empty\(\))', joined)
        # 📌 **「不提异常」才算吞**（规则 12 的本质）：catch 里写了 log.warn / log.error
        #    → 异常**有交代**，降级是有意为之（规则 11 / 43），不算静默失败。
        #    ⚠️ 只看「返零值」会误伤一整类合法代码：
        #      · JwtUtil.validateToken → catch → return false（签名校验失败本就该返 false）
        #      · StringListJsonConverter → 脏数据降级成空列表（注释已说明）
        #    → 假阳性比假阴性更危险（规则 99）：它会逼下一个人去**放松闸门**。
        mentions_ex = bool(re.search(r'\b(log|logger|LOG|Logger)\b', joined)) \
            or bool(re.search(r'\b' + re.escape(var) + r'\b', joined))
        if returns_zero and not uses_var and not mentions_ex:
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
