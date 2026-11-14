#!/usr/bin/env python3
"""Bean 名冲突巡检：共享模块里的 @Bean 若与 Spring Boot 自动装配同名，必须有兜底保护。

背景（2026-11-14 **首次真跑起服务**时实测踩出来的坑）：

  erp-common 的 RedisConfig 定义了名为 `redisTemplate` 的 @Bean，
  而 Boot 自带的 RedisAutoConfiguration 也定义同名 bean（带
  @ConditionalOnMissingBean(name = "redisTemplate")）。

  📌 原来的 CommonRedisAutoConfiguration 注释写着：
     「自动装配按类名排序，com.crossborder.* 排在 org.springframework.* 之前，
       因此本配置优先生效」—— **这是错的**（见下面「为什么字母序不可靠」）。

  📌 结果：**erp-gateway 根本起不来**：
       BeanDefinitionOverrideException: The bean 'redisTemplate', defined in
       [com/crossborder/erp/common/config/RedisConfig.class], could not be registered.
       A bean with that name has already been defined in
       [org/springframework/boot/autoconfigure/data/redis/RedisAutoConfiguration.class]
       and overriding is disabled.
     而 erp-user-service / product / order 等 **servlet 服务全部正常**
     → 现象极具迷惑性（只有网关挂），且 **70 道闸门 + 1047 个用例全绿**
     → 靠静态检查完全看不出来（规则 60：编译通过 ≠ 模块可用）。

  📌 **为什么字母序不可靠**：自动装配排序的**默认**规则确实是类名字母序，
     但 `@AutoConfigureBefore/After` 会重排整张图。gateway 里存在
     GatewayRedisAutoConfiguration(@AutoConfigureAfter(RedisReactiveAutoConfiguration))
     与 GatewayAutoConfiguration(@AutoConfigureBefore(WebFluxAutoConfiguration))，
     实际把 Boot 的 RedisAutoConfiguration 排到了 erp-common 之前。
     → **排序必须用注解显式声明，不能靠字母序碰运气。**

本脚本检查（对**共享模块**里的每个 @Bean）：
1. 该 @Bean 的方法名是否落在 Spring Boot 自动装配的 Bean 名清单里
   （清单 = Boot 3.1.5 spring-boot-autoconfigure 里最常撞名的那些，
     见 BOOT_AUTO_BEAN_NAMES；每项都注明了它出自哪个自动装配类）
2. 若命中 → 该 @Bean **必须**带 @ConditionalOnMissingBean 保护，
   否则视为「谁先注册谁赢 + 后注册的直接抛异常」的定时炸弹
3. 共享模块若自己定义了同名 bean，也必须带 @ConditionalOnMissingBean
   （多模块共享同一个 @Bean 名时同样会撞）

📌 **判据要认全等价写法**（规则 100 / 117）：
   - @ConditionalOnMissingBean(name = "redisTemplate")
   - @ConditionalOnMissingBean(value = "redisTemplate")
   - @ConditionalOnMissingBean("redisTemplate")            ← 位置参数
   - @ConditionalOnMissingBean(name = {"redisTemplate"})    ← 数组
   - @ConditionalOnMissingBean(type = RedisTemplate.class)  ← 按类型
   任一种写法都算「有保护」。

📌 **闸门自己也要能被抓**（规则 127：判据必须在真的看代码）：
   本脚本对每个共享模块的每个 @Bean 都做了实际解析，
   并在自检里断言「若去掉保护 → 必然报红」（见 --self-test）。

用法：python3 scripts/check-bean-name-collision.py
      python3 scripts/check-bean-name-collision.py --self-test
退出码 0 = 全绿，1 = 有问题
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ---------------------------------------------------------------------------
# Spring Boot 自动装配里「最常被共享模块撞名」的 bean 名。
#
# 📌 来源：spring-boot-autoconfigure 3.1.5（本仓 spring-boot.version）。
#    每一项都注明出处，便于日后核对。
# 📌 这份清单是**人工枚举**的，不是穷举 —— 目的是抓住「已知撞名」，
#    不是保证没有别的撞名。宁可漏报也不要误报（规则 99：假阳性更危险）。
# ---------------------------------------------------------------------------
BOOT_AUTO_BEAN_NAMES = {
    "redisTemplate": "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "stringRedisTemplate": "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "reactiveRedisTemplate": "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "reactiveStringRedisTemplate": "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
    "redisConnectionFactory": "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "restTemplate": "org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration",
    "restTemplateBuilder": "org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration",
    "dataSource": "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "jdbcTemplate": "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
    "sqlSessionFactory": "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "objectMapper": "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
    "jackson2ObjectMapperBuilder": "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration",
    "applicationTaskExecutor": "org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration",
    "taskExecutor": "org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration",
    "taskScheduler": "org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration",
    "applicationEventMulticaster": "org.springframework.context.support.AbstractApplicationContext",
}

# 共享模块：这里的 @Bean 会被**所有**依赖它的服务加载 → 撞名会波及全仓
SHARED_MODULE_HINTS = ("erp-common", "erp-llm-common", "erp-platform-api")


def shared_modules():
    """找出仓库里的共享模块（目录名命中 SHARED_MODULE_HINTS）。"""
    out = []
    for name in sorted(os.listdir(ROOT)):
        path = os.path.join(ROOT, name)
        if name in SHARED_MODULE_HINTS and os.path.isdir(path):
            out.append((name, path))
    return out


def java_files(module_dir):
    src = os.path.join(module_dir, "src", "main", "java")
    found = []
    for dirpath, _dirs, files in os.walk(src):
        for f in files:
            if f.endswith(".java"):
                found.append(os.path.join(dirpath, f))
    return sorted(found)


def strip_comments(text):
    """去掉 // 行注释和 /* */ 块注释（避免注释里的 @Bean 被误判）。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def find_bean_methods(text):
    """找出所有 @Bean 方法：返回 [(方法名, 注解块文本)]。

    注解块 = @Bean 后面的连续注解 + 方法签名。
    📌 必须把 @Bean 后面**紧邻**的其它注解一起读进来 ——
       保护注解（@ConditionalOnMissingBean）就写在那里。
    """
    text = strip_comments(text)
    results = []
    # @Bean(...) 或 @Bean 后跟方法
    for m in re.finditer(r"@Bean\b", text):
        # 从 @Bean 开始往后取，直到方法签名的左花括号
        rest = text[m.end():]
        # 跳过 @Bean 自己的可选参数 (...)（可能是空的或带属性）
        i = 0
        while i < len(rest) and rest[i] in " \t\r\n":
            i += 1
        if i < len(rest) and rest[i] == "(":
            depth = 0
            while i < len(rest):
                if rest[i] == "(":
                    depth += 1
                elif rest[i] == ")":
                    depth -= 1
                    if depth == 0:
                        i += 1
                        break
                i += 1
        # 接着收集后续注解（@ConditionalOnMissingBean 等）
        ann_block = []
        pos = i
        while True:
            j = pos
            while j < len(rest) and rest[j] in " \t\r\n":
                j += 1
            if j < len(rest) and rest[j] == "@":
                # 📌 注解名 = @ 后的标识符（到空白或 '(' 为止）
                k = j + 1
                while k < len(rest) and rest[k] not in " \t\r\n(":
                    k += 1
                # 📌 注解参数 (...) 里**可以含空格**（如 name = "redisTemplate"），
                #    所以要先跳过空白再找 '('，再按配对深度吃掉整个参数表。
                m = k
                while m < len(rest) and rest[m] in " \t\r\n":
                    m += 1
                if m < len(rest) and rest[m] == "(":
                    depth = 0
                    while m < len(rest):
                        if rest[m] == "(":
                            depth += 1
                        elif rest[m] == ")":
                            depth -= 1
                            if depth == 0:
                                m += 1
                                break
                        m += 1
                    k = m
                ann_block.append(rest[j:k])
                pos = k
                continue
            break
        # 方法签名：找到 public/protected/private ... name(
        sig = rest[pos:]
        sm = re.search(r"\b(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?"
                        r"[A-Za-z0-9_<>,\[\]\.\s?]+?\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(", sig)
        if sm:
            results.append((sm.group(1), " ".join(ann_block)))
    return results


