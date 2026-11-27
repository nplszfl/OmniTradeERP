# 🔗 登记表引用悬空巡检（第 81 道闸门）

> 📌 **本文记录一个真实缺陷的修复**：代码里硬编码的「登记表 ID」**在数据库里根本不存在** →
> 落库的是**悬空引用** → 追溯不到来源；且这类缺陷能一路
> **编译通过 → 单测全绿 → 前 80 道闸门全绿**，只有数据脏了才暴露。
>
> 📌 **本文里的每个数字都是实测的**，不是估计的。复现命令都在下面。

---

## 一、缺陷本体

| 项 | 事实 | 怎么核实的 |
|---|---|---|
| 登记表 | `description_template`（`template_id` 列有 **UNIQUE** 约束 + **4 条**种子） | `database/product-description-service.sql` |
| 库里真实的模板 ID | `amazon_template` / `ebay_template` / `shopee_template` / `general_template` | 同上（`INSERT INTO description_template`） |
| 缺陷态代码里的模板 ID | `amazon_product_template` / `ebay_product_template` / `shopee_product_template` / `general_product_template` | 修复前的 `HEAD` 版本 `ProductDescriptionServiceImpl.initTemplates()` |
| 后果 1 | 落库 `product_description.template_id` = **悬空引用** → join 不到 `description_template` → 「这条描述用了哪个模板」永远查不出来 | 字符串字面量，编译器不校验存在性 |
| 后果 2 | `listTemplates()` 返回的是**写死的 4 条元数据**，**根本没查库** → 运营在后台维护的模板**一个都看不到** | 旧实现是 `initTemplates()` + `template(...)` 硬编码 |
| 后果 3 | `buildPrompt()` **从头到尾没用过模板正文** → `useTemplate` / `templateId` 是两个**死参数** → 运营改模板对生成结果**零影响** | 旧 `buildPrompt(request)` 无模板段落 |

### 🔴 为什么「编译通过 + 单测全绿 + 80 道闸门全绿」都发现不了

| 现象 | 原因 |
|---|---|
| 编译通过 | `"amazon_product_template"` 只是个**字符串字面量**，编译器不管它存不存在 |
| 单测全绿 | 单测用 Mockito 把 Mapper **mock 掉了** → **查库那一步根本没执行** |
| 前 80 道闸门全绿 | 已有闸门查的是「内存存储冒充 DB」「硬编码常量」「SQL 契约」「枚举未校验」……**没有一道查「引用是否悬空」** |
| 接口 200 | `GET /api/v1/product-description/templates` 返回的是**写死的 4 条**，根本没查库 → 看起来一切正常 |

---

## 二、修复内容

### 2.1 产品代码：`description_template` 表接线（主线 A）

这是一张**有 DDL、有种子数据、但全仓没有任何一行代码读它**的表 → 现在接上了。

| 新增 | 作用 |
|---|---|
| `DescriptionTemplate`（Entity） | 严格对齐 DDL 列名 / 类型 / 可空性 |
| `DescriptionTemplateMapper` | MyBatis-Plus `BaseMapper` |
| `DescriptionTemplateService` / `…Impl` | `listEnabled` / `findByTemplateId` / `findDefault` —— **真查库**，查不到就返回空（**不编**） |
| `DescriptionTemplateRenderer` / `…Impl` | 把模板正文里的 `{占位符}` 换成**真实商品事实**；取不到值的占位符**原样保留**（不编假值） |

同时修掉的**三处真缺陷**（都在 `ProductDescriptionServiceImpl`）：

| # | 病灶 | 修法 |
|---|---|---|
| 1 | 内存 `templates` Map + 写死的模板元数据 | 模板改成**数据库里的事实**；`listTemplates()` 真查库，库空就返回空列表 |
| 2 | `map.put("tone", row.getLanguage())` —— **把「语言」当成「语气」返回**（`tone: "zh"`） | 表里**没有 tone 这一列** → **不返回 tone**（规则 72） |
| 3 | `buildPrompt` 里又调了一次 `resolveTemplate` → **同一个请求查两遍模板表**，两次可能查到不同的行 → 「写进响应的 templateId」和「真正进 prompt 的模板」**对不上** | 模板**只解析一次**，同时用于 prompt 与响应 |

