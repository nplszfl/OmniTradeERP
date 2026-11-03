#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""巡检「雪花 ID 精度丢失」型缺陷。

本项目已确认发作过的（详见 MEMORY.md 2026-11-03）：
  **后端用雪花算法生成 19 位 Long 主键，前端用 JS Number 承载 → 精度丢失。**

  JS 的 Number 只能精确表示 2^53-1 = 9007199254740991（16 位），
  19 位数字在 `JSON.parse` 之后必然被截断：

      后端返回 : 2117546519450398722
      JS 解析 : 2117546519450398700      ← 差 22

  前端把丢失精度的 id 原样发回后端 → 后端匹配不到任何行
  → **HTTP 200，但删除/更新静默失败**（UI 提示成功，数据纹丝不动）。

修复方式：后端把 id 序列化成字符串（erp-common 的 CommonJacksonAutoConfiguration），
前端类型同步改成 string。

本脚本守住两端：
  1. 后端自动装配必须在册（否则所有服务的 id 又变回数字）
  2. 前端不允许再出现 `id: number` / `xxxId: number`（本项目实体 id 全是 Long）

用法：python3 scripts/check-id-precision.py
退出码：0 = 干净，1 = 有问题
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WEB = os.path.join(ROOT, 'erp-web', 'src')

# 后端自动装配清单
IMPORTS = os.path.join(
    ROOT, 'erp-common', 'src', 'main', 'resources', 'META-INF', 'spring',
    'org.springframework.boot.autoconfigure.AutoConfiguration.imports')
JACKSON_CONFIG = 'com.crossborder.erp.common.config.CommonJacksonAutoConfiguration'

# 前端：id 样字段被声明成 number
#   `\bid\s*:\s*number`  —— 主键 id
#   `\b[A-Za-z_]+Id\s*:\s*number` —— 外键 customerId / orderId ...
#   可选写法 `id?: number`
ID_NUMBER_RE = re.compile(r'\b(?:[A-Za-z_]+Id\??|id\??)\s*:\s*number\b')
# 明确豁免：这些是计数/金额，不是主键
EXEMPT_NAMES = {'totalPaid', 'paidAmount', 'totalAmount'}

SKIP_DIRS = {'node_modules', 'dist', '.git', 'target'}


def iter_ts_vue():
    for dirpath, dirnames, filenames in os.walk(WEB):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for fn in filenames:
            if fn.endswith(('.ts', '.vue')):
                yield os.path.join(dirpath, fn)


def main():
    problems = []

    # ---- 1. 后端自动装配必须在册 ----
    if not os.path.exists(IMPORTS):
        problems.append(('后端', 'AutoConfiguration.imports 不存在: ' + IMPORTS))
    else:
        content = open(IMPORTS, encoding='utf-8').read()
        if JACKSON_CONFIG not in content:
            problems.append((
                '后端',
                'CommonJacksonAutoConfiguration 没在自动装配清单里 → '
                '所有服务的 Long id 会退回数字，前端再次精度丢失'))

    # ---- 2. 自动装配类必须存在 ----
    cfg = os.path.join(ROOT, 'erp-common', 'src', 'main', 'java',
                       'com', 'crossborder', 'erp', 'common', 'config',
                       'CommonJacksonAutoConfiguration.java')
    if not os.path.exists(cfg):
        problems.append(('后端', 'CommonJacksonAutoConfiguration.java 不存在'))

    # ---- 3. 后端：Controller 不许直接返回裸 Long（根级标量不走 BeanSerializerModifier）----
    ctrl_hits = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS | {'erp-web'}]
        for fn in filenames:
            if not fn.endswith('Controller.java'):
                continue
            path = os.path.join(dirpath, fn)
            text = open(path, encoding='utf-8', errors='ignore').read()
            # 只看「方法签名返回 Long/long」且方法名含 create/新增
            for m in re.finditer(
                    r'public\s+(?:Long|long)\s+(\w+)\s*\(', text):
                name = m.group(1)
                if not re.search(r'create|add|save|insert|new', name, re.I):
                    continue
                lineno = text[:m.start()].count('\n') + 1
                rel = os.path.relpath(path, ROOT)
                ctrl_hits.append((rel, lineno, name))
    for rel, lineno, name in ctrl_hits:
        problems.append((
            '后端',
            f'{rel}:{lineno}  `{name}` 直接返回裸 Long —— '
            f'根级标量不走 BeanSerializerModifier，JS 解析必丢精度。'
            f'应改成 String（服务层仍用 Long，只在 HTTP 出口转字符串）'))

    # ---- 4. 后端：Result<Long> 的 create 接口应改用 Result.successId ----
    result_hits = []
    for dirpath, dirnames, filenames in os.walk(ROOT):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS | {'erp-web'}]
        for fn in filenames:
            if not fn.endswith('Controller.java'):
                continue
            path = os.path.join(dirpath, fn)
            text = open(path, encoding='utf-8', errors='ignore').read()
            for m in re.finditer(r'public\s+Result<Long>\s+(\w+)\s*\(', text):
                name = m.group(1)
                if not re.search(r'create|add|save|insert|new', name, re.I):
                    continue
                lineno = text[:m.start()].count('\n') + 1
                rel = os.path.relpath(path, ROOT)
                result_hits.append((rel, lineno, name))
    for rel, lineno, name in result_hits:
        problems.append((
            '后端',
            f'{rel}:{lineno}  `{name}` 返回 Result<Long> —— '
            f'Result.data 静态类型是 Object（泛型擦除），Jackson 分不出 id/计数。'
            f'应改用 Result.successId(...)'))

    # ---- 5. 前端不允许再出现 number 类型的 id ----
    hits = []
    for path in iter_ts_vue():
        src = open(path, encoding='utf-8', errors='ignore').read()
        for m in ID_NUMBER_RE.finditer(src):
            name = m.group(0).split(':')[0].strip().rstrip('?')
            if name in EXEMPT_NAMES:
                continue
            lineno = src[:m.start()].count('\n') + 1
            hits.append((os.path.relpath(path, ROOT), lineno, m.group(0).strip()))
    for rel, lineno, text in hits:
        problems.append(('前端', f'{rel}:{lineno}  `{text}` —— Long 型 id 必须是 string'))

    if not problems:
        print('✅ 未发现雪花 ID 精度丢失风险')
        print('   （后端自动装配在册；前端 id 类型统一为 string）')
        return 0

    print(f'⚠️  发现 {len(problems)} 处问题：')
    for scope, detail in problems:
        print(f'   [{scope}] {detail}')
    print()
    print('提示：后端把 id 序列化成字符串后，前端必须用 string 承载，')
    print('     否则 JS Number 又会把 19 位 id 截断 → 删除/更新静默失败。')
    return 1


if __name__ == '__main__':
    sys.exit(main())
