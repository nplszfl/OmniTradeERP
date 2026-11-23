# 🔢 枚举字段写库校验（第 80 道闸门）

> 📌 **本文记录一个真实缺陷的修复**：枚举定义了合法取值，但写库前**没人校验** →
> 拼错的值**静默入库** → 报表/对账/审计**静默漏掉**这条单据，且**全程不报错**。
>
> 📌 **本文里的每个数字都是实测的**，不是估计的。复现命令都在下面。

---

## 一、缺陷本体

| 项 | 事实 | 怎么核实的 |
|---|---|---|
| 落库列 | `wms_inbound_order.inbound_type` / `wms_outbound_order.outbound_type` | `database/warehouse-service.sql` |
| 列类型 | `VARCHAR(30)` —— **没有 ENUM 约束、没有 CHECK 约束** | 同上；实测该文件里 `ENUM(` = False、`CHECK(` = False |
| 合法取值 | `InboundType`：PURCHASE / RETURN / TRANSFER / PRODUCTION / OTHER<br>`OutboundType`：SALE / TRANSFER / RETURN / SAMPLE / DAMAGE / OTHER | `erp-warehouse-service/.../enums/` |
| 缺陷 | `WarehouseServiceImpl` **import 了这两个枚举，却从不在正文里用它们** —— `createInboundOrder` / `createOutboundOrder` 直接把前端传来的字符串塞进实体 | 修复前的 `HEAD` 版本；`InboundType` / `OutboundType` 在正文里出现 **0** 次 |

### 🔴 为什么这在生产上是真事故，不是洁癖

1. **入库/出库类型直接决定「库存怎么动」**：
   `RETURN`（退货入库）必须让库存**增加**，`PURCHASE`（采购入库）走采购结算链路，
   `DAMAGE`（报损出库）要让库存**减少**。类型错了 → 库存方向就错了。
2. **一条拼错的 `'RETRUN'`（RETURN 的错字）能一路走到表里**：
   → 后续所有**按类型分组**的库存报表 / 对账 / 审计都**查不到它**
   → 单据在账面上「消失」，**而且没有任何报错**。
3. **编译器不会告诉你**：Java 里 **unused import 不是错误** →
   这类缺陷能一路**编译通过 → 通过全部单测 → 上线**，只有数据脏了才暴露。
4. **单测也不会告诉你**：字段是 **Jackson 直接绑定**到实体的
   （`@RequestBody` 里嵌着 `InboundOrder`）→ **不走 setter** → 单元测试里
   `setInboundType(...)` 什么都能设进去 → 测不出「该被拦的值」。

---

## 二、修复内容

### 2.1 产品代码：`WarehouseServiceImpl`

新增两个私有归一化方法，并在两个写库入口调用：

| 方法 | 作用 |
|---|---|
| `normalizeInboundType(String)` | 校验并归一化入库类型；空值/空白 → `OTHER`；非空但不合法 → 抛 `BusinessException` |
| `normalizeOutboundType(String)` | 同上，出库类型 |

📌 **两个容易漏掉的点（本修复特意钉住）**：

1. **必须把归一化后的值写回实体**。
   `InboundType.getByCode` 是**精确匹配**（`type.code.equals(code)`）。
   如果只校验不写回，前端传 `"purchase"` 会**通过校验**、然后以**小写**落库
   → 后续任何 `getByCode("purchase")` 都返回 `null`
   → **按类型分组的报表照样漏掉这条单据**。
   → 「校验放行了但没治好」**比不校验更坏**（它让人以为已经防住了）。
2. **空值不能只是「不报错」，必须真的落成 `OTHER`**。
   DDL 里该列可空（平台同步建单本来就不带类型），
   但库里留 `NULL` 的话，按类型分组的报表还是会漏这一行。

📌 **为什么空值落 `OTHER` 而不是直接拒绝**：
平台同步建单**不带类型**（见 `WarehouseRequiredFieldTest`「空明细是允许的」）
→ 直接拒绝会**打断平台同步**。`OTHER` 本来就是枚举里的「其他」兜底桶。

### 2.2 第 80 道闸门：`scripts/check-enum-field-validated.py`

判据（**unused import**，而不是「有没有 setter 调用」——后者对 Jackson 直绑的字段天生失明）：

1. 某模块的 `enums/` 包里定义了枚举 `E`；
2. 该模块的某个 `*ServiceImpl.java` **import 了 `E`**；
3. 但这个文件的**代码正文**里**从未出现过 `E`**（注释和字符串字面量不算）。

满足 ①②③ → **报红**。

---

## 三、验证

### 3.1 单元测试

`erp-warehouse-service/src/test/java/com/crossborder/erp/warehouse/InboundOutboundTypeValidationTest.java`
—— **11 条**（2 个 `@Nested` + 1 条顶层），Mockito 只在 `src/test`：

