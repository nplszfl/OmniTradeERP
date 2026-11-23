#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
第 80 道闸门：枚举定义了合法取值，但写库前没人校验 —— 脏值静默入库。

🔴 **真实缺陷（2026-11-21 实测并已修）**：
   `erp-warehouse-service` 的 `wms_inbound_order.inbound_type` /
   `wms_outbound_order.outbound_type` 是 `VARCHAR(30)`，
   **没有 ENUM / CHECK 约束**（MySQL 的 ENUM 在 DDL 里没用）；
   而 `InboundType` / `OutboundType` 两个枚举在
   `WarehouseServiceImpl` 里**只被 import、从不被使用** ——
   `createInboundOrder` / `createOutboundOrder` 不校验类型合法性。
   → 一条拼错的 `'RETRUN'`（RETURN 的错字）**静默入库**，
     后续所有按类型分组的库存报表 / 对账 / 审计**漏掉这条单据**，
     而且**全程没有任何报错**。

📌 **本闸门抓的缺陷类（更一般）**：
   「某个 `enum` 定义了合法取值集合（带 `getByCode`），但某个**写库方法**
   把某个**枚举字段**直接塞进实体/入参，没有经过该枚举的任何引用」。

📌 **判据：unused import（规则 100 的反面 —— 只认一种写法 = 双向失明）**：

   ① 某模块的 `enums/` 包里定义了枚举 `E`（带 `getByCode`）；
   ② 该模块的某个 `*ServiceImpl.java` **import 了 `E`**；
   ③ 但这个文件的**正文**（去掉 import 行）里**从未出现过 `E` 这个名字**。

   满足 ①②③ → 报红。

📌 **为什么用「import 了但正文从未引用」当判据**（而不是「有没有 setter 调用」）：

   🔴 **踩过一次的坑（写这个闸门时真的踩了）**：
   最初版本用「方法体里有 `setInboundType(...)` 且方法体里没有 `InboundType.` 引用」
   当判据 → **反向验证直接失败**：把代码回退到缺陷态（HEAD），
   闸门**依然报绿** —— 因为**缺陷态里根本没有 `setInboundType(...)` 调用**！

   原因：入库单的 `inboundType` 是 **Jackson 直接绑定到实体字段**的
   （`@RequestBody InboundOrderRequest` 里嵌着 `InboundOrder`），
   **不走 setter** → Jackson 反序列化时直接写字段。
   → 「有没有 setter 调用」这种判据对 **Jackson 直绑**的字段**天生失明**。

   而「import 了枚举却从不在正文里用它」**不依赖字段是怎么被赋值的** ——
   它只看「**这个文件知不知道这个枚举的存在**」：
   - 真做了校验 → 正文里必然出现 `InboundType.getByCode(...)` 之类 → 不报；
   - 没做校验 → import 悬空（Java 编译器**不会**对 unused import 报错！）
     → **能一路编译通过、能通过全部单测、能上线**，只有数据脏了才暴露。

   → 这也正是**编译器不报错、闸门能报**的那一类缺陷。

📌 **第二版补的坑（自检把自己抓出来了）**：
   第一版判据只看「正文里有没有出现过枚举名」，**没剥注释** →
   文件里只要有一句 `// TODO: 应该校验 InboundType`，
   闸门就认为「这个文件用了这个枚举」→ **假绿**：
   缺陷原封不动还在，闸门却说「干净」。
   → 第二版把**行注释 / 块注释 / 字符串字面量**全部**空格化**
   （**行数不变** → 报出来的行号仍然对得上原文件），
   只认**代码里的真引用**。
   ⚠️ **第一版「剥注释」的实现自己也踩了一次坑**：
   把注释内容**原样保留**（而不是空格化）→ 注释里的枚举名照样能匹配上
   → 自检 3 条新用例当场报红 → 改成空格化才通过。
   **这就是自检必须包含「缺陷形状本身」的用例**的价值。

