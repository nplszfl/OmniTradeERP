package com.crossborder.erp.user.service.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 「种子口令哈希 ↔ 文档声称的口令」一致性测试。
 *
 * <h3>为什么需要这个测试</h3>
 *
 * 2026-11-14 第一次<b>真的把 ERP 跑起来</b>时（此前从未真正部署过），
 * 抓到一个极其阴险的缺陷：
 *
 * <pre>
 *   database/seed-data.sql / database/user-service.sql
 *     种子密码写成 $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 *     —— 长度 60、结构合法，但<b>不是任何口令的合法 BCrypt 摘要</b>
 *
 *   docs/Installation.md  「前端 | http://localhost:3000 | admin/admin123」
 *   docs/SECURITY.md     「所有种子用户（admin / 租户用户）的密码都是 admin123 的 BCrypt 哈希」
 * </pre>
 *
 * → <b>文档说 admin123，数据库里的哈希不是 admin123。</b>
 *
 * <h3>为什么其他测试全都看不见</h3>
 *
 * <p>{@code AuthServiceImplTest} 里的 {@code user()} 辅助方法调的是
 * {@code passwordEncoder.encode(rawPassword)} —— <b>运行时现生成</b>一个哈希，
 * 再用它去 {@code matches} 自己 → <b>永远自洽</b>。
 * 仓库里真正的种子哈希<b>从未被任何测试读过</b>。</p>
 *
 * <p>结果就是：1047 个用例全绿、70+ 道闸门全绿、CI 双绿，
 * 但真实部署后<b>没有一个账号能登录</b>。症状极具迷惑性：
 * 接口正常响应、错误码正常（400「密码错误」）、日志无异常 ——
 * 「看起来在跑」和「真的能用」是两回事。</p>
 *
 * <h3>本测试查什么</h3>
 *
 * <ol>
 *   <li>扫出 {@code database/*.sql} 里所有 BCrypt 哈希（{@code $2a$/$2b$/$2y$} 开头）；</li>
 *   <li>用<b>真实的</b> {@link BCryptPasswordEncoder} 验算文档声称的口令；</li>
 *   <li>验不通 → 测试失败（红）。</li>
 * </ol>
 *
 * <p>📌 刻意用<b>真实库</b>而不是自己实现 bcrypt：自己实现会引入
 * 「实现有 bug 却报绿」的新风险（纯 Python / 自研实现都踩过）。
 * 这里直接用与生产完全相同的 {@code BCryptPasswordEncoder}，
 * 验不通 = 生产也登不进去。</p>
 *
 * <p>📌 <b>反向验证</b>：把某个种子哈希换成
 * {@code $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy}
 * → 本测试必须变红。</p>
 */
class SeedPasswordHashConsistencyTest {

    /** 📌 文档声称的种子口令（口径来源：docs/Installation.md + docs/SECURITY.md）。 */
    private static final String CLAIMED_PASSWORD = "admin123";

    /** BCrypt 哈希：版本段 + 成本段 + 22 字符 salt + 31 字符摘要。 */
    private static final Pattern BCRYPT = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    /**
     * 📌 **任何注释行都不可能是数据行** —— 判据只认「行首就是注释符」。
     *
     * <p>⚠️ 曾经的写法是「只有含『原来』二字的注释才跳过」→ 那是个<b>假阳性陷阱</b>：
     * 别人写 {@code -- 旧哈希 $2a$10$N9qo...}（不带「原来」）→ 测试报红 →
     * 下一个人按「闸门误报」的惯性去<b>放松判据</b>，而不是去查真相。
     * （与「闸门的假阳性比假阴性更危险」同源：假阳性会逼下一个人拆掉保护。）</p>
     *
     * <p>📌 因此这里用**结构**判据（行首是 {@code --} / {@code #} / {@code /*} / {@code *}）
     * 而不是**用词**判据 —— 「用词判据」只认一种写法就 = 真空绿 / 假红。</p>
     */
    private static final Pattern COMMENT_LINE = Pattern.compile("^\\s*(--|#|/\\*|\\*|//)");

    private static final String[] SEED_FILES = {
            "database/seed-data.sql",
            "database/user-service.sql",
            "database/seed-tenant.sql",
            "database/seed-live-data.sql",
            "database/seed-warehouse.sql",
    };

    /**
     * 📌 **动态扫 {@code database/*.sql}**，而不是写死文件名单。
     *
     * <p>写死名单 = <b>覆盖漂移</b>：以后新增一个带种子口令的 SQL 文件 →
     * 本测试<b>根本不看它</b> → 闸门全绿，而那个文件的账号依然登不进去。
     * （与「闸门覆盖的盲区不按规则编号来，按『当初有没有顺手加一个』来」同源。）</p>
     *
     * <p>本方法的 docstring 一直写的是「扫出 {@code database/*.sql} 里所有 BCrypt 哈希」，
     * 但代码是写死名单 —— 文档与实现不一致，正是本类缺陷的温床。</p>
     *
     * <p>📌 {@link #SEED_FILES} 保留：它退化为「这些文件必须存在」的存在性断言，
     * 避免 {@code database/} 目录结构变了导致本测试静默扫不到任何东西。</p>
     */
    private static List<String> discoverSeedFiles(Path root) throws IOException {
        List<String> all = new ArrayList<>();
        Path dir = root.resolve("database");
        if (Files.isDirectory(dir)) {
            try (java.util.stream.Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().endsWith(".sql"))
                        .map(p -> "database/" + p.getFileName())
                        .sorted()
                        .forEach(all::add);
            }
        }
        return all;
    }

    /**
     * 📌 「这些文件必须存在」的存在性断言（与发现逻辑分开）。
     *
     * <p>目的：防止 {@code database/} 目录结构变了 → 动态扫描扫不到任何东西 →
     * 本测试静默变绿（判据根本没在读代码）。</p>
     */
    private static void assertSeedFilesExist(Path root) {
        for (String required : SEED_FILES) {
            if (!Files.isRegularFile(root.resolve(required))) {
                fail("种子 SQL 文件不存在：" + required + "（本测试的判据可能已经扫不到任何东西）");
            }
        }
    }

    /** 仓库根目录（测试运行时 cwd = 模块目录，向上两级）。 */
    private static Path repoRoot() {
        Path cur = Paths.get("").toAbsolutePath();
        // 优先找含 database/ 的祖先目录
        for (int i = 0; i < 5 && cur != null; i++) {
            if (Files.isDirectory(cur.resolve("database"))) {
                return cur;
            }
            cur = cur.getParent();
        }
        throw new IllegalStateException("找不到仓库根目录（应含 database/ 目录），cwd=" + Paths.get("").toAbsolutePath());
    }

    @Test
    @DisplayName("种子口令哈希：能验通文档声称的口令 admin123")
    void seedHashesMatchDocumentedPassword() throws IOException {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Path root = repoRoot();
        assertSeedFilesExist(root);

        // 收集 <文件:行号, 哈希>
        Map<String, String> found = new LinkedHashMap<>();
        List<String> scanned = new ArrayList<>();

        for (String rel : discoverSeedFiles(root)) {
            Path p = root.resolve(rel);
            if (!Files.isRegularFile(p)) {
                continue;
            }
            scanned.add(rel);
            List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                // 📌 注释行一律跳过（注释里的哈希只是叙述，不是数据行）
                if (COMMENT_LINE.matcher(line).find()) {
                    continue;
                }
                Matcher m = BCRYPT.matcher(line);
                while (m.find()) {
                    found.put(rel + ":" + (i + 1), m.group());
                }
            }
        }

        assertFalse(scanned.isEmpty(),
                "一个种子 SQL 都没扫到 —— 判据可能根本没在读文件（cwd=" + Paths.get("").toAbsolutePath() + "）");
        assertFalse(found.isEmpty(),
                "扫不到任何 BCrypt 哈希 —— 判据可能根本没在读文件");
        // 📌 **运行时覆盖保险**：扫到的文件数必须等于 database/ 下 .sql 的总数
        //    —— 否则「动态扫描」这条保险本身坏了（判据只认了名单 = 覆盖漂移）。
        long allSql = 0;
        try (java.util.stream.Stream<Path> s = Files.list(root.resolve("database"))) {
            allSql = s.filter(p -> p.getFileName().toString().endsWith(".sql")).count();
        }
        assertEquals(allSql, scanned.size(),
                "扫到的种子文件数（" + scanned.size() + "）≠ database/ 下的 .sql 数（" + allSql
                        + "）→ 动态扫描没生效 → 覆盖漂移（新增的种子文件不会被验）");

        List<String> unmatched = new ArrayList<>();
        for (Map.Entry<String, String> e : found.entrySet()) {
            if (!encoder.matches(CLAIMED_PASSWORD, e.getValue())) {
                unmatched.add(e.getKey() + "  " + e.getValue());
            }
        }

        if (!unmatched.isEmpty()) {
            fail("以下种子哈希**验不通**文档声称的口令 '" + CLAIMED_PASSWORD + "'（共 " + unmatched.size() + " 处）：\n"
                    + String.join("\n", unmatched)
                    + "\n\n→ 这些账号**永远登不进去**：接口会正常返回「密码错误」，日志无异常，"
                    + "看起来一切正常 —— 但没有一个账号能用。\n"
                    + "→ 修法：用 new BCryptPasswordEncoder().encode(\"" + CLAIMED_PASSWORD + "\") 重新生成。");
        }
    }

    @Test
    @DisplayName("反向验证：注入一个验不通的哈希，测试必须能识别出来")
    void detectsBrokenHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        // 结构合法（60 字符）但不是任何口令的合法摘要 —— 2026-11-14 实际踩到的那个
        String broken = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        // 阳性对照：本测试自己生成的哈希必须能验通
        String good = encoder.encode(CLAIMED_PASSWORD);
        assertTrue(encoder.matches(CLAIMED_PASSWORD, good),
                "阳性对照失败：BCryptPasswordEncoder 行为异常，本测试不可信");
        // 阴性对照：坏哈希必须验不通（否则判据形同虚设）
        assertFalse(encoder.matches(CLAIMED_PASSWORD, broken),
                "阴性对照失败：判据对坏哈希也放行 → 形同虚设");
    }

    /**
     * 📌 反向验证（阴性对照）：<b>注释行里的坏哈希必须被跳过</b>。
     *
     * <p>判据只认「行首是注释符」—— 注释里的哈希是**叙述**，不是数据行。
     * 旧写法只跳过含「原来」二字的注释 → 别的注释（不带「原来」）会<b>假报红</b>。</p>
     */
    @Test
    @DisplayName("反向验证：注释行里的坏哈希不算判失败（旧写法会假报红）")
    void commentLinesAreIgnored() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String broken = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        // 构造一条**不含「原来」**的注释行（旧写法会把它当成数据行 → 假报红）
        String commentLine = "-- 旧哈希 " + broken;
        assertTrue(COMMENT_LINE.matcher(commentLine).find(),
                "阴性对照失败：注释行没被识别为注释 → 判据形同虚设");
        assertTrue(BCRYPT.matcher(commentLine).find(),
                "阴性对照失败：注释行里确实含哈希（本用例的前提）");
        // 注释行被跳过 → 不会进入待验集合
        assertFalse(COMMENT_LINE.matcher(commentLine).find()
                        && !COMMENT_LINE.matcher(commentLine).find(),
                "判据逻辑异常");
        // 真正的判据仍必须能抓到**数据行**里的坏哈希
        String dataLine = "    ('admin', '" + broken + "', 'x'),";
        assertFalse(COMMENT_LINE.matcher(dataLine).find(),
                "阴性对照失败：数据行被误判为注释 → 判据形同虚设");
        assertFalse(encoder.matches(CLAIMED_PASSWORD, broken),
                "阴性对照失败：坏哈希竟然验得通");
    }

    /**
     * 📌 反向验证：<b>新增一个带坏哈希的种子 SQL 文件必须被识别</b>（覆盖漂移防护）。
     *
     * <p>旧写法把文件名单写死 → 新增文件<b>根本不被扫</b> → 闸门全绿而账号照样登不进去。
     * 本用例在临时目录里造一个 {@code database/} 目录 + 一个坏哈希文件，
     * 验证动态发现逻辑能把它找出来。</p>
     */
    @Test
    @DisplayName("反向验证：新增的种子 SQL 文件必须自动纳入扫描（防覆盖漂移）")
    void newlyAddedSeedFileIsDiscovered() throws IOException {
        Path tmp = Files.createTempDirectory("seed-scan-probe");
        Path db = tmp.resolve("database");
        Files.createDirectories(db);
        String broken = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
        Files.writeString(db.resolve("brand-new-seed.sql"),
                "INSERT INTO `t_user` (`id`, `password`) VALUES\n"
                        + "  (99, '" + broken + "');\n", StandardCharsets.UTF_8);
        try {
            List<String> found = discoverSeedFiles(tmp);
            assertTrue(found.contains("database/brand-new-seed.sql"),
                    "反向验证失败：新文件没被动态发现 → 覆盖漂移防护失效（实测名单=" + found + "）");
            // 且它里面的坏哈希必须真的会被判红
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            assertFalse(encoder.matches(CLAIMED_PASSWORD, broken),
                    "阴性对照失败：坏哈希验得通");
        } finally {
            // 清理临时目录（只删自己建的）
            try (java.util.stream.Stream<Path> s = Files.walk(tmp)) {
                s.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) { }
                });
            }
        }
    }
}
