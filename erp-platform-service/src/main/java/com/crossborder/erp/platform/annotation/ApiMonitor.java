package com.crossborder.erp.platform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * API监控注解 - 用于标记需要监控的API方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiMonitor {
    /**
     * API描述
     */
    String description() default "";

    /**
     * 所属平台
     */
    String platform() default "";
}
