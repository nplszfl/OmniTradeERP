package com.crossborder.erp.warehouse;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.crossborder.erp.warehouse.entity.WarehouseLocation;
import com.crossborder.erp.warehouse.vo.LocationVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 库位实体 ↔ SQL ↔ JSON 契约测试
 *
 * <p>🔴 <b>修复前的真缺陷（线上 100% 复现）</b>：</p>
 * <p>实体属性叫 {@code row} / {@code column} / {@code level}，用
 * {@code @TableField("row_no")} 之类映射到列。MyBatis-Plus 在<b>属性名与列名不一致</b>时
 * 会自动补一个别名，于是生成的 SELECT 变成：</p>
 *
 * <pre>
 *   SELECT row_no AS row, column_no AS column, level_no AS level, ...
 * </pre>
 *
 * <p>而 {@code ROW} / {@code COLUMN} / {@code LEVEL} 在 <b>MySQL 8 里全是保留字</b>，
 * 不加反引号就是语法错误（MySQL 8.0 实测）：</p>
 *
 * <pre>
 *   SQLSyntaxErrorException: ... near 'row,column_no AS column,level_no AS level'
 * </pre>
 *
 * <p>后果：{@code GET /warehouse/{id}/locations} 和 {@code /warehouse/locations/page}
 * <b>全部 500</b>，库位页面直接废掉。</p>
 *
 * <p>📌 关键认知：<b>只避开列名是不够的，别名也要避开</b>。
 * MEMORY 里记的「row/column/level → row_no/column_no/level_no」只解决了列名侧，
 * 别名侧没人管 —— 而 MP 是按<b>属性名</b>生成别名的。</p>
 *
 * <p>📌 为什么单测测不出来：其它 warehouse 单测都 mock 了 Mapper，
 * 根本不生成 SQL；而 H2 的保留字表和 MySQL 不同，H2 也测不出来。
 * 所以这里直接断言 <b>TableInfo 生成的 SELECT 片段</b>。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
class WarehouseLocationSqlContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** MySQL 8 保留字（实测会炸的那几个） */
    private static final String[] RESERVED_ALIASES = {"row", "column", "level"};

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), WarehouseLocation.class);
    }

    @Test
    @DisplayName("生成的 SELECT 不能给保留字起别名（回归：AS row / AS column / AS level）")
    void selectMustNotAliasReservedWords() {
        TableInfo info = TableInfoHelper.getTableInfo(WarehouseLocation.class);
        String allSelect = info.getAllSqlSelect();

        assertTrue(allSelect.contains("row_no"), "row_no 列必须出现: " + allSelect);
        assertTrue(allSelect.contains("column_no"), "column_no 列必须出现: " + allSelect);
        assertTrue(allSelect.contains("level_no"), "level_no 列必须出现: " + allSelect);

        for (String reserved : RESERVED_ALIASES) {
            String alias = "AS " + reserved;
            assertFalse(allSelect.toUpperCase().contains(alias.toUpperCase()),
                    "SELECT 里出现了保留字别名「" + alias + "」，MySQL 8 会直接语法错误: " + allSelect);
        }
    }

    @Test
    @DisplayName("属性名本身不能是 MySQL 保留字")
    void propertyNamesMustNotBeReservedWords() {
        TableInfo info = TableInfoHelper.getTableInfo(WarehouseLocation.class);
        for (String reserved : RESERVED_ALIASES) {
            boolean hit = info.getFieldList().stream()
                    .anyMatch(f -> reserved.equals(f.getProperty()));
            assertFalse(hit,
                    "属性名「" + reserved + "」是 MySQL 保留字，MP 会用它当别名 → 语法错误");
        }
    }

    @Test
    @DisplayName("前端提交 row/column/level 仍能被正确接收（回归：改名后字段静默丢失）")
    void jsonAliasKeepsFrontendContract() throws Exception {
        String json = "{\"warehouseId\":1,\"locationCode\":\"A-01-01-01\","
                + "\"row\":\"01\",\"column\":\"02\",\"level\":\"03\"}";

        WarehouseLocation location = MAPPER.readValue(json, WarehouseLocation.class);

        assertEquals("01", location.getRowNo(), "row 没接住");
        assertEquals("02", location.getColumnNo(), "column 没接住");
        assertEquals("03", location.getLevelNo(), "level 没接住");
    }

    @Test
    @DisplayName("VO 对外契约仍是 row/column/level（前端 erp-web/src/api/warehouse.ts）")
    void voContractUnchanged() {
        LocationVO vo = new LocationVO();
        vo.setRow("01");
        vo.setColumn("02");
        vo.setLevel("03");
        assertEquals("01", vo.getRow());
        assertEquals("02", vo.getColumn());
        assertEquals("03", vo.getLevel());
    }

    // ============================================================
    // 元测试：证明上面的探测器本身有效（不然「测试全绿」也只是假绿）
    // ============================================================

    /**
     * 一次性实体：属性名叫 {@code row}（MySQL 保留字），映射到 {@code row_no} 列。
     * 这正是修复前的写法。
     */
    @com.baomidou.mybatisplus.annotation.TableName("tmp_reserved_probe")
    static class ReservedProbeEntity {
        @com.baomidou.mybatisplus.annotation.TableId
        private Long id;
        @com.baomidou.mybatisplus.annotation.TableField("row_no")
        private String row;
        @com.baomidou.mybatisplus.annotation.TableField("column_no")
        private String column;
    }

    @Test
    @DisplayName("元测试：探测器确实能抓到保留字别名（否则上面的断言是假绿）")
    void detectorActuallyDetectsReservedAlias() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReservedProbeEntity.class);
        TableInfo info = TableInfoHelper.getTableInfo(ReservedProbeEntity.class);
        String allSelect = info.getAllSqlSelect();

        // 确认 MP 确实会生成 AS row / AS column —— 也就是修复前 SQL 会炸的原因
        assertTrue(allSelect.toUpperCase().contains("AS ROW"),
                "MP 应该生成了 AS row，实际: " + allSelect);
        assertTrue(allSelect.toUpperCase().contains("AS COLUMN"),
                "MP 应该生成了 AS column，实际: " + allSelect);

        // 同样的判定规则，应该判定为「有问题」
        boolean detected = false;
        for (String reserved : RESERVED_ALIASES) {
            if (allSelect.toUpperCase().contains(("AS " + reserved).toUpperCase())) {
                detected = true;
            }
        }
        assertTrue(detected, "探测器没能抓到保留字别名 —— 断言逻辑本身失效了");
    }
}