还有一处**串台**（在 `DescriptionTemplateServiceImpl.findDefault`）：指定了平台却匹配不上时，旧逻辑会兜底返回**别的平台**的模板 → 明明要 Amazon 格式却按 Shopee 写文案，且响应里的 `templateId` 记的是 `shopee_template` → **追溯时完全看不出用错模板了**。现在：**返回空**（不串台）。

### 2.2 第 81 道闸门：`scripts/check-dangling-registry-ref.py`

判据（窄判据，宁可漏报也不误报）：

1. 从 `database/*.sql` 找出**登记表** = 「有 UNIQUE 列」+「该列有种子数据」的表；
2. 从种子值里**推导标识符形状**（后缀，如 `_template`）——
   ⚠️ 必须**所有**种子值都符合该后缀才认，否则一个表里混着别的形状就说明不是同一族；
3. 在 `src/main` + `src/test` 的 Java 里找**同形状的字符串字面量**，
   逐个核对是否在**合法值集合**里 → 不在 → **悬空引用**，报错。

📌 **三个关键设计**（都是踩过坑才定下来的）：

| 设计 | 为什么 |
|---|---|
| **注释挖空（保留行号）、字符串字面量原样保留** | 注释里写「以前是 amazon_product_template」是**说明历史**，不是引用 → 不挖空会报一堆噪音 → 闸门变哑（规则 99/102）。而**要报的正是字符串字面量本身** → 挖掉字符串闸门就永远没东西可报 |
| **连 `src/test` 一起扫** | 生产代码里那个悬空值是**注释里的一句历史**；真正写死它的是**单测**（`request.setTemplateId("amazon_product_template")`）→ 只扫 `src/main` 会漏掉一半，而**单测里那个值会原样落库** |
| **逐行显式豁免标记 `dangling-ref:allow`** | 单测里**故意**造一个不存在的值来验证「取不到就返回空」→ 那不是缺陷，是**测试意图**。豁免必须**逐行**（按文件豁免 = 整份文件都不查 → 真缺陷也漏） |

---

## 三、验证

### 3.1 单元测试（Mockito 只在 `src/test`）

| 文件 | 条数 | 守护什么 |
|---|---|---|
| `DescriptionTemplateServiceImplTest` | **7** | 大小写匹配（生产种 `Amazon`、请求传 `amazon`）／**不串台**／`selectOne` 不抛 TooManyResults／只用启用中的模板／空表返回空 |
| `DescriptionTemplatePromptTest` | **8** | 模板正文**真的进 prompt**／取不到值的占位符**原样保留**／null 模板返回空串／变量 JSON 解析／脏数据不炸／取模板顺序 |

📌 **为什么「进 prompt」用 Mockito、「落库」用 H2**：前者是**纯逻辑**（拼字符串）→ mock 掉模板服务即可；后者是**真落库** → 由 `ProductDescriptionPersistenceTest`（H2 + 真实 Mapper）负责 —— 两者分工不重叠（规则 12）。

### 3.2 反向验证（闸门必须红 → 还原必须绿）

| 注入 | 结果 |
|---|---|
| 把 `ProductDescriptionPersistenceTest` 的 `setTemplateId` 改回 `amazon_product_template` | ✅ **exit 1**，报 1 处：`…ProductDescriptionPersistenceTest.java:232 "amazon_product_template"（形状 _template）→ description_template.template_id 里没有这个值` |
| 还原 | ✅ **exit 0**，「未发现悬空引用」 |
| `git diff --stat` 确认工作树没被改脏 | ✅ 与注入前一致（4 insertions / 1 deletion） |

### 3.3 闸门自检

`python3 scripts/check-dangling-registry-ref.py --self-test` → ✅ **8 个用例**：
悬空必红 / 合法不红 / 行注释不红 / 块注释不红 / `src/test` 也红 / 混合形状不推导 / 豁免生效 / 豁免逐行。

### 3.4 全量

- `mvn -q -DskipTests compile` → ✅
- `mvn -pl erp-product-description-service -am test` → ✅ **81** 条全绿
- `mvn test`（全仓 29 个模块）→ ✅ **BUILD SUCCESS**
- 81 道闸门全跑 → ✅ 全绿；`check-gates-wired.py` → ✅ 81 个脚本全部被 workflow 引用

---

## 四、🔴 诚实残留（本闸门**抓不到**的东西）

