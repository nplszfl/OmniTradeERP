# 账户系统鉴权（Security）

> 📌 **2026-11-12 修复了一处 Critical 级漏洞。** 本文既是修复记录，也是**其余服务的整改模板**。

## 一、漏洞（修复前）

### 现象

`POST /api/user/{id}/reset-password` **任何人**都能调，而且**新密码由调用方指定**：

```bash
# 完全没有 token，服务器照单全收
curl -X POST "https://erp.example.com/api/user/1/reset-password?defaultPassword=attacker"
# → 管理员(admin)的密码被改成了 attacker
curl -X POST "https://erp.example.com/api/v1/auth/login" \
     -d '{"username":"admin","password":"attacker"}'
# → 以管理员身份登录成功
```

### 根因（四个缺口叠在一起）

| # | 缺口 | 位置 |
|---|---|---|
| 1 | `anyRequest().permitAll()` —— **整个账户系统一个请求都不用带 token** | `erp-user-service/.../config/SecurityConfig.java` |
| 2 | 重置密码的**新密码由调用方指定**（`defaultPassword` 请求参数） | `UserController.resetPassword` |
| 3 | 网关**不做鉴权**，且是 `type: LoadBalancer` + Ingress → **外部可达** | `erp-gateway`、`k8s/gateway.yaml` |
| 4 | 全仓 **0 个 `@PreAuthorize` / `hasRole`** —— RBAC 数据模型（`t_role` / `t_user_role` / `t_permission`）**建了但从没用过** | 全仓 |

**完整攻击链**：注册（`POST /api/v1/auth/register` 也是 permitAll）→ 重置管理员密码 → 以管理员登录。

### 影响面不止重置密码

`permitAll` 意味着这些**全部**无需认证：

- `GET  /api/user/page` → **用户表可被枚举**（用户名、邮箱、手机号）
- `POST /api/user` → **可任意造账号**
- `PUT  /api/user/{id}/status` → **可禁用他人账号**（DoS）
- `DELETE /api/user/{id}` → **可删账号**
- `POST /api/user/{id}/roles` → **可给自己授予管理员角色**
- `POST /api/role` `DELETE /api/role/{id}` → **可改角色定义本身**

### 修复前的**检查方式**（怎么自己复现）

```bash
# 不带任何 token
curl -i -X POST http://localhost:8080/user/1/reset-password
# 修复前：200 / 修复后：401
```

## 二、修复

### 1. 认证：JWT 过滤器（`UserJwtAuthenticationFilter`）

- 抄本仓已有的 `erp-tenant-service/TenantJwtAuthenticationFilter`（同一套「过滤器 + SecurityContext」范式）
- 📌 **过滤器必须放在本服务包下**（`com.crossborder.erp.user.*`）——
  各服务的 `@SpringBootApplication` 只扫自己的子包 → 放 `erp-common` 里**不会被加载**（规则 21）

### 2. 授权：**按库里的真实角色**给权限

```java
// t_user_role → t_role.role_code → ROLE_SUPER_ADMIN
```

📌 **查库而不是信 token**：token 里只有 userId/username，没有角色；
实时查 → **管理员在界面上改掉某人的角色立刻生效**，不需要等对方重新登录。
📌 **查不到角色 = 没有权限**（返回空列表，**绝不退化成「放行」**）。

### 3. 规则：`SecurityConfig`

```java
.requestMatchers("/api/v1/auth/**").permitAll()   // 登录 / 注册 / 刷新
.requestMatchers("/actuator/**").permitAll()
.anyRequest().authenticated()                     // 其余一律要认证
```

📌 **必须配 `AuthenticationEntryPoint`**：不配的话未认证请求返回 **403 + 空 body**
（没有 httpBasic/formLogin 触发 401）→ 前端只看到一个空 body，排障时完全看不出发生了什么。

### 4. 敏感写操作：`@PreAuthorize`

```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@PostMapping("/{id}/reset-password")
```

📌 **角色编码必须和种子数据一致**：种子里的编码是 **`SUPER_ADMIN`**（不是 `ADMIN`）！
写错了**管理员会被自己的代码锁在门外**（全部 403）→ 这条由**第 59 道闸门**守着。

