package com.crossborder.erp.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 🔴 <b>原始 SQL 栈泄露给前端的回归测试</b>。
 *
 * <p><b>缺陷本体</b>：{@link GlobalExceptionHandler} 的兜底分支
 * 判定「是不是业务异常」时，只看「是不是 RuntimeException 且 message 非空」，
 * 于是把 MyBatis 包装的 {@code DataIntegrityViolationException}
 * 当成了「业务提示」原样透出。</p>
 *
 * <p>📌 它的 message 里含<b>完整的 SQL 语句、列名、表名</b>，例如：</p>
 * <pre>
 * ### Error updating database.  Cause: java.sql.SQLException:
 *   Field 'total_amount' doesn't have a default value
 * ### The error may exist in com.crossborder.erp.order.mapper.OrderItemMapper.java
 * ### SQL: INSERT INTO t_order_item ...
 * </pre>
 *
 * <p>→ 直接透给前端 = <b>数据库结构泄露</b>，
 * 顺便还把内部表名、字段名、mapper 类名告诉了攻击者。</p>
 *
 * <p>📌 实测：漏传 {@code totalAmount} 的下单请求，
 * 前端收到的响应体就是整段 SQL 栈（{@code code=500}）。</p>
 *
 * @author 火球鼠
 * @since 2026-11-03
 */
@DisplayName("原始 SQL 栈不得泄露给前端")
class SqlLeakProtectionTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new LeakyController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    /** 模拟 MyBatis 的真实异常（message 里带完整 SQL） */
    private static DataIntegrityViolationException sqlLeakException() {
        return new DataIntegrityViolationException(
                "\n### Error updating database.  Cause: java.sql.SQLException: "
                        + "Field 'total_amount' doesn't have a default value\n"
                        + "### The error may exist in "
                        + "com.crossborder.erp.order.mapper.OrderItemMapper.java (best guess)\n"
                        + "### SQL: INSERT INTO t_order_item (order_id, unit_price) VALUES (?, ?)\n"
                        + "### Cause: java.sql.SQLException: Field 'total_amount' ...",
                new SQLException("Field 'total_amount' doesn't have a default value"));
    }

    @Test
    @DisplayName("🔴 DataIntegrityViolationException → 响应体不得含 SQL / 表名 / mapper 类名")
    void dataIntegrityViolationDoesNotLeakSql() throws Exception {
        String body = mockMvc.perform(get("/leak/integrity"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .as("响应体绝不能包含 SQL 语句")
                .doesNotContain("INSERT INTO")
                .doesNotContain("t_order_item")
                .doesNotContain("OrderItemMapper")
                .doesNotContain("SQL:")
                .doesNotContain("doesn't have a default value");
    }

    @Test
    @DisplayName("🔴 其它 DataAccessException 也不泄露")
    void otherDataAccessExceptionsDoNotLeak() throws Exception {
        String body = mockMvc.perform(get("/leak/connection"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body)
                .doesNotContain("jdbc:")
                .doesNotContain("password")
                .doesNotContain("Connection refused");
    }

    @Test
    @DisplayName("✅ 真正的业务提示仍要原样透出（别把提示也一起吞了）")
    void businessMessageStillPassedThrough() throws Exception {
        String body = mockMvc.perform(get("/leak/business"))
                .andExpect(status().isInternalServerError())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(body).contains("账户不存在");
    }

    @RestController
    static class LeakyController {

        @GetMapping("/leak/integrity")
        public void integrity() {
            throw sqlLeakException();
        }

        @GetMapping("/leak/connection")
        public void connection() {
            throw new CannotGetJdbcConnectionException(
                    "jdbc:mysql://internal-db:3306/erp_order - Connection refused (password=secret)");
        }

        @GetMapping("/leak/business")
        public void business() {
            throw new RuntimeException("账户不存在");
        }
    }
}