这一节不是免责声明，是**下一轮的 backlog**。

| # | 残留 | 实测数据 | 后果 |
|---|---|---|---|
| 1 | **只认「同后缀」的值** | 当前只推导出 `_template` 一族（`description_template`，4 个合法值） | `SUFFIX_CANDIDATES` 里还有 `_dict` / `_type` / `_config` / `_code` / `_category`，但**当前仓库没有任何表**的种子值符合这些形状 → 这些候选**目前全是空转**。将来有表长成那样会自动生效 |
| 2 | **只认「有 UNIQUE 列 + 有种子」的表** | 全仓 **113** 张 DDL 表里，符合「有 UNIQUE 列 + 有种子数据」这个形状的有 **3** 张；但它们**只有 1 张**能推导出统一的后缀形状（`description_template`） | 那些「种子值形状不统一」「唯一性靠业务代码保证」的登记表**扫不到** → 悬空引用仍可能溜过去 |
| 3 | **只查「字符串字面量」** | 判据是 `"xxx_template"` 这种形状的字符串 | ID 若是**拼接**出来的（`prefix + "_template"`）→ **扫不到**（拼接形状无法静态判定） |
| 4 | **只扫 `src/main` + `src/test` 下的 Java** | 全仓 761 个 `.java` **全部**落在这两个目录内 | 将来若有 Java 源码放在别的目录（如 `src/it`）→ **扫不到** |
| 5 | **合法值集合来自 `database/*.sql` 的种子** | `description_template` 的 4 条种子在 `database/product-description-service.sql` | 若某表在**别处**（如 k8s ConfigMap 里的初始化 SQL）播种 → 本闸门**看不到**那些值 → 会**误报** |
| 6 | **没有数据库侧的兜底约束** | `description_template.template_id` 有 UNIQUE，但**外键**没有（模板表与业务表分库，不可能有 FK） | 本闸门是**唯一**防线 —— 闸门不跑，悬空引用照样能进库 |
| 7 | **存量脏数据没清洗** | 本次只挡住**新写入** | 修复前已经落库的 `amazon_product_template` 之类**仍然在表里** → 需要一条数据修复 SQL（本次**未做**） |
| 8 | **模板表没有管理界面** | `GET /templates` 接口通了，但前端 `erp-web/src/views/ai/Description.vue` **没有模板下拉框**（`descriptionApi.templates()` 全仓无调用方） | 运营**暂时无法**在页面上选模板；模板仍按平台自动匹配生效。要让运营真正用起来，还需要前端接上 |

---

## 五、怎么复现

```bash
cd ~/OmniTradeERP

# 闸门（人看）
python3 scripts/check-dangling-registry-ref.py

# 闸门自检
python3 scripts/check-dangling-registry-ref.py --self-test

# 看闸门推导出哪些「登记表 + 合法值集合」
python3 scripts/check-dangling-registry-ref.py | head -5

# 确认「有 DDL 无实体」的表（description_template 修完后应已消失）
python3 - <<'PY'
import os, re
os.chdir(os.path.expanduser("~/OmniTradeERP"))
ddl=set()
for f in os.listdir("database"):
    if f.endswith(".sql"):
        s=open(f"database/{f}",encoding="utf-8",errors="ignore").read()
        for m in re.finditer(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?[`\"]?([A-Za-z_][A-Za-z0-9_]*)[`\"]?", s, re.I):
            ddl.add(m.group(1).lower())
ent=set()
for root,dirs,files in os.walk("."):
    if "target" in root or "node_modules" in root or "/.git" in root: continue
    for fn in files:
        if fn.endswith(".java"):
            s=open(os.path.join(root,fn),encoding="utf-8",errors="ignore").read()
            for m in re.finditer(r"@Table(?:Name)?\s*\(\s*(?:name\s*=\s*)?\"([^\"]+)\"", s):
                ent.add(m.group(1).lower())
print("有DDL无实体:", sorted(t for t in ddl if t not in ent))
PY

# 模块测试
mvn -pl erp-product-description-service -am test
```

---

## 六、相关链接

- [🔢 枚举字段写库校验（第 80 道闸门）](ENUM-VALIDATION.md)
- [🏠 首页](Home.md)
- [🏗️ 架构设计](Architecture.md)
- [📡 API 文档](API-Documentation.md)
- [🔐 安全说明](SECURITY.md)