### 5. 身份只认 token

`changePassword` 原来「body 里的 userId 优先、token 兜底」→ 登录用户可以指定**任意 userId**。
现在：**token 才是身份**；body 里带了不同的 userId → 直接报错。

### 6. 状态码要诚实

`AccessDeniedException` 是在 DispatcherServlet **里面**抛的 → 先被 `@ControllerAdvice` 接住
→ 走不到上游的 `ExceptionTranslationFilter` → **「没有权限」被报成 500「系统繁忙」**。

✅ `UserSecurityExceptionHandler`（`@Order(HIGHEST_PRECEDENCE)`）把它还原成 **403**。
📌 **不能加到 erp-common**：其余 23 个模块没有 spring-security 依赖，加了编译不过；
而且**一旦给它们引上 security starter**，Boot 的默认安全配置会要求所有接口都带 Basic Auth
→ **全线 401**（比现在还糟）。所以**逐个服务加固**。

### 7. 口令不走 URL 查询串（2026-11-12 修，原「残余风险 6」）

重置密码的口令原来用 **`@RequestParam`** → 调用方可以写
`POST /api/user/1/reset-password?defaultPassword=Secret123`
→ nginx / ingress / 网关的访问日志格式里有 **`$request` = 完整请求行（含 query string）**
→ **明文口令进访问日志**（也可能进浏览器历史、代理日志、审计日志）。

✅ 现在口令**只认请求体**（`@RequestBody(required = false) ResetPasswordRequest`）：

```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
@PostMapping("/{id}/reset-password")
public void resetPassword(@PathVariable Long id,
                          @RequestBody(required = false) ResetPasswordRequest request) {
    userService.resetPassword(id, request == null ? null : request.getDefaultPassword());
}
```

📌 **走 query string 的口令会被忽略**（不报错、不生效）→ URL 里再也不可能带出口令。
📌 body **可选** → 前端不传时仍是「重置为默认口令」，契约兼容。
📌 前端 `userApi.resetPassword(id, defaultPassword?)` 同步改成发 body。

**反向验证**（`UserAuthzTest`）：

| 注入 | 结果 |
|---|---|
| 改回 `@RequestParam String defaultPassword`（原始缺陷） | ✅ exit 1（`resetPasswordViaQueryStringIsIgnored` 报红：「query string 里的口令居然生效了」） |
| 还原 | ✅ exit 0（15/15 全绿） |

📌 **这条只修了「传输通道」**；口令值弱（兜底常量 `DEFAULT_PASSWORD`）是**风险 2**，仍未修。

## 三、第 59 道闸门 `scripts/check-account-authz.py`

| 断言 | 内容 |
|---|---|
| **C1** | 账户服务不许出现 `anyRequest().permitAll()`；必须有 `authenticated()` + `AuthenticationEntryPoint` + JWT 过滤器 |
| **C2** | 敏感写操作（重置密码 / 删人 / 建人 / 改状态 / 分配角色 / 角色增删改 / 部门增删改）**必须**有 `@PreAuthorize` |
| **C3** | `@PreAuthorize` 里的角色编码必须**存在于种子数据的 `t_role.role_code`** |

📌 **C3 是实战里救过命的**：第一版写的是 `hasRole('ADMIN')`，而种子里的编码是 `SUPER_ADMIN`
→ 上线后管理员会被锁在门外。**静态检查发现不了**（两边看起来都「对」）。

### 反向验证（注入原始缺陷 → 闸门必须变红）

| 注入 | 结果 |
|---|---|
| 恢复 `anyRequest().permitAll()` | ✅ exit 1（C1） |
| 删掉 `resetPassword` 的 `@PreAuthorize`（**签名跨行**） | ✅ exit 1（C2） |
| 角色码改成种子里不存在的 `SUPERADMIN` | ✅ exit 1（C3） |
| 还原 | ✅ exit 0 |

## 四、**还没加固的服务**（⚠️ 重要）