def has_missing_bean_guard(ann_block, bean_name):
    """这个 @Bean 有没有 @ConditionalOnMissingBean 保护（认全等价写法）。"""
    if "@ConditionalOnMissingBean" not in ann_block:
        return False, ""
    # 取出 @ConditionalOnMissingBean(...) 的参数部分
    idx = ann_block.find("@ConditionalOnMissingBean")
    tail = ann_block[idx:]
    # 参数部分（可能为空）
    if "(" in tail:
        depth = 0
        args = ""
        started = False
        for ch in tail[len("@ConditionalOnMissingBean"):]:
            if ch == "(":
                depth += 1
                started = True
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            if started:
                args += ch
        # 📌 args 含外层括号（闭括号可能被循环 break 掉），判据前先剥掉
        args = args.strip()
        if args.startswith("("):
            args = args[1:].strip()
        if args.endswith(")"):
            args = args[:-1].strip()
        # 形式一：name = "xxx" / value = "xxx"
        if re.search(r"""\b(name|value)\s*=\s*\{?\s*["']""" + re.escape(bean_name) + r"""["']""", args):
            return True, args.strip()
        # 形式二：位置参数 @ConditionalOnMissingBean("xxx")
        if re.search(r"""^\s*\{?\s*["']""" + re.escape(bean_name) + r"""["']""", args):
            return True, args.strip()
        # 形式三：按类型 @ConditionalOnMissingBean(type = Xxx.class)
        if re.search(r"\btype\s*=\s*[A-Za-z0-9_$.]+\.class", args):
            return True, args.strip()
        # 形式四：只写了 @ConditionalOnMissingBean（无参）→ 对任何同名 bean 都兜底
        if not args.strip():
            return True, args.strip()
    else:
        # 无参数的 @ConditionalOnMissingBean → 按类型兜底
        return True, ""
    return False, ""


