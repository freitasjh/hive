package br.com.systec.hive.commons.api.tools.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class HasPermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(HasPermissionAspect.class);

    @Around("execution(* *(..)) && ( @annotation(br.com.systec.hive.commons.api.tools.security.HasPermission) || @within(br.com.systec.hive.commons.api.tools.security.HasPermission) )")
    public Object checkPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        HasPermission hasPermission = resolveHasPermission(joinPoint);
        if (hasPermission == null) {
            return joinPoint.proceed();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String required = hasPermission.value();
        String[] anyOf = hasPermission.anyOf();
        boolean hasValue = required != null && !required.isBlank();
        boolean hasAnyOf = anyOf != null && anyOf.length > 0;

        String logPermission = hasValue ? required : (hasAnyOf ? String.join(",", anyOf) : "UNKNOWN");

        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("HasPermission denied: no authentication for permission {}", logPermission);
            throw new AccessDeniedException("Access denied: not authenticated for permission " + logPermission);
        }

        var authorities = authentication.getAuthorities();

        if (authorities == null || authorities.isEmpty()) {
            log.warn("HasPermission denied: authorities empty for permission {}, user {}", logPermission, authentication.getName());
            throw new AccessDeniedException("Access denied: missing permission " + logPermission);
        }

        java.util.Set<String> authoritySet = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());

        boolean has;
        if (hasValue) {
            has = authoritySet.contains(required);
        } else if (hasAnyOf) {
            has = java.util.Arrays.stream(anyOf).anyMatch(authoritySet::contains);
        } else {
            log.warn("HasPermission denied: no permission configured on annotation for user {}", authentication.getName());
            throw new AccessDeniedException("Access denied: missing permission configuration");
        }

        if (has) {
            return joinPoint.proceed();
        }

        log.warn("Access denied: missing permission {} for user {}", logPermission, authentication.getName());
        throw new AccessDeniedException("Access denied: missing permission " + logPermission);
    }

    private HasPermission resolveHasPermission(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        HasPermission hasPermission = AnnotationUtils.findAnnotation(method, HasPermission.class);
        if (hasPermission != null) {
            return hasPermission;
        }

        // check target class implementation method
        try {
            Method targetMethod = joinPoint.getTarget().getClass()
                    .getMethod(method.getName(), method.getParameterTypes());
            hasPermission = AnnotationUtils.findAnnotation(targetMethod, HasPermission.class);
            if (hasPermission != null) {
                return hasPermission;
            }
        } catch (NoSuchMethodException ignored) {
        }

        // class-level annotation
        hasPermission = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), HasPermission.class);
        if (hasPermission != null) {
            return hasPermission;
        }
        return AnnotationUtils.findAnnotation(signature.getDeclaringType(), HasPermission.class);
    }
}
