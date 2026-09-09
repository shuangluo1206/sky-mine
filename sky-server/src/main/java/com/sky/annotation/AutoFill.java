package com.sky.annotation;

import com.sky.enumeration.OperationType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自动填充
 */
@Target(ElementType.METHOD)//指定注解加在什么位置上面，目前是方法上面，后面会拦截方法的
@Retention(RetentionPolicy.RUNTIME)
public @interface AutoFill {
    //指定数据库操作类型 update和insert，因为这里的更新和插入操作涉及到了自动填充updatetime、createtime等数据
    OperationType value();
}