全仓 27 个模块里**只有 `erp-user-service` 做了鉴权**。
其余 25 个模块**连 spring-security starter 都没引** → **接口全开**。

⚠️ **不能一次性统一引 starter**：Spring Boot 一旦在 classpath 上检测到 security，
就会启用默认安全配置 → **所有接口都要求 Basic Auth** → 全线 401。

✅ **正确做法**：逐个服务加，顺序建议

1. `erp-user-service` ✅（已做，账户系统）
2. `erp-order-service` / `erp-product-service` / `erp-inventory-service`（**钱和货**）
3. `erp-finance-service`（**账**）
4. `erp-warehouse-service`
5. 其余

📌 每个服务要做的四件事（照 `erp-user-service` 抄）：
① JWT 过滤器（放本服务包下） ② `anyRequest().authenticated()` + `AuthenticationEntryPoint`
③ 敏感写操作 `@PreAuthorize` ④ 本地 `@RestControllerAdvice`（`@Order(HIGHEST_PRECEDENCE)`）把
`AccessDeniedException` 还原成 403

📌 **网关也建议加一层**（`erp-gateway` 目前零鉴权）—— 纵深防御：
服务层万一漏了，网关还能挡一道。

## 五、相关测试

`erp-user-service/src/test/java/.../UserAuthzTest.java` —— **14 条**，覆盖：
匿名请求被挡（401）、非管理员被挡（403）、管理员放行（200）、
**伪造 token 被挡**、**body 里写别人的 userId 被挡**、**登录注册仍然匿名可用**。

📌 **测试用真实 token**（不是 mock principal）—— mock principal 只证明「有个认证上下文」，
不证明**过滤器验签 → 查库取角色 → 授权判定**这条链路是通的（规则 48 / 127）。

## 六、**还没修完的残余风险**（⚠️ 2026-11-12 复核后如实记录）

> 📌 这一节是**复核第 59/60 道闸门时新发现的**。前面的修复把「未认证即可接管账号」
> 这条 Critical 路径堵住了，但**还有两条通往同一结果的路径没堵** —— 如实记在这里，
> 不藏。整改清单和第 60 道闸门的「已知待整改」名单一一对应。

### 风险 1：种子账号的初始密码是**写在仓库里的通用口令** `admin123`

| 事实 | 位置 |
|---|---|
| 所有种子用户（admin / 租户用户）的密码都是 `admin123` 的 BCrypt 哈希 | `database/seed-data.sql`、`database/seed-tenant.sql`、`database/user-service.sql` |
| 明文口令直接写在 SQL 注释里 | 同上（`-- 密码 admin123（BCrypt）`） |
| **没有任何强制改密机制** | 全仓无 `must_change_password` 字段、无首次登录提示、无密码过期策略 |

→ **攻击路径**：拿到仓库（或备份、镜像、任何泄露点）的人 → 直接用 `admin123` 登录 admin
→ **拿到的是完整的管理员权限**。
📌 鉴权加固（`authenticated()` + `@PreAuthorize`）**挡不住这条** —— 因为这条路径**本来就是带着合法口令进来的**。
📌 与 JWT 密钥是同一类问题的两面：**密钥公开 = 不用登录就能冒充；口令公开 = 不用知道密钥也能登录**。

**整改方向（待决策）**：
- ① **部署期随机生成初始口令**（跟 `JWT_SECRET` 一个套路：`k8s/deploy.sh` 里 `openssl rand` 生成 → 写进 Secret → 首次登录强制改密）
- ② 加 `must_change_password` 列 + 首次登录强制跳转改密
- ③ 至少：**把明文口令从 SQL 注释里删掉**（注释里的明文 = 明文，bcrypt 存了也没用）

### 风险 2：重置密码的**兜底口令**是硬编码常量

```java
// erp-user-service/.../service/impl/UserServiceImpl.java:46
private static final String DEFAULT_PASSWORD = "admin123";
```

`resetPassword(id, defaultPassword)` 在**调用方没指定**新密码时用这个兜底值
→ 管理员重置后，用户拿到的又是**仓库里公开的那个通用口令**。