📌 **已知边界（不要指望本闸门抓这些）**：
   - 只看 `src/main`；`src/test` 里允许直接用枚举常量（正常）。
   - 只看 `*ServiceImpl.java`（写库方法在这里）。
     ⚠️ **如果把校验写在 Controller / Converter 里，本闸门会误报**
     → 所以额外认两种「等价写法」（规则 100）：
       ① 校验写在同模块的 **Converter / Helper** 类里（被 ServiceImpl 调用）；
       ② 校验写在 **Controller** 里（ServiceImpl 不管，Controller 先拦）。
     这两种都在下面的 `EXTERNAL_VALIDATORS` 里显式列出。
   - 🔴 **只认放在 `enums/` 包下的枚举** —— 实测 `src/main` 里 34 个枚举定义里
     **只有 18 个**在 `enums/` 包里，另外 16 个在 `entity/`、`constant/`、
     `model/` 等目录下 → **本闸门看不见它们**。
     （已知的漏网例子：`erp-tenant-service` 的 `TenantStatus` / `UserRole`、
      `erp-common` 的 `OrderStatus` / `PlatformType`。）
     → 这是**已知边界**，不是已修的缺陷；下一轮可以把枚举识别放宽。
   - DDL 层用 MySQL `ENUM(...)` 硬约束的表天然免疫，本闸门对它们无意义。
   - **枚举字段声明成枚举类型本身**（`private FinanceType type;`）的实体
     **天然免疫** —— Jackson 反序列化时枚举不合法会直接抛异常。
     本闸门只抓「声明成 String + 用枚举定义取值」这种**真危险**的组合。

📌 **反向验证（规则 40 / 75）**：
   ① 把 `WarehouseServiceImpl` 回退到缺陷态 → 本闸门报红（2 处）；
   ② 还原 → 报绿。

用法：
    python3 scripts/check-enum-field-validated.py            # 人看
    python3 scripts/check-enum-field-validated.py --self-test # 自检（CI 用）
    python3 scripts/check-enum-field-validated.py --strict    # CI 用（有问题退出码非 0）

退出码：0 = 干净，1 = 有可疑项
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_MAIN_RE = re.compile(r"[/\\]src[/\\]main[/\\]")

# 📌 枚举定义所在的包路径（相对路径片段）
ENUM_PACKAGE_RE = re.compile(r"[/\\]enums[/\\][^/\\]+\.java$")

# 📌 「import 了同模块的枚举」—— 必须是 `xxx.enums.E` 这种包路径
ENUM_IMPORT_RE = re.compile(r"^\s*import\s+[\w.]*\.enums\.(\w+)\s*;")

# 📌 **等价写法白名单**：校验可能发生在 ServiceImpl 之外的类里。
#    见到这些名字出现在 ServiceImpl 正文里 → 认为「该枚举已被校验」→ 不报。
#    ⚠️ 必须是**真实存在**的类名，不能凭空写一个来「消音」。
EXTERNAL_VALIDATORS = {
    "FinanceConverter": "erp-finance-service 用它把 type 转 FinanceType/CategoryType",
}

# 📌 必须是**真实存在**的枚举类名 → 用来验证白名单没写错
KNOWN_ENUM_NAMES = {
    "InboundType", "OutboundType", "WaveStatus", "PickStatus",
    "FinanceType", "CategoryType", "RecordStatus", "AccountStatus",
    "OperationType", "InventoryStatus", "AlertStatus", "AlertType",
    "CheckStatus", "LockStatus", "LockType",
}


def module_of(java_path, scan_root=ROOT):
    """取 `erp-xxx` 这个模块名。"""
    rel = os.path.relpath(java_path, scan_root)
    for part in rel.split(os.sep):
        if part.startswith("erp-"):
            return part
    return None


def enum_names_for_module(module, scan_root=ROOT):
    """返回该模块 `enums/` 包里定义的枚举类名集合。

    📌 scan_root 必须是**本次扫描的根目录**，不能写死全局 ROOT ——
       否则 --self-test 用临时目录时，枚举会去真实仓库里找，
       永远找不到 → 判据退化成「什么都不报」→ **自检假绿**。
    """
    names = set()
    module_root = os.path.join(scan_root, module)
    if not os.path.isdir(module_root):
        return names
    for dirpath, _dirs, files in os.walk(module_root):
        if "target" in dirpath:
            continue
        for fn in files:
            if not fn.endswith(".java"):
                continue
            if not ENUM_PACKAGE_RE.search(os.path.join(dirpath, fn).replace(os.sep, "/")):
                continue
            names.add(fn[:-5])
    return names


def strip_imports(src):
    """去掉 import 行（以及包声明），得到「正文」。"""
    out = []
    for line in src.split("\n"):
        stripped = line.strip()
        if stripped.startswith("import ") or stripped.startswith("package "):
            continue
        out.append(line)
    return "\n".join(out)


