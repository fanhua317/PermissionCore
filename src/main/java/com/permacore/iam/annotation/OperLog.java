package com.permacore.iam.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * 用于标注需要记录操作日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {
    
    /**
     * 操作标题
     */
    String title() default "";
    
    /**
     * 业务类型（0-其他 1-新增 2-修改 3-删除 4-查询）
     */
    int businessType() default 0;
    
    /**
     * 是否保存请求参数
     */
    boolean isSaveRequestData() default true;
    
    /**
     * 是否保存响应数据
     */
    boolean isSaveResponseData() default true;
}