📌 **这 1 处已登记进第 60 道闸门的「已知待整改」名单**（`KNOWN_PENDING_JAVA_PWD`），
**整行精确匹配**（规则 139：豁免判据不能是子串，否则会「自己豁免自己」）。
第 60 道闸门同时加了 **C5**：Java 里任何**新增的** `*PASSWORD*` 字面量常量一律报错
（差分验证：注入 `FALLBACK_PASSWORD = "P@ssw0rd2026"` → 闸门 exit 1 并指名行号；
占位符 `CHANGE_ME` → 不报）。

**整改方向（待决策）**：
- 兜底口令改成**随机生成**并**回传给操作人**（管理员看到后转告用户）→ 需要改 service 返回值 + controller + 前端提示
- 或：兜底直接**强制调用方必须指定**，缺参数就报错（不再有默认值）

### 风险 3：数据库口令仍是硬编码（**另一个档位**，非身份冒充）

`password: root` / `${MYSQL_PASSWORD:root}` —— 13 个服务 + `k8s/mysql.yaml` + `docker-compose`。
→ 内网可达、不直接等于「能冒充身份」→ 与上面两条**不同档**。
📌 第 60 道闸门**刻意不把它算进来**（算进来 = 一上来红 28 处 → 逼下一个人去「放松闸门」→ 规则 99）。
📌 整改清单见本节末。

### 风险 4：**网关零鉴权** + `type: LoadBalancer` + Ingress

服务层现在有鉴权了，但**网关这一层仍然是敞开的** → 纵深防御缺一层。
→ 本轮已在第四节记为「建议」；若网关对外暴露，应优先补。

### 风险 5：其余 24 个服务**连认证框架都没有**

全仓 27 个模块里只有 `erp-user-service` / `erp-tenant-service` 引了 `spring-boot-starter-security`，
其余 24 个服务**任何请求都不用带 token**。
📌 **不能给它们统一引 starter**（一旦 classpath 上出现 security，Spring Boot 会启用默认配置
→ 全线要 Basic Auth → 比现在更糟）→ **必须逐个服务加**，清单见第四节。

---

### ~~风险 6：重置密码的口令可以走 **URL 查询串** → 明文进 nginx 访问日志~~ ✅ **已修（2026-11-12）**

> 📌 **这条已从「残余风险」里划掉** —— 现实变了，文档必须跟着改（否则文档就从「风险清单」变成「历史读物」）。
> 修法与反向验证见**第二节第 7 条**「口令不走 URL 查询串」。
> 📌 修的只是**传输通道**；口令值弱（兜底常量 `DEFAULT_PASSWORD`）是**风险 2**，仍未修。

---

### 📌 整改清单（第 60 道闸门「已知边界」的落点）

| # | 风险 | 档位 | 建议动作 | 状态 |
|---|---|---|---|---|
| 1 | 种子账号初始口令 `admin123` + 无强制改密 | **高**（拿到仓库 = 直接登录 admin） | 部署期随机生成 + 首次登录强制改密 | ⬜ 待决策 |
| 2 | `DEFAULT_PASSWORD` 兜底常量 | 中 | 改随机生成并回传 | ⬜ 已登记在闸门名单 |
| 3 | 数据库口令硬编码（28 处） | 中低（内网） | 统一走 Secret 注入 | ⬜ 清单已列 |
| 4 | 网关零鉴权 | 中 | 网关加一层 JWT 校验 | ⬜ 待决策 |
| 5 | 24 个服务无认证 | **高**（按服务逐个） | 照 `erp-user-service` 抄 | ⬜ 进行中 |
| 6 | 重置口令可走 URL query → 明文进访问日志 | 中（需 ADMIN 角色；前端目前不踩） | 改 `@RequestBody`；或服务端随机生成只回传一次 | ✅ **已修**（2026-11-12，口令只认请求体；反向验证见第二节第 7 条） |

📌 **第 60 道闸门只守住第 2 条**（且是「新增的不许再犯」）；
第 1/3/4/5 条是**它够不着的地方** —— 如实记在文档里，而不是硬塞进闸门凑数（规则 42 / 55）。
