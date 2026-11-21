package com.painuo.suite.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按钮级权限校验。
 *
 * <pre>
 * &#64;RequirePerm("cms:content:create")
 * &#64;RequirePerm(value = {"cms:content:audit", "cms:content:publish"}, logic = Logic.ANY)
 * </pre>
 *
 * <p>权限编码规范：{@code 模块:资源:动作}，与 {@code sys_menu.perm} 一一对应，
 * 后台「角色-菜单」勾选即生效，无需改代码。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RequirePerms.class)
public @interface RequirePerm {

    String[] value();

    /** 多个权限之间是「且」还是「或」，默认「且」。 */
    Logic logic() default Logic.ALL;

    enum Logic {
        ALL, ANY
    }
}