# 📌 **块注释 / 行注释 / 字符串字面量** —— 三种「提到枚举名但不是真引用」的地方。
#
# 🔴 **为什么必须剥（写这个闸门时自己踩到的坑）**：
#   第一版只剥 import 行 → 正文里只要**有注释提到** `InboundType`，
#   闸门就认为「这个文件用了这个枚举」→ **假绿**。
#   实测：文件里只有一句 `// TODO: 应该校验 InboundType`，
#   闸门报「未发现可疑点」—— 但**代码里根本没做任何校验**，
#   缺陷原封不动还在，闸门却说「干净」。
#   → 这比「闸门不存在」更坏：它让人以为这一类已经被守住了。
#
# 📌 **剥的时候必须保持行数不变**（把内容换成空格，**换行符原样保留**），
#   否则报出来的行号会整体偏移 → 定位不到真正的问题行。
def _blank(s):
    """把一段文本里的非换行字符换成空格，换行符原样保留 → 行数不变。"""
    return "".join(ch if ch == "\n" else " " for ch in s)


def strip_comments_and_literals(src):
    """剥掉注释与字符串字面量，**保持行数不变**（换行全部保留）。"""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        # 行注释
        if c == "/" and i + 1 < n and src[i + 1] == "/":
            j = src.find("\n", i)
            if j == -1:
                j = n
            out.append(_blank(src[i:j]))   # 空格化（无换行）
            i = j
            continue
        # 块注释
        if c == "/" and i + 1 < n and src[i + 1] == "*":
            j = src.find("*/", i + 2)
            j = n if j == -1 else j + 2
            out.append(_blank(src[i:j]))   # 空格化（内部换行保留）→ 行数不变
            i = j
            continue
        # 字符串字面量（含转义）
        if c == '"':
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == '"':
                    j += 1
                    break
                j += 1
            out.append(_blank(src[i:j]))
            i = j
            continue
        # 字符字面量
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(_blank(src[i:j]))
            i = j
            continue
        out.append(c)
        i += 1
    return "".join(out)


def code_body(src):
    """真正的「代码正文」= 去掉 import/package + 注释 + 字符串字面量。

    📌 行数与 `src` 完全一致 → 报出来的行号仍然对得上原文件。
    """
    return strip_comments_and_literals(strip_imports(src))


def analyze_file(java_path, enum_names):
    """返回问题列表 [(enum_name, line_no, snippet)]"""
    problems = []
    try:
        src = open(java_path, encoding="utf-8", errors="ignore").read()
    except OSError:
        return problems

    # 📌 **import 行也要从「剥注释后」的文本里取** ——
    #    否则 `// import com.demo.enums.InboundType;` 这种
    #    被注释掉的 import 行会被当成「真的 import 了」→ 误报。
    stripped_src = strip_comments_and_literals(src)

    imported = []
    for line in stripped_src.split("\n"):
        m = ENUM_IMPORT_RE.match(line)
        if m and m.group(1) in enum_names:
            imported.append(m.group(1))
    if not imported:
        return problems

    body = code_body(src)

    for enum_name in sorted(set(imported)):
        # 📌 正文里出现了这个枚举名 → 说明文件**确实用了**它 → 不是悬空 import
        if re.search(r"\b%s\b" % re.escape(enum_name), body):
            continue
        # 📌 校验写在 ServiceImpl 之外的类里（Converter / Helper）→ 认
        if any(re.search(r"\b%s\b" % re.escape(h), body) for h in EXTERNAL_VALIDATORS):
            continue
        line_no = 0
        # 找该 import 的行号
        for idx, line in enumerate(stripped_src.split("\n"), start=1):
            m = ENUM_IMPORT_RE.match(line)
            if m and m.group(1) == enum_name:
                line_no = idx
                break
        lines = src.splitlines()
        snippet = lines[line_no - 1].strip()[:120] if 0 < line_no <= len(lines) else ""
        problems.append((enum_name, line_no, snippet))
    return problems


def collect(root=ROOT):
    problems = []
    for dirpath, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if d not in ("target", "node_modules", ".git", "dist")]
        for fn in files:
            if not fn.endswith(".java"):
                continue
            p = os.path.join(dirpath, fn)
            if not SRC_MAIN_RE.search(p.replace(os.sep, "/")):
                continue
            # 只看 Service 实现类（写库方法在这里）
            if "ServiceImpl" not in fn:
                continue
            module = module_of(p, root)
            if not module:
                continue
            enum_names = enum_names_for_module(module, root)
            if not enum_names:
                continue
            for enum_name, line_no, snippet in analyze_file(p, enum_names):
                problems.append((p, enum_name, line_no, snippet))
    return problems


