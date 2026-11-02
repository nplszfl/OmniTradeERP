package com.crossborder.erp.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 插件配置
 *
 * <p>历史上本项目所有服务都<b>没有</b>注册分页拦截器，导致：
 * <ul>
 *   <li>{@code selectPage(page, wrapper)} 不生成 LIMIT / OFFSET，实际返回全量数据</li>
 *   <li>{@code page.getTotal()} 恒为 0，前端分页组件永远显示"共 0 条"</li>
 * </ul>
 * 由于各服务的单测大多 mock 了 Mapper，这个问题在测试中无法暴露。</p>
 *
 * <p>该配置通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 自动装配到所有依赖 erp-common 的服务，无需在各服务的启动类中显式扫描。</p>
 *
 * @author 火球鼠
 * @since 2026-10-28
 */
@AutoConfiguration
@ConditionalOnClass(MybatisPlusInterceptor.class)
public class MybatisPlusConfig {

    /**
     * 分页插件
     *
     * <p>不指定 DbType，由 MyBatis-Plus 依据 DataSource 的 JDBC URL 自动识别
     * （MySQL / H2 均可）。</p>
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        // 单页最大条数保护，避免前端传入超大 size 拖垮数据库
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 审计字段自动填充
     *
     * <p>⚠️ 全项目<b>从来没有</b>注册过 MetaObjectHandler，
     * 而几十个实体都标了 {@code @TableField(fill = FieldFill.INSERT)} ——
     * 这些 fill 注解全是摆设，createTime / updateTime 靠数据库默认值兜底，
     * 结果就是 insert 之后内存里的实体 createTime 还是 null
     * （库里的值是对的，但刚 insert 完就拿 createTime 去展示/判断的代码会拿到 null）。</p>
     *
     * <p>用 strictInsertFill / strictUpdateFill：只有标了对应 fill 注解的字段才会被填，
     * 不会误伤其他实体。</p>
     */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
