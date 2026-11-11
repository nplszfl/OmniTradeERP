#!/usr/bin/env python3
"""CI 日志统计（**把取数口径固化成脚本**，避免同一个量被算出好几个数）。

📌 **为什么要有这个脚本**（2026-11-11 当天踩了两次同一族的坑）：
  ① **同一份输出存了两份**：GitHub 日志 ZIP 里每个 job 有「job 级」entry（内含该 job 全部 step
     的输出），每个 step 另有「step 级」entry → 同一段 `Tests run:` 出现两次 → 按行累加**翻倍**
     （真值 1022 → 假数 2044）。
  ② **同一份数据有多个块**：`Reactor Build Order` 在一份日志里出现多次（完整构建 27 项目 /
     定向构建 3 项目）→ 只取第一个块 → 得出「reactor = 3」的错数。
  ③ **闸门分散在多个 workflow**：只看 CI/CD 那一个 run → 判成「新闸门没跑」。

✅ **本脚本的处理方式（都是「结构上排除」，不靠人工记得）**：
  - 日志 ZIP **只用 Python `zipfile` 解**（macOS 的 `unzip` CLI 会**静默丢掉中文名 entry** → 整包并集只剩 38 → 假红）
  - **测试数只取 job 级 entry**（判别特征：entry 里含 `Runner Image Provisioner`）→ 不双算
  - **Reactor 取所有块的最大值** → 不被定向构建干扰
  - **闸门按唯一脚本名去重**，且 `--all-workflows` 时**跨 workflow 取并集**
  - 每个数字都**标出口径**，避免下次把不同口径的数并列

用法：
  python3 scripts/ci-log-stats.py <run_id>                     # 单个 run
  python3 scripts/ci-log-stats.py <run_id> --all-workflows     # 同一 commit 的所有 workflow run 取并集
  python3 scripts/ci-log-stats.py --head                       # 用当前 HEAD 的 run
退出码：0 = 取数成功；1 = 取数失败（**绝不返回半截数字**，规则 43：要么查，要么失败）
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import time
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = "nplszfl/OmniTradeERP"

# 聚合行：Tests run: N, Failures: x, Errors: y, Skipped: z   （**没有** -- in 后缀）
AGG_RE = re.compile(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)\s*$")
# 每测试类行：多一段 Time elapsed 与 -- in <类名>
PER_RE = re.compile(
    r"Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+), "
    r"Time elapsed: [^ ]+ s -- in (.+?)\s*$")
REACTOR_ITEM_RE = re.compile(r"\[INFO\]\s+(.+?)\s+\[(pom|jar|war)\]\s*$")
GATE_RE = re.compile(r"scripts/(check-[\w-]+\.py)")


def sh(cmd, timeout=300, binary=False):
    return subprocess.run(cmd, capture_output=True, text=not binary, timeout=timeout, cwd=ROOT)


def gh_json(args):
    r = sh(["gh"] + args)
    if r.returncode != 0:
        raise SystemExit(f"❌ gh {' '.join(args)} 失败：{(r.stderr or r.stdout).strip()[:300]}")
    try:
        return json.loads(r.stdout)
    except json.JSONDecodeError as e:
        raise SystemExit(f"❌ gh 输出不是合法 JSON：{e}\n{(r.stdout or '')[:300]}")


def download_log_zip(run_id, dest, retries=3):
    """下载整个 run 的日志 ZIP（**只用 Python zipfile 解**）。

    📌 日志 ZIP 是**二进制** → 必须以 bytes 捕获（用 text=True 会在解码时炸），
    且**绝不能用 `unzip` CLI**（会静默丢中文名 entry）。

    📌 **必须重试 + 验完整性**（规则 131）：
      实测 `gh api .../actions/runs/<id>/logs` 会**半路失败** →
      `stream error: stream ID 1; CANCEL; received from peer`（rc=0 但内容残缺）
      → 只看 rc 会把「日志没下全」当成「这个 workflow 没闸门」→ **假红**。
      ✅ 判据 = `zipfile.testzip()` 返回 None（无损坏 entry），否则重下。
    """
    last_err = ""
    for attempt in range(1, retries + 1):
        r = sh(["gh", "api", f"repos/{REPO}/actions/runs/{run_id}/logs"], binary=True)
        if r.returncode != 0:
            last_err = (r.stderr or b"").decode("utf-8", "replace").strip()[:300]
        else:
            data = r.stdout
            if not data:
                last_err = "下载到 0 字节（多半是 API 限流或 token 失效）"
            else:
                with open(dest, "wb") as f:
                    f.write(data)
                try:
                    z = zipfile.ZipFile(dest)
                    bad = z.testzip()
                    if bad is None:
                        return dest
                    last_err = f"ZIP 损坏：坏 entry = {bad}"
                except zipfile.BadZipFile as e:
                    last_err = f"不是合法 ZIP：{e}"
        if attempt < retries:
            time.sleep(3 * attempt)
    raise SystemExit(
        f"❌ 拉取 run {run_id} 日志失败（已重试 {retries} 次）：{last_err}\n"
        f"   ⚠️ `gh api .../actions/jobs/<id>/logs` 会**静默返 0 字节**，别用它（用整 run 的 logs）\n"
        f"   ⚠️ **「取不到日志」与「该 workflow 没闸门」必须分开** —— 上层不能把前者当成后者（规则 131）")


def classify_entries(zpath):
    """返回 (job 级文本列表, step 级文本列表, 全部文本)"""
    z = zipfile.ZipFile(zpath)
    job_level, step_level = [], []
    for name in z.namelist():
        try:
            text = z.read(name).decode("utf-8", "replace")
        except Exception:
            continue
        # 判别特征：job 级 entry 含 runner 初始化段，step 级 entry 不含
        (job_level if "Runner Image Provisioner" in text else step_level).append(text)
    return job_level, step_level


def strip_prefix(line):
    """去掉 '2026-11-11T04:04:28.9Z #15 4.198 ' 这类时间戳/行号前缀。"""
    return re.sub(r"^[\d\-T:.Z]+\s+#\d+\s+[\d.]+\s+", "", line)


def parse_tests(job_level_texts):
    """测试数：**只用 job 级 entry**（避免 job 级 + step 级双份 → 翻倍）"""
    agg, per = [], []
    for text in job_level_texts:
        for line in text.splitlines():
            m = PER_RE.search(line.rstrip())
            if m:
                per.append((int(m.group(1)), m.group(5)))
                continue
            m = AGG_RE.search(line.rstrip())
            if m:
                agg.append(tuple(int(x) for x in m.groups()))
    return agg, per


def parse_reactor_max(texts):
    """Reactor 项目数：**取所有块的最大值**（完整构建 vs 定向构建）"""
    best = 0
    for text in texts:
        lines = [strip_prefix(l) for l in text.splitlines()]
        for i, line in enumerate(lines):
            if "Reactor Build Order:" not in line:
                continue
            names = []
            for body in lines[i + 1:i + 150]:
                m = REACTOR_ITEM_RE.search(body.rstrip())
                if m:
                    names.append(m.group(1))
                elif names and ("Building " in body or body.startswith("[INFO] ---")):
                    break
            best = max(best, len(names))
    return best


def disk_gates():
    """**当前磁盘**上的闸门（基线 = 磁盘）。"""
    d = os.path.join(ROOT, "scripts")
    return {f for f in os.listdir(d) if f.startswith("check-") and f.endswith(".py")}


def tree_gates(sha):
    """**该 commit 的树**上的闸门（基线 = 被核验那个 commit 的树）。

    📌 **为什么必须有这个**（规则 128）：
        核验一个**较老**的 commit 时，磁盘上可能已经多了好几道新闸门
        → 拿磁盘当基线 → 差集 = 那几道新闸门 → **假红**（「这 commit 漏跑了」）
        → 其实那 commit 当时**根本没有**那些闸门，漏跑的是**别人后来加的**。
        ✅ 基线必须锚在 `git ls-tree <sha> scripts/`。
    """
    try:
        out = subprocess.run(
            ["git", "ls-tree", "-r", sha, "scripts/", "--name-only"],
            cwd=ROOT, capture_output=True, text=True, timeout=60, check=True,
        ).stdout
    except Exception as e:
        raise SystemExit(f"❌ 取不到 {sha} 的树（git ls-tree 失败：{e}）")
    return {os.path.basename(x) for x in out.split()
            if re.match(r"^scripts/check-.*\.py$", x)}


def report(run_id, zpath, all_workflows=False):
    job_level, step_level = classify_entries(zpath)
    job_text = "\n".join(job_level)
    all_text = "\n".join(job_level + step_level)

    agg, per = parse_tests(job_level)
    test_sum = sum(a[0] for a in agg)
    fails = sum(a[1] for a in agg)
    errs = sum(a[2] for a in agg)
    skips = sum(a[3] for a in agg)
    unique_classes = len({nm for _, nm in per})

    gates = set(GATE_RE.findall(all_text))
    reactor = parse_reactor_max([job_text, all_text])

    print(f"📊 CI 日志统计 —— run {run_id}（{REPO}）")
    print(f"   ZIP entry：job 级 {len(job_level)} / step 级 {len(step_level)}"
          f"（📌 同一 step 的输出**在两处各存一份** → 求和只能取 job 级）")
    print()
    print(f"🧪 测试（口径：**只取 job 级 entry 的聚合行**，{len(agg)} 条）")
    print(f"   用例总数 **{test_sum}** ｜ 失败 {fails} / 错误 {errs} / 跳过 {skips}")
    print(f"   每测试类行 {len(per)} → 求和 {sum(n for n, _ in per)} ｜ 唯一类名 {unique_classes}")
    print(f"   ⚠️ 全 entry 求和会是 **{sum(a[0] for a in agg) + sum(n for n, _ in per)}**（双份 → 假数，别用）")
    print()
    print(f"🚦 闸门（口径：**按唯一脚本名去重**）")
    print(f"   本 run 内出现 **{len(gates)}** 个")
    disk = disk_gates()
    missing = sorted(disk - gates)
    print(f"   磁盘 `scripts/check-*.py` = **{len(disk)}** ｜ 本 run 没出现：{missing or '无 ✅'}")
    if missing:
        print(f"   ⚠️ **「本 run 没出现」≠「闸门漏跑」** —— 闸门**分散在多个 workflow** 里，"
              f"本 run 只覆盖它自己那部分 → 请看下面的**跨 workflow 并集**（规则 92）")
    print()
    print(f"🏗️ Reactor（口径：**所有块取最大值**）= **{reactor}**")
    print(f"   ⚠️ 只取第一个块会拿到定向构建的小数字 → 必须取最大值")
    print()
    print(f"❗ `##[error]` 计数 = **{all_text.count('##[error]')}**")
    print()
    return {"tests": test_sum, "gates": len(gates), "reactor": reactor,
            "errors": all_text.count("##[error]"), "missing_gates": missing}


def expand_sha(sha):
    """短 sha → 完整 sha。

    📌 **`head_sha` 参数只认完整 sha** → 传短 sha 会**静默返 0 个 run**（不报错！）
      → 「该 commit 没有任何 run」与「这个 commit 根本没跑过」看起来一模一样 = 假红。
      （实测：`?head_sha=c983436` → total_count=0；`?head_sha=<40 位全 sha>` → 2）
    """
    if re.fullmatch(r"[0-9a-f]{40}", sha or ""):
        return sha
    r = sh(["git", "rev-parse", sha])
    if r.returncode != 0:
        raise SystemExit(f"❌ 无法解析 sha {sha}：{(r.stderr or r.stdout).strip()[:200]}")
    return r.stdout.strip()


def runs_for_sha(sha):
    """同一 commit 的**所有** workflow run（闸门分散在多个 workflow 时必须合并看）。

    📌 **必须按 sha 精确取，不能用 `gh run list --limit N`**：
      `gh run list` 只扫**最近 N 个 run** → 核验一个较老的 commit 时
      → 列表里根本没有它的 run → **并集 = 0（静默给错数）** → 上层报「闸门全漏跑」= **假红**。
      （规则 128 记录的就是这个坑；本仓已有 314 个 run，--limit 30 必然漏。）
    ✅ `gh api repos/:owner/:repo/actions/runs?head_sha=<完整 sha>` = **按 sha 精确过滤**。
    """
    sha = expand_sha(sha)
    rs = gh_json(["api", f"repos/{REPO}/actions/runs?head_sha={sha}&per_page=100"])
    return [{"databaseId": r["id"], "name": r.get("name") or r.get("workflow_name"),
             "headSha": r.get("head_sha"), "workflowName": r.get("workflow_name")}
            for r in rs.get("workflow_runs", [])]


def union_over_workflows(sha):
    """跨 workflow 取闸门并集 → 回答「这个 commit 的闸门**是不是都真跑了**」。"""
    union, detail = set(), []
    for r in runs_for_sha(sha):
        rid = str(r["databaseId"])
        with tempfile.TemporaryDirectory() as td:
            zp = os.path.join(td, "r.zip")
            try:
                download_log_zip(rid, zp)
                if not zipfile.is_zipfile(zp):
                    detail.append((rid, r.get("name", "?"), set(), "下载非 ZIP"))
                    continue
                jl, sl = classify_entries(zp)
                g = set(GATE_RE.findall("\n".join(jl + sl)))
                union |= g
                detail.append((rid, r.get("name", "?"), g, ""))
            except SystemExit as e:
                detail.append((rid, r.get("name", "?"), set(), str(e)[:80]))
    return union, detail


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_id", nargs="?", help="GitHub Actions run id")
    ap.add_argument("--all-workflows", action="store_true",
                    help="把同一 commit 的**所有** workflow run 取并集（闸门分散在多个 workflow 时必用）")
    ap.add_argument("--head", action="store_true", help="用当前 HEAD 对应的 run")
    args = ap.parse_args()

    if args.head and not args.run_id:
        head = sh(["git", "rev-parse", "HEAD"]).stdout.strip()
        mine = runs_for_sha(head)
        if not mine:
            raise SystemExit(f"❌ 当前 HEAD {head[:8]} 还没有 run（可能没推送）")
        args.run_id = str(mine[0]["databaseId"])

    if not args.run_id:
        ap.print_help()
        return 1

    with tempfile.TemporaryDirectory() as td:
        zpath = os.path.join(td, "run.zip")
        download_log_zip(args.run_id, zpath)
        if not zipfile.is_zipfile(zpath):
            raise SystemExit("❌ 下载到的不是 ZIP（多半是 0 字节或被 gh 截断）→ 换用 `gh run view --job <id> --log`")
        report(args.run_id, zpath)

    if args.all_workflows:
        sha = gh_json(["run", "view", args.run_id, "--json", "headSha"]).get("headSha")
        union, detail = union_over_workflows(sha)
        # 📌 **基线必须锚在「被核验那个 commit 的树」**，不是当前磁盘（规则 128）：
        #    核验较老的 commit 时，磁盘可能已多出几道新闸门 → 拿磁盘当基线 → **假红**。
        base = tree_gates(sha)
        disk = disk_gates()
        print("🔀 跨 workflow 取并集（同一 commit 的全部 run）")
        for rid, name, g, err in detail:
            print(f"   run {rid}（{name}）→ 闸门 {len(g)} 个" + (f" ⚠️ {err}" if err else ""))
        print(f"   **并集 = {len(union)}** ｜ 基线 `{sha[:7]}` 的树 = {len(base)} ｜ 当前磁盘 = {len(disk)}")
        print(f"   差集（树 - 并集）= {sorted(base - union) or '空 ✅'}")
        if base != disk:
            print(f"   ℹ️ 磁盘比树多 {len(disk - base)} 道：{sorted(disk - base)}")
            print(f"      → 那是**后来**加的闸门，**不属于** {sha[:7]} 的核验范围（规则 128）")
        # 📌 **结论必须与数字同源**（规则 51 / 113 的工具版）：
        #   旧写法把「并集 == 磁盘 → 都真跑过」**无条件打印** →
        #   实测：某个 workflow 的日志**下载失败** → 并集 44 ≠ 磁盘 55 → 11 道闸门被报成「漏跑」
        #   而工具**同时**打印「→ 并集 == 磁盘 → 每道闸门都真跑过」= **自相矛盾的假绿**。
        #   ✅ 现在：有下载失败 → 只能说「无法判定」；且**明确区分「取不到」与「跑出 0 道」**（规则 131）。
        failed = [(rid, err) for rid, name, g, err in detail if err]
        if failed:
            print(f"   ❌ **无法判定**：{len(failed)} 个 run 的日志**没取到**（不是「该 workflow 没闸门」）")
            for rid, err in failed:
                print(f"      run {rid} → {err}")
            print("   → **重试本命令**（下载失败是偶发的）；在取全之前，**不得**断言「闸门都跑过」")
        elif not base:
            print("   ❌ **基线取不到**（该 commit 的树里没有闸门）→ 无法判定")
        elif base - union:
            # 📌 **判据必须比「集合」，不能只比「计数」**（规则 74）：
            #   旧写法 `len(union) == len(disk)` → union 里多一个不存在的名字、少一道真闸门
            #   → **数量相同** → 打印「每道闸门都真跑过 ✅」= **假绿**
            print(f"   ❌ 树 - 并集 = {sorted(base - union)} → **这些闸门没在这个 commit 跑过**")
            extra = sorted(union - base)
            if extra:
                print(f"      （另有并集里多出的名字：{extra} → 日志提到但树里没有）")
        elif union - base:
            print(f"   ⚠️ 并集里多出 {sorted(union - base)}（日志提到但树里没有）→ 不影响「树里的都跑了」")
            print("   → 树里的每道闸门都真在 CI 里跑过 ✅")
        else:
            print("   → 并集 == 树 → **该 commit 的每道闸门都真在 CI 里跑过** ✅")
        if not detail:
            print("   ❌ **该 sha 下一个 run 都没取到** → 可能是 sha 写错 / 该 commit 还没推送；"
                  "**注意「取不到」与「跑出 0 道」必须分开**（规则 131）")

    return 0


if __name__ == "__main__":
    sys.exit(main())
