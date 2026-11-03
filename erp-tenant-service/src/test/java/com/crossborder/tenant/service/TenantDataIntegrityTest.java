package com.crossborder.tenant.service;

import com.crossborder.tenant.converter.YearMonthConverter;
import com.crossborder.tenant.entity.Tenant;
import com.crossborder.tenant.entity.TenantPlan;
import com.crossborder.tenant.entity.TenantStatus;
import com.crossborder.tenant.entity.billing.Bill;
import com.crossborder.tenant.entity.billing.Bill.BillStatus;
import com.crossborder.tenant.entity.billing.Bill.BillType;
import com.crossborder.tenant.entity.usage.UsageRecord;
import com.crossborder.tenant.repository.TenantRepository;
import com.crossborder.tenant.repository.billing.BillRepository;
import com.crossborder.tenant.repository.usage.UsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户模块「H2 里能过、真 MySQL 里炸」的缺陷回归测试
 *
 * <p>🔴 本类每个用例都对应一个**真实缺陷**，且这些缺陷单测全绿时依然存在：</p>
 * <ol>
 *   <li><b>YearMonth 反序列化 500</b> —— Hibernate 6.2.x 不支持 java.time.YearMonth
 *       （整个 hibernate-core 里连 YearMonthType 都没有），会当成 Serializable 处理，
 *       列类型被 ddl-auto 改成 varbinary(255)，
 *       有数据时读取抛 {@code SerializationException: could not deserialize}
 *       → GET /api/tenant/billing/bills 直接 500。
 *       <b>表里没数据时不报错</b>，所以之前一直没暴露。</li>
 *   <li><b>账单/用量列被改成 varbinary</b> —— 同上，必须断言列类型是 varchar。</li>
 *   <li><b>掩码回写</b> —— 出参脱敏后，前端把 `rt-am****0001` 回传，
 *       会把库里的真实 token 覆盖成掩码 → 凭证静默失效。</li>
 * </ol>
 *
 * <p>📌 H2 用 MODE=MySQL + ddl-auto=create-drop，列类型断言在这里就能挡住
 * 「@Convert 没加」这类回归 —— 加了转换器后 Hibernate 才会生成 varchar。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@SpringBootTest
