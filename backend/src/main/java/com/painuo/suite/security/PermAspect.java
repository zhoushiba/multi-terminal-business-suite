package com.painuo.suite.security;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 按钮级权限切面：把 {@link RequirePerm} 翻译成 403。
 *
 * <p>方法注解优先于类注解；同一处多个 {@code @RequirePerm} 之间是「且」关系，
 * 单注解内多值按 {@link RequirePerm.Logic} 判断。</p>
 *
 * <p>超级管理员（role=admin）直接放行——这是运维现实：平台方需要能排查任意问题。</p>
 */
@Aspect
@Component
public class PermAspect {

    public static final String SUPER_ADMIN_ROLE = "admin";

    @Around("@annotation(com.painuo.suite.security.RequirePerm) "
            + "|| @within(com.painuo.suite.security.RequirePerm)")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        AuthPrincipal principal = AuthContext.require();
        if (!principal.hasAnyRole(SUPER_ADMIN_ROLE)) {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            RequirePerm[] annotations = collect(method, joinPoint.getTarget().getClass());
            for (RequirePerm perm : annotations) {
                if (!satisfied(principal, perm)) {
                    throw BizException.forbidden("缺少权限：" + String.join(",", perm.value()));
                }
            }
        }
        return joinPoint.proceed();
    }

    private RequirePerm[] collect(Method method, Class<?> targetClass) {
        RequirePerm[] onMethod = method.getAnnotationsByType(RequirePerm.class);
        if (onMethod.length > 0) {
            return onMethod;
        }
        RequirePerm onClass = AnnotationUtils.findAnnotation(targetClass, RequirePerm.class);
        return onClass == null ? new RequirePerm[0] : new RequirePerm[]{onClass};
    }

    private boolean satisfied(AuthPrincipal principal, RequirePerm perm) {
        boolean any = perm.logic() == RequirePerm.Logic.ANY;
        return any
                ? Arrays.stream(perm.value()).anyMatch(principal::hasPermission)
                : Arrays.stream(perm.value()).allMatch(principal::hasPermission);
    }
}