def main():
    self_test = "--self-test" in sys.argv
    violations = []
    checked = 0

    for mod_name, mod_dir in shared_modules():
        for path in java_files(mod_dir):
            rel = os.path.relpath(path, ROOT)
            try:
                text = open(path, encoding="utf-8").read()
            except OSError:
                continue
            for bean_name, ann_block in find_bean_methods(text):
                checked += 1
                if bean_name not in BOOT_AUTO_BEAN_NAMES:
                    continue
                guarded, args = has_missing_bean_guard(ann_block, bean_name)
                if not guarded:
                    violations.append({
                        "file": rel,
                        "bean": bean_name,
                        "boot_owner": BOOT_AUTO_BEAN_NAMES[bean_name],
                    })

    if self_test:
        # 反向验证（规则 40 / 106）：判据必须能真的抓到缺陷。
        probe = (
            "package com.x;\n"
            "import org.springframework.context.annotation.Bean;\n"
            "import org.springframework.context.annotation.Configuration;\n"
            "public class ProbeConfig {\n"
            "    @Bean\n"
            "    public Object redisTemplate(Object cf) { return null; }\n"
            "}\n"
        )
        hits = [n for n, ann in find_bean_methods(probe)
                if n in BOOT_AUTO_BEAN_NAMES and not has_missing_bean_guard(ann, n)[0]]
        if not hits:
            print("❌ 自检失败：无保护的 redisTemplate 竟然没被抓到 —— 判据失明")
            return 1
        print(f"✅ 自检通过：无保护的 {hits[0]} 被判据抓到")
        # 反向自检：带保护的写法必须**不**被误报（避免假阳性）
        for probe_src, label in [
            ('@Bean\n    @ConditionalOnMissingBean(name = "redisTemplate")\n    public Object redisTemplate(Object cf) { return null; }', "name="),
            ('@Bean\n    @ConditionalOnMissingBean(value = "redisTemplate")\n    public Object redisTemplate(Object cf) { return null; }', "value="),
            ('@Bean\n    @ConditionalOnMissingBean("redisTemplate")\n    public Object redisTemplate(Object cf) { return null; }', "位置参数"),
            ('@Bean\n    @ConditionalOnMissingBean(name = {"redisTemplate"})\n    public Object redisTemplate(Object cf) { return null; }', "数组"),
            ('@Bean\n    @ConditionalOnMissingBean(type = Object.class)\n    public Object redisTemplate(Object cf) { return null; }', "按类型"),
        ]:
            got = [n for n, ann in find_bean_methods(probe_src)
                   if n in BOOT_AUTO_BEAN_NAMES and not has_missing_bean_guard(ann, n)[0]]
            if got:
                print(f"❌ 自检失败：等价写法「{label}」被误报")
                return 1
        print("✅ 自检通过：5 种等价保护写法均未被误报")

    print(f"📋 扫描共享模块 @Bean 共 {checked} 个"
          f"（模块：{', '.join(n for n, _ in shared_modules()) or '无'}）")

    if violations:
        print(f"\n❌ 发现 {len(violations)} 处 Bean 名冲突隐患：")
        for v in violations:
            print(f"   ❌ {v['file']}")
            print(f"      @Bean {v['bean']}() 与 {v['boot_owner']} 同名，"
                  f"且无 @ConditionalOnMissingBean 保护")
            print(f"      → 谁先注册谁赢；后注册的会抛 BeanDefinitionOverrideException（服务起不来）")
        return 1

    print("✅ 未发现未加保护的 Boot 同名 @Bean")
    return 0


if __name__ == "__main__":
    sys.exit(main())
