package com.crossborder.erp.product.entity;

import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>实体 ↔ 表结构错位的回归测试</b>。
 *
 * <p><b>缺陷本体</b>：{@code ProductSku} / {@code ProductCategory} 实体的字段名
 * （{@code sku} / {@code name} / {@code attributes} / {@code barCode} …）
 * 跟线上表里的列名（{@code sku_code} / {@code sku_name} / {@code specs} …）
 * <b>完全对不上</b>。MyBatis-Plus 默认驼峰转下划线映射 →
 * 实体字段 {@code name} 会映射到列 {@code name}，而表里没这一列 →
 * 一旦有人调用这个 Mapper，SQL 直接报
 * {@code Unknown column 'name' in 'field list'}。
 * 另外 {@code sku_code} 是 NOT NULL 且无默认值 → INSERT 也会被 MySQL 拒绝。</p>
 *
 * <p>📌 <b>为什么必须有这个测试</b>：这两个实体当时是<b>死代码</b>
 * （全项目没有任何地方引用这两个 Mapper），
 * 所以既没有单测覆盖，也没有任何接口能暴露问题。
 * 一旦哪天有人写了个 Service 用上它，编译期无感知、运行期直接炸。</p>
 *
 * <p>📌 本测试锁死「实体映射出的列名集合」，
 * 防止以后再有人把字段名改回 {@code sku} / {@code name}。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@DisplayName("实体 ↔ 表结构错位回归（Unknown column / NOT NULL 无默认值）")
class ProductEntityColumnAlignmentTest {

    private static Set<String> mappedColumns(Class<?> entity) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entity);
        TableInfo info = TableInfoHelper.getTableInfo(entity);
        assertThat(info).as("实体 %s 没被 MyBatis-Plus 识别", entity.getSimpleName())
                .isNotNull();
        Set<String> cols = new java.util.HashSet<>();
        // 📌 getFieldList() **不含主键**（@TableId 单独在 getKeyColumn()）
        if (info.getKeyColumn() != null) {
            cols.add(info.getKeyColumn());
        }
        info.getFieldList().stream().map(f -> f.getColumn()).forEach(cols::add);
        return cols;
    }

    @Test
    @DisplayName("✅ ProductSku 映射的列 == t_product_sku 的真实列")
    void productSkuColumnsMatchTable() {
        Set<String> cols = mappedColumns(ProductSku.class);

        // t_product_sku 的真实列（database/init.sql + 线上 information_schema）
        assertThat(cols).contains("id", "product_id", "sku_code", "sku_name",
                "specs", "cost_price", "sale_price", "status",
                "create_time", "update_time", "deleted");

        // 📌 旧实体里那些**表里根本没有**的列
        assertThat(cols).doesNotContain("name", "sku", "attributes",
                "bar_code", "weight", "length", "width", "height",
                "stock_quantity", "safety_stock");
    }

    @Test
    @DisplayName("✅ ProductCategory 映射的列 == t_product_category 的真实列")
    void productCategoryColumnsMatchTable() {
        Set<String> cols = mappedColumns(ProductCategory.class);

        assertThat(cols).contains("id", "parent_id", "category_name",
                "category_name_en", "category_code", "sort_order", "status",
                "create_time", "update_time", "deleted");

        // 📌 旧实体里那些**表里根本没有**的列
        assertThat(cols).doesNotContain("name", "level", "sort", "icon");
    }

    @Test
    @DisplayName("✅ Product 的关键列也没错位（回归保护）")
    void productColumnsMatchTable() {
        Set<String> cols = mappedColumns(Product.class);

        assertThat(cols).contains("id", "internal_sku", "product_name",
                "category_id", "brand", "main_image", "status",
                "create_time", "update_time", "deleted");

        // name/sku 都不存在（实体用 @TableField 映射到 product_name/internal_sku）
        assertThat(cols).doesNotContain("name", "sku");
    }

    @Test
    @DisplayName("✅ ProductImage / ProductPlatformSku 的列也没错位")
    void otherProductEntitiesMatchTable() {
        assertThat(mappedColumns(ProductImage.class))
                .contains("id", "product_id", "image_url", "thumb_url",
                        "image_type", "sort", "create_time");

        assertThat(mappedColumns(ProductPlatformSku.class))
                .contains("id", "product_id", "sku_id", "platform", "shop_id",
                        "platform_sku", "platform_product_id", "product_url",
                        "sale_price", "original_price", "stock_quantity",
                        "status", "sync_time", "create_time", "update_time");
    }
}
