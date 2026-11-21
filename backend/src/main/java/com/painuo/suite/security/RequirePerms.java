package com.painuo.suite.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** {@link RequirePerm} 的容器注解，由 Java 编译器自动填充，业务代码无需直接使用。 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerms {

    RequirePerm[] value();
}