def run_self_test():
    """合成 fixture 验证判据 —— fixture 文件名必须落在 ServiceImpl 范围内。"""
    import shutil
    import tempfile

    tmp = tempfile.mkdtemp(prefix="check-enum-")
    try:
        module = os.path.join(tmp, "erp-demo")
        base = os.path.join(module, "src", "main", "java", "com", "demo")
        enum_dir = os.path.join(base, "enums")
        svc_dir = os.path.join(base, "service", "impl")
        os.makedirs(enum_dir, exist_ok=True)
        os.makedirs(svc_dir, exist_ok=True)

        with open(os.path.join(enum_dir, "InboundType.java"), "w", encoding="utf-8") as f:
            f.write("package com.demo.enums;\npublic enum InboundType {\n"
                    "  PURCHASE, RETURN, TRANSFER, PRODUCTION, OTHER;\n"
                    "  public static InboundType getByCode(String c) { return null; }\n}\n")
        with open(os.path.join(enum_dir, "FinanceType.java"), "w", encoding="utf-8") as f:
            f.write("package com.demo.enums;\npublic enum FinanceType {\n"
                    "  INCOME, EXPENSE, REFUND;\n"
                    "  public static FinanceType getByCode(String c) { return null; }\n}\n")

        cases = [
            # (文件名, 内容, 期望是否报错)
            # 📌 **缺陷态的真形状**：Jackson 直绑字段 → **没有 setter 调用**，
            #    只有一句悬空的 import → 必须能报出来（第一版闸门就是漏了这条）
            ("BadJacksonBoundInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class BadJacksonBoundInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    insert(order);\n"
             "  }\n"
             "  private void insert(Object order) {\n"
             "    // Jackson 直接绑定字段，没有 setter 调用\n"
             "  }\n"
             "}\n", True),
            # 缺陷态：import 了但正文里从不引用（原始真缺陷形状）
            ("BadUnusedImportInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class BadUnusedImportInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    order.setOrderNo(\"X\");\n"
             "  }\n"
             "}\n", True),
            # ✅ 合法：正文里用了枚举做校验 → 不报
            ("OkValidatedInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class OkValidatedInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    if (InboundType.getByCode(\"RETRUN\") == null) { throw new RuntimeException(); }\n"
             "    order.setInboundType(\"RETRUN\");\n"
             "  }\n"
             "}\n", False),
            # ✅ 合法：正文里引用了枚举常量 → 不报
            ("OkConstantInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class OkConstantInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    order.setInboundType(InboundType.PURCHASE.name());\n"
             "  }\n"
             "}\n", False),
            # ✅ 合法：校验在转换器里（FinanceConverter 是白名单里的真实类名）
            ("OkConverterHelperServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.FinanceType;\n"
             "public class OkConverterHelperServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    FinanceConverter.toRecord(order);\n"
             "  }\n"
             "}\n", False),
            # ✅ 合法：完全没 import 枚举 → 不关本闸门的事
            ("OkUnrelatedFieldServiceImpl.java",
             "package com.demo.service.impl;\n"
             "public class OkUnrelatedFieldServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    order.setCustomerName(\"x\");\n"
             "  }\n"
             "}\n", False),
            # 🔴 **注释里提到枚举名 ≠ 真的用了** —— 第一版闸门被这条骗过（假绿）
            #    文件里只有一句 `// TODO: 应该校验 InboundType`，
            #    闸门却说「干净」→ 缺陷原封不动还在，却给人「已经守住」的错觉。
            ("BadCommentOnlyInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class BadCommentOnlyInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    // TODO: 应该校验 InboundType\n"
             "    insert(order);\n"
             "  }\n"
             "  private void insert(Object order) {\n"
             "  }\n"
             "}\n", True),
            # 🔴 **块注释里提到枚举名** → 同上，必须报
            ("BadBlockCommentInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class BadBlockCommentInboundTypeServiceImpl {\n"
             "  /* 这里原本打算用 InboundType 校验 */\n"
             "  public void create(Object order) {\n"
             "    insert(order);\n"
             "  }\n"
             "  private void insert(Object order) {\n"
             "  }\n"
             "}\n", True),
            # 🔴 **字符串字面量里提到枚举名** → 同上，必须报
            ("BadStringLiteralInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class BadStringLiteralInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    order.setRemark(\"type must be InboundType\");\n"
             "    insert(order);\n"
             "  }\n"
             "  private void insert(Object order) {\n"
             "  }\n"
             "}\n", True),
            # ✅ 合法：Javadoc 里提到枚举名，但**代码里真的用了** → 不报
            ("OkJavadocPlusRealUseInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "import com.demo.enums.InboundType;\n"
             "public class OkJavadocPlusRealUseInboundTypeServiceImpl {\n"
             "  /** 这里的类型取自 {@link InboundType} */\n"
             "  public void create(Object order) {\n"
             "    if (InboundType.getByCode(\"X\") == null) { throw new RuntimeException(); }\n"
             "    insert(order);\n"
             "  }\n"
             "  private void insert(Object order) {\n"
             "  }\n"
             "}\n", False),
            # ✅ 合法：**被注释掉的 import** 不算真的 import → 整个文件不归本闸门管
            #    （否则「注释里 import 了一下」会误报成「import 了却不用」）
            ("OkCommentedOutImportInboundTypeServiceImpl.java",
             "package com.demo.service.impl;\n"
             "// import com.demo.enums.InboundType;\n"
             "public class OkCommentedOutImportInboundTypeServiceImpl {\n"
             "  public void create(Object order) {\n"
             "    insert(order);\n"
             "  }\n"
             "  private void insert(Object order) {\n"
             "  }\n"
             "}\n", False),
        ]

        failures = []
        for fname, content, should_fail in cases:
            fp = os.path.join(svc_dir, fname)
            with open(fp, "w", encoding="utf-8") as f:
                f.write(content)
            # 📌 **逐个 fixture 单独扫** —— 多个 fixture 放一起时，
            #    别的文件被报出来会让 `got` 变成 True → 假绿。
            solo = tempfile.mkdtemp(prefix="check-enum-solo-")
            try:
                shutil.copytree(tmp, solo, dirs_exist_ok=True)
                got = len(collect(solo)) > 0
            finally:
                shutil.rmtree(solo, ignore_errors=True)
            if got != should_fail:
                failures.append((fname, should_fail, got))
            os.remove(fp)

        # 📌 **白名单自检**：白名单里的类名必须是真实存在的 → 防止「写个不存在的名字消音」
        for helper in EXTERNAL_VALIDATORS:
            found = False
            for dirpath, _dirs, files in os.walk(ROOT):
                if "target" in dirpath:
                    continue
                for fn in files:
                    if fn == helper + ".java":
                        found = True
                        break
                if found:
                    break
            if not found:
                failures.append((helper + ".java（白名单里的类必须真实存在）", False, True))

        if failures:
            print("❌ 自检失败：")
            for fname, want, got in failures:
                print("   %s: 期望报错=%s 实际报错=%s" % (fname, want, got))
            return 1
        print("✅ 自检通过（%d 个用例 + %d 个白名单存在性检查）"
              % (len(cases), len(EXTERNAL_VALIDATORS)))
        return 0
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--self-test", action="store_true")
    ap.add_argument("--strict", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return run_self_test()

    problems = collect()
    if not problems:
        print("✅ 枚举字段写库校验：未发现「定义了枚举却不校验」的可疑点")
        return 0

    print("🔴 发现 %d 处「枚举定义了合法取值，写库前却没校验」：" % len(problems))
    for path, enum_name, line, snippet in problems:
        rel = os.path.relpath(path, ROOT)
        print("   · %s / 枚举 %s（第 %d 行）" % (rel, enum_name, line))
        print("     %s" % snippet)
    print()
    print("💡 修法：写库前用枚举校验，并把**归一化后的值写回实体** ——")
    print("   if (XxxType.getByCode(value.toUpperCase()) == null) { throw new BusinessException(\"xxx 类型不合法：\" + value); }")
    print("   order.setXxxType(normalized);   // 📌 必须写回：getByCode 是精确匹配")
    print("   （枚举名以实际为准；空值按「未指定」处理还是直接拒绝，取决于该列 DDL 是否可空）")
    return 1


if __name__ == "__main__":
    sys.exit(main())
