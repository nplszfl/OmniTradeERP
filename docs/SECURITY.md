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
