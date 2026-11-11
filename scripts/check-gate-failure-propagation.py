#!/usr/bin/env python3
"""闸门「失败能不能真的把 job 拖红」巡检（CI 假绿防线）。

背景（2026-11-11 10:45 实测）：
  51 道闸门全绿这件事，本身**不能证明**「闸门红了 CI 就会红」。
  一个闸门要真的拖红 job，必须同时满足 4 个条件：

    ① **跑得到**   —— step 的 `if:` 不是恒假、job 不是被跳过
    ② **跑得完**   —— step / job 的 `continue-on-error` 没有把它标成「失败无所谓」
    ③ **红得出来** —— 脚本本身有**非零退出路径**（不是只 print 一个 ❌ 然后 return 0）
    ④ **红得传得上去** —— 退出码没被 `|| true` 吞掉、没被管道 `| tee` 掩盖、
                        shell 没关掉 errexit（`-e`）

📌 **本闸门补的是哪个缺口**（对照 `check-workflow-validity.py`，避免重复造闸门）：
  - 已有闸门查了：step 级 `continue-on-error`（含「巡检」字样的 step）、
    `if:` 里用 secrets、`refs/heads` 分支名存在、key 合法性、`runs-on` 存在
  - **本闸门查的是那 4 个条件里的「传播链」**：
      · **job 级** `continue-on-error: true`（已有闸门只看 step 级 → **job 级是真空**）
      · 闸门命令行上的 `|| true` / `|| :` / `|| echo` / `|| exit 0`（失败被吞）
      · 闸门命令行上的管道 `| tee / | cat / | head`（**退出码变成管道最后一个命令的** → 恒 0）
      · `shell:` 关掉 errexit（`shell: bash {0}` / `shell: sh` → 块中间的命令失败不会中止 step）
      · step 里出现 `set +e`
      · 闸门 step 的 `if:` 恒假（`if: false` → 闸门永远不跑 = 规则 29「闸门不跑 = 不存在」）
      · **闸门脚本没有非零退出路径**（只能永远绿 → 规则 88「闸门自己坏了 = 静默失效」）

📌 **为什么值得单独一道闸门**：
  规则 29（闸门不跑 = 不存在）和规则 88（闸门自己崩溃 = 静默失效）都假设
  「闸门红了 = CI 会红」。这个假设**从来没被机器验证过** —— 只要有人在 workflow 里
  手滑加个 `|| true`，**整个 51 道闸门体系就静默退化成 51 个装饰品**，
  而 CI 照样全绿、没有任何报警。

📌 **已知边界（声明出来，避免下一个维护者误判覆盖面 —— 规则 116）：
  1. 只认「**同一条命令行**上的 `|| true` / 管道」；如果写成
     `set +e` + 后面单独一行 `python3 scripts/check-x.py`，**靠 `set +e` 那条规则**兜住。
  2. 不认「通过变量间接调用闸门」（例如 `GATE=scripts/check-x.py; python3 $GATE`）——
     这类写法本仓库没有；真出现了也只能靠 `set +e` 规则兜。
  3. 不验证闸门脚本**内部的**判据对不对（那是各闸门自己的事）——
     本闸门只管「红灯能不能传到 job」这一段。

用法：python3 scripts/check-gate-failure-propagation.py
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys

try:
    import yaml
except ImportError:  # pragma: no cover
    print("🔴 缺少 PyYAML（pip install pyyaml）→ 本闸门无法工作")
    sys.exit(1)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
WF_DIR = os.path.join(ROOT, ".github", "workflows")

# ── 判据的正则（**等价写法一起认** —— 规则 100）────────────────────────
GATE_RE = re.compile(r"scripts/(check-[\w-]+\.py)")
# `|| true` / `|| :` / `|| echo ...` / `|| exit 0` / `|| continue` → 失败被吞
SWALLOW_RE = re.compile(r"\|\|\s*(true|:|echo\b|printf\b|cat\b|exit\s+0\b|continue\b)")
# 闸门调用后接管道 → 退出码被管道最后一个命令接管（恒 0）
PIPE_AFTER_GATE_RE = re.compile(r"check-[\w-]+\.py\s*\|")
# 恒假的 if 条件（闸门永远不跑）
# 📌 **YAML 会把 `if: false` 解析成布尔值 False**，不是字符串 "false"
#    → 只认字符串写法 = 另一种写法静默失明（规则 100）→ 这里两种都认
FALSE_IF_VALUES = {"false", "''", '""', "0", "none", "null"}


def is_false_cond(cond):
    """`if:` 是否恒假（闸门永远不跑）。布尔 False 与字符串写法都认。"""
    if cond is False:
        return True
    s = str(cond).strip()
    # 去掉 ${{ }} 包装
    s = re.sub(r"^\$\{\{\s*", "", s)
    s = re.sub(r"\s*\}\}$", "", s).strip()
    return s.lower() in FALSE_IF_VALUES
# 脚本里的非零退出路径（脚本能不能变红）
NONZERO_EXIT_RE = re.compile(
    r"sys\.exit\(\s*[1-9]|exit\(\s*[1-9]|return\s+1\b|SystemExit\(\s*[1-9]|\bexit\s+1\b")


def errexit_disabled(shell_value):
    """shell 是否关掉了 errexit（`-e` / `--errexit`）→ 块中间失败不会中止 step。"""
    s = str(shell_value).strip()
    if not s:
        return False
    if "-e" in s.split() or "--errexit" in s:
        return False
    # `bash {0}` / `sh` / `sh {0}` / `bash --noprofile --norc {0}` → 没有 -e → 关掉了
    return True


def gate_lines(run_text):
    """返回 run 块里真正调用闸门的命令行（跳过注释行与空行）。"""
    out = []
    for raw in str(run_text or "").splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if GATE_RE.search(line):
            out.append(line)
    return out


def main():
    errors = []
    infos = []
    audited = {}          # 闸门脚本 → 被引用的次数
    files = sorted(f for f in os.listdir(WF_DIR) if f.endswith((".yml", ".yaml"))) \
        if os.path.isdir(WF_DIR) else []

    print(f"📋 巡检 {len(files)} 个 workflow 文件的「闸门失败传播链」\n")

    for fn in files:
        path = os.path.join(WF_DIR, fn)
        try:
            with open(path, encoding="utf-8") as f:
                doc = yaml.safe_load(f)
        except Exception as e:
            errors.append(f"[{fn}] YAML 解析失败：{e}")
            continue
        if not isinstance(doc, dict):
            continue

        # 顶层 defaults.run.shell（关掉 errexit 会影响所有 step）
        top_shell = ((doc.get("defaults") or {}).get("run") or {}).get("shell")
        if top_shell and errexit_disabled(top_shell):
            errors.append(
                f"[{fn}] 顶层 `defaults.run.shell: {top_shell}` **关掉了 errexit**\n"
                f"      → run 块中间的命令失败**不会中止 step** → 闸门红了也可能被后面的命令盖过去"
            )

        for jn, jd in (doc.get("jobs") or {}).items():
            if not isinstance(jd, dict):
                continue
            steps = jd.get("steps") or []

            job_gate_lines = []
            for st in steps:
                if not isinstance(st, dict):
                    continue
                run = st.get("run") or ""
                sname = st.get("name") or "(未命名)"
                gl = gate_lines(run)
                job_gate_lines += gl
                for line in gl:
                    for m in GATE_RE.finditer(line):
                        audited.setdefault(m.group(1), []).append(f"{fn}:{jn}:{sname}")

                if not gl:
                    continue

                # ① step 级 continue-on-error
                if st.get("continue-on-error") is True:
                    errors.append(
                        f"[{fn}] job '{jn}' step「{sname}」是闸门，却设了 `continue-on-error: true`\n"
                        f"      → 闸门红了 step 仍绿 → **等于没跑**"
                    )

                # ② 恒假的 if
                cond = st.get("if")
                if cond is not None and is_false_cond(cond):
                    errors.append(
                        f"[{fn}] job '{jn}' step「{sname}」的 `if: {cond}` **恒假**\n"
                        f"      → 闸门**永远不跑** → 规则 29「闸门不跑 = 不存在」"
                    )

                # ③ shell 关掉 errexit
                sh = st.get("shell")
                if sh and errexit_disabled(sh):
                    errors.append(
                        f"[{fn}] job '{jn}' step「{sname}」的 `shell: {sh}` **关掉了 errexit**\n"
                        f"      → run 块中间的命令失败**不会中止 step** → 闸门的红传不上去"
                    )

                # ④ run 块里的 set +e
                if re.search(r"^\s*set\s+\+e\s*$", str(run), re.M):
                    errors.append(
                        f"[{fn}] job '{jn}' step「{sname}」的 run 块里有 `set +e`\n"
                        f"      → 之后的命令失败**不再中止 step** → 闸门的红传不上去"
                    )

                # ⑤ 命令行上的失败吞噬 / 管道掩盖
                for line in gl:
                    if SWALLOW_RE.search(line):
                        errors.append(
                            f"[{fn}] job '{jn}' step「{sname}」的闸门命令**把失败吞掉了**：\n"
                            f"      {line}\n"
                            f"      → `|| true` 之类 → 闸门退出码被丢弃 → **红灯传不上去**"
                        )
                    if PIPE_AFTER_GATE_RE.search(line):
                        errors.append(
                            f"[{fn}] job '{jn}' step「{sname}」的闸门命令**接了管道**：\n"
                            f"      {line}\n"
                            f"      → 管道里 `$?` 是**最后一个命令**的退出码 → 闸门红了也是绿的"
                        )

            # ⑥ job 级 continue-on-error（已有闸门只看 step 级 → 这里才是真空）
            if jd.get("continue-on-error") is True and job_gate_lines:
                errors.append(
                    f"[{fn}] job '{jn}' 设了 **job 级** `continue-on-error: true`，"
                    f"而这个 job 里有 {len(job_gate_lines)} 处闸门调用\n"
                    f"      → 整个 job 失败**不影响 workflow 结论** → 该 job 里所有闸门的红都白红"
                )

            # ⑦ job 级 defaults.run.shell
            jshell = ((jd.get("defaults") or {}).get("run") or {}).get("shell")
            if jshell and errexit_disabled(jshell) and job_gate_lines:
                errors.append(
                    f"[{fn}] job '{jn}' 的 `defaults.run.shell: {jshell}` **关掉了 errexit**\n"
                    f"      → 该 job 内所有闸门的红都传不上去"
                )

    # ⑧ 被引用的闸门脚本必须有非零退出路径（否则永远绿）
    for script, refs in sorted(audited.items()):
        spath = os.path.join(ROOT, "scripts", script)
        if not os.path.exists(spath):
            # 文件缺失由 check-workflow-script-refs.py 负责，这里不重复报（规则 32）
            continue
        with open(spath, encoding="utf-8", errors="ignore") as f:
            src = f.read()
        if not NONZERO_EXIT_RE.search(src):
            errors.append(
                f"[scripts/{script}] **没有非零退出路径**（只 print 不 return 1 / sys.exit(1)）\n"
                f"      → 被引用 {len(refs)} 次：{refs[0]}\n"
                f"      → 这个闸门**永远绿** → 规则 88「闸门自己坏了 = 静默失效」"
            )

    print(f"ℹ️  审计了 {len(audited)} 个闸门脚本、{sum(len(v) for v in audited.values())} 处调用\n")

    if errors:
        print(f"🔴 发现 {len(errors)} 处「闸门失败传不上去」问题：\n")
        for e in errors:
            print(f"   {e}\n")
        print(f"合计问题 {len(errors)} 项")
        print()
        print("📌 **闸门红了但 CI 还是绿的 = 这道闸门等于没跑**（规则 29 / 88 / 99）")
        return 1

    print(f"✅ 所有闸门的失败都能真正拖红 job（{len(audited)} 个脚本 / "
          f"{sum(len(v) for v in audited.values())} 处调用）")
    return 0


if __name__ == "__main__":
    sys.exit(main())