@ActiveProfiles("test")
class TenantDataIntegrityTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private UsageRecordRepository usageRecordRepository;

    /**
     * 📌 直接 new 而不是 @Autowired：@Converter 类默认**不是 Spring bean**
     *    （只有 autoApply=true 且被 Hibernate 扫描时才会进容器）。
     *    这里要单独测转换逻辑本身，所以手动实例化。
     */
    private final YearMonthConverter yearMonthConverter = new YearMonthConverter();

    @Autowired
    private DataSource dataSource;

    private String tenantId;

    @BeforeEach
    void setUp() {
        billRepository.deleteAll();
        usageRecordRepository.deleteAll();
        tenantRepository.deleteAll();

        Tenant tenant = Tenant.builder()
                .name("反序列化测试租户")
                .domain("deser-test.example.com")
                .plan(TenantPlan.BUSINESS)
                .status(TenantStatus.ACTIVE)
                .region("cn-shenzhen")
                .expiresAt(LocalDateTime.now().plusDays(30))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        tenantId = tenantRepository.save(tenant).getId();
    }

    // ============================================================
    // 缺陷 1：YearMonth 转换器
    // ============================================================

    @Test
    @DisplayName("YearMonth 转换器：往返一致")
    void yearMonthRoundTrip() {
        YearMonth ym = YearMonth.of(2026, 11);
        String db = yearMonthConverter.convertToDatabaseColumn(ym);
        assertEquals("2026-11", db);
        assertEquals(ym, yearMonthConverter.convertToEntityAttribute(db));
    }

    @Test
    @DisplayName("YearMonth 转换器：null 安全")
    void yearMonthNullSafe() {
        assertNull(yearMonthConverter.convertToDatabaseColumn(null));
        assertNull(yearMonthConverter.convertToEntityAttribute(null));
        assertNull(yearMonthConverter.convertToEntityAttribute("   "));
    }

    @Test
    @DisplayName("YearMonth 转换器：容忍带日的脏数据")
    void yearMonthToleratesDirtyData() {
        // 历史上有人存成 '2026-11-01'，转换时不能直接抛异常
        assertEquals(YearMonth.of(2026, 11),
                yearMonthConverter.convertToEntityAttribute("2026-11-01"));
    }

    // ============================================================
    // 缺陷 2：账单 —— 落库 + 读回（YearMonth 必须能反序列化）
    // ============================================================

    @Test
    @DisplayName("账单落库后能读回 YearMonth（回归：曾经读取直接 500）")
    void billRoundTripsYearMonth() {
        YearMonth period = YearMonth.of(2026, 11);

        billRepository.save(Bill.builder()
                .tenantId(tenantId)
                .billingPeriod(period)
                .billType(BillType.SUBSCRIPTION)
                .status(BillStatus.PENDING)
                .subscriptionFee(new java.math.BigDecimal("12999.00"))
                .usageFee(new java.math.BigDecimal("358.40"))
                .discount(java.math.BigDecimal.ZERO)
                .totalAmount(new java.math.BigDecimal("13357.40"))
                .paidAmount(java.math.BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        billRepository.flush();

        // 📌 关键：这里以前抛 SerializationException
        //    "could not deserialize" —— 因为 Hibernate 把 YearMonth 当 Serializable，
        //    列类型是 varbinary，读的时候用 Java 反序列化一个 VARCHAR 字符串。
        Bill loaded = billRepository.findAll().stream()
                .filter(b -> tenantId.equals(b.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("账单没读回来"));

        assertNotNull(loaded.getBillingPeriod());
        assertEquals(period, loaded.getBillingPeriod());
        assertEquals(BillType.SUBSCRIPTION, loaded.getBillType());
        assertEquals(BillStatus.PENDING, loaded.getStatus());
    }

    @Test
    @DisplayName("用量记录落库后能读回 YearMonth")
    void usageRecordRoundTripsYearMonth() {
        YearMonth period = YearMonth.of(2026, 11);

        usageRecordRepository.save(UsageRecord.builder()
                .tenantId(tenantId)
                .billingPeriod(period)
                .apiCalls(96400L)
                .storageBytes(3221225472L)
                .orderCount(642)
                .platformCount(4)
                .aiCalls(12900L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        usageRecordRepository.flush();

        UsageRecord loaded = usageRecordRepository.findAll().stream()
                .filter(u -> tenantId.equals(u.getTenantId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("用量记录没读回来"));

        assertNotNull(loaded.getBillingPeriod());
        assertEquals(period, loaded.getBillingPeriod());
        assertEquals(96400L, loaded.getApiCalls());
    }

    @Test
    @DisplayName("按 YearMonth 精确查询能命中（回归：查询条件同样要能绑定）")
    void findByTenantAndPeriod() {
        YearMonth period = YearMonth.of(2026, 11);
        usageRecordRepository.save(UsageRecord.builder()
                .tenantId(tenantId)
                .billingPeriod(period)
                .apiCalls(100L)
                .storageBytes(0L)
                .orderCount(1)
                .platformCount(1)
                .aiCalls(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
        usageRecordRepository.flush();

        var found = usageRecordRepository.findByTenantIdAndBillingPeriod(tenantId, period);
        assertTrue(found.isPresent(), "按周期查询没命中");
        assertEquals(100L, found.get().getApiCalls());
    }

    @Test
    @DisplayName("最近 N 个周期按周期倒序（趋势接口用）")
    void findTop12OrdersByPeriodDesc() {
        for (int m = 1; m <= 3; m++) {
            usageRecordRepository.save(UsageRecord.builder()
                    .tenantId(tenantId)
                    .billingPeriod(YearMonth.of(2026, m))
                    .apiCalls((long) (m * 100))
                    .storageBytes(0L)
                    .orderCount(m)
                    .platformCount(1)
                    .aiCalls(0L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build());
        }
        usageRecordRepository.flush();

        var list = usageRecordRepository.findTop12ByTenantIdOrderByBillingPeriodDesc(tenantId);
        assertEquals(3, list.size());
        assertEquals(YearMonth.of(2026, 3), list.get(0).getBillingPeriod());
        assertEquals(YearMonth.of(2026, 1), list.get(2).getBillingPeriod());
    }

    // ============================================================
    // 缺陷 3：列类型必须是 varchar（不能是 varbinary）
    // ============================================================

    @Test
    @DisplayName("billing_period 列类型是字符型而不是 varbinary")
    void billingPeriodColumnIsVarcharNotVarbinary() {
        // 📌 没有 @Convert 时，Hibernate 会把 YearMonth 当 Serializable →
        //    列类型生成 varbinary(255)。有 @Convert 才生成 varchar(7)。
        //    这是「真 MySQL 上被 ddl-auto 悄悄改坏」的根因，必须锁死。
        //
        // 📌 H2 报的是 JDBC 标准名 `character varying`，MySQL 报 `varchar`，
        //    两边都要认；关键是**不能**是 binary/varbinary 那类二进制列。
        String billColType = columnType("TENANT_BILLS", "BILLING_PERIOD");
        assertCharColumn(billColType, "tenant_bills.billing_period");

        String usageColType = columnType("TENANT_USAGE_RECORDS", "BILLING_PERIOD");
        assertCharColumn(usageColType, "tenant_usage_records.billing_period");
    }

    private void assertCharColumn(String colType, String label) {
        assertNotNull(colType, label + " 不存在");
        String t = colType.toLowerCase();
        assertTrue(!t.contains("binary"),
                label + " 类型应为字符型，实际是 " + colType + "（Hibernate 把 YearMonth 当 Serializable 了）");
        assertTrue(t.contains("char") || t.contains("varchar"),
                label + " 类型应为 varchar，实际是 " + colType);
    }

    private String columnType(String table, String column) {
        // 📌 H2 的 DATABASE_TO_LOWER=TRUE 会把表名/列名存成小写，
        //    而 INFORMATION_SCHEMA 里查出来也可能是小写 → 大小写不敏感匹配，
        //    避免因为大小写差异误判成「列不存在」。
        try (var conn = dataSource.getConnection();
             var st = conn.prepareStatement(
                     "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                             + "WHERE UPPER(TABLE_NAME) = ? AND UPPER(COLUMN_NAME) = ?")) {
            st.setString(1, table.toUpperCase());
            st.setString(2, column.toUpperCase());
            try (var rs = st.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            throw new AssertionError("读列类型失败: " + e.getMessage(), e);
        }
    }
}