| 用例 | 断言 |
|---|---|
| `inboundTypeTypoRejected` | `'RETRUN'` → 抛 `BusinessException`，且 `inboundOrderMapper.insert` **从未被调用** |
| `outboundTypeTypoRejected` | `'SALEE'` → 同上 |
| `inboundTypeNullAllowed` / `inboundTypeBlankAllowed` | 空值/空白 → **真的落成 `OTHER`**（且 `getByCode` 查得到） |
| `outboundTypeNullAllowed` / `outboundTypeBlankAllowed` | 同上（出库侧） |
| `allLegalInboundTypesAccepted` / `allLegalOutboundTypesAccepted` | **全部合法枚举值**都放行（证明排除规则不是空洞的） |
| `inboundTypeCaseInsensitive` / `outboundTypeCaseInsensitive` | 小写输入 → **归一化成大写落库**（钉住「必须写回」） |
| `getByCodeReturnsNullForUnknown` | 枚举 `getByCode` 对未知值返回 `null`（不是抛异常） |

### 3.2 反向验证（闸门必须红 → 还原必须绿）

| 注入 | 结果 |
|---|---|
| 把 `WarehouseServiceImpl` 回退到缺陷态（`git show HEAD:...`） | ✅ **exit 1**，报 2 处（`InboundType` 第 9 行 / `OutboundType` 第 10 行） |
| 还原 | ✅ **exit 0**，「未发现可疑点」 |
| `diff -q` 确认工作树没被改脏 | ✅ IDENTICAL |

### 3.3 闸门自检

`python3 scripts/check-enum-field-validated.py --self-test` → ✅ **11 个用例 + 1 个白名单存在性检查**。

---

## 四、🔴 诚实残留（本闸门**抓不到**的东西）

这一节不是免责声明，是**下一轮的 backlog**。

| # | 残留 | 实测数据 | 后果 |
|---|---|---|---|
| 1 | **只认放在 `enums/` 包下的枚举** | `src/main` 里共 **34** 个枚举定义，**只有 18 个**在 `enums/` 包里；另 **16 个**在 `entity/` / `constant/` / `model/` 等目录 | 闸门**看不见**这 16 个枚举。已知漏网例子：`erp-tenant-service` 的 `TenantStatus` / `UserRole` / `TenantPlan` / `Bill` / `BillingRule` / `CredentialStatus`、`erp-common` 的 `OrderStatus` / `PlatformType`、`erp-social-commerce-service` 的 `TikTokOrder` / `TikTokProduct` / `Influencer` / `LiveStream` / `Cooperation` 等 |
| 2 | **只看 `*ServiceImpl.java`** | `src/main` 里共 **37** 个 `*ServiceImpl` 文件被扫 | 校验写在 Controller / Converter / Repository 层的**同类缺陷扫不到** |
| 3 | **白名单只认 `FinanceConverter` 一个类名** | `EXTERNAL_VALIDATORS` 当前只有 1 项 | 别的模块若用「Converter 里校验」的写法，会**误报**（宁可误报不可漏报，但仍是已知噪音） |
| 4 | **DDL 层没有硬约束兜底** | `database/warehouse-service.sql` 里 `ENUM(` = False、`CHECK(` = False | 本闸门是**唯一**防线 —— 闸门不跑，脏数据照样能进库。更稳的做法是**同时**把列改成 MySQL `ENUM(...)` |
| 5 | **历史脏数据没有清洗** | 本次只挡住**新写入**，没清存量 | 修复前已经落库的 `'RETRUN'` 之类**仍然在表里** → 需要一条数据修复 SQL（本次**未做**） |
| 6 | **闸门只覆盖 1 个模块** | 实测：37 个 `*ServiceImpl` 里，**只有 1 个** import 了同模块枚举（`erp-warehouse-service`） | 其余 36 个文件目前**没有**这种「import 悬空」的形状 → 闸门在那 36 个上**天然无信号**。这不是「它们都安全」，而是**「这一类缺陷目前只在这一个模块出现过」** |

---

## 五、怎么复现

```bash
cd ~/OmniTradeERP

# 闸门（人看）
python3 scripts/check-enum-field-validated.py

# 闸门自检
python3 scripts/check-enum-field-validated.py --self-test

# 确认枚举定义分布在哪些包（对应残留 #1）
grep -rn "public enum" --include="*.java" */src/main | wc -l

# 确认 DDL 没有硬约束（对应残留 #4）
grep -c "ENUM(\|CHECK(" database/warehouse-service.sql

# 模块测试
mvn -pl erp-warehouse-service -am test
```

---

## 六、相关链接

- [🏠 首页](Home.md)
- [🏗️ 架构设计](Architecture.md)
- [📡 API 文档](API-Documentation.md)
- [🔐 安全说明](SECURITY.md)
