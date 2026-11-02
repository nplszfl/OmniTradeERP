#!/usr/bin/env python3
"""把文档里的服务端口表对齐到 application.yml。

规则：某一行如果出现 erp-xxx-service 且只带一个 8000-8999 的四位数端口，
就把这个端口改成该服务在 application.yml 里的真实端口。

跳过的情况：
- 一行里有多个端口（历史变更记录、旧/新端口对照表）→ 人工确认
- 一行里出现多个 erp-xxx-service 且目标端口不一致（歧义）→ 人工确认

用法：python3 scripts/fix-doc-ports.py [--dry-run]
"""
import argparse
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PORT_RE = re.compile(r"(?<![\d.])(\d{4})(?![\d.])")


def load_ports():
    ports = {}
    for module in sorted(os.listdir(ROOT)):
        if not module.startswith("erp-"):
            continue
        path = os.path.join(ROOT, module, "src", "main", "resources", "application.yml")
        if not os.path.exists(path):
            continue
        txt = open(path, encoding="utf-8").read()
        m = re.search(r"\n  port:\s*(\d+)", txt)
        if m:
            ports[module] = int(m.group(1))
    return ports


def build_alias(ports):
    alias = {}
    for mod in ports:
        alias[mod] = mod
        alias[mod.replace("erp-", "")] = mod
        alias["erp-test-" + mod.replace("erp-", "")] = mod
    return alias


def md_files():
    out = []
    for base in (ROOT, os.path.join(ROOT, "docs"), os.path.join(ROOT, "k8s")):
        if not os.path.isdir(base):
            continue
        for name in sorted(os.listdir(base)):
            if name.endswith(".md"):
                out.append(os.path.join(base, name))
    for module in sorted(os.listdir(ROOT)):
        p = os.path.join(ROOT, module, "README.md")
        if module.startswith("erp-") and os.path.exists(p):
            out.append(p)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    ports = load_ports()
    alias = build_alias(ports)

    total = 0
    for path in md_files():
        rel = os.path.relpath(path, ROOT)
        lines = open(path, encoding="utf-8").read().split("\n")
        changed = False
        for i, line in enumerate(lines):
            toks = {alias[t] for t in re.findall(r"erp-[a-z0-9-]+", line) if t in alias}
            if not toks:
                continue
            nums = [int(m.group(1)) for m in PORT_RE.finditer(line)]
            nums = [n for n in nums if 8000 <= n <= 8999]
            if len(nums) != 1 or len(toks) != 1:
                continue
            mod = toks.pop()
            want = ports[mod]
            if nums[0] == want:
                continue
            old = nums[0]
            new_line = PORT_RE.sub(lambda m: str(want) if int(m.group(1)) == old else m.group(1), line, count=1)
            lines[i] = new_line
            changed = True
            total += 1
            print(f"{rel}:{i+1}  {mod}  {old} -> {want}")
        if changed and not args.dry_run:
            open(path, "w", encoding="utf-8").write("\n".join(lines))
    print(f"\n共修正 {total} 处（dry-run={args.dry_run}）")


if __name__ == "__main__":
    main()
