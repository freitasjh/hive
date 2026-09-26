package br.com.systec.hive.commons.api.tools.security;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HasPermissionAspectTest {

    private final HasPermissionAspect aspect = new HasPermissionAspect();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static class SecuredService {
        @HasPermission("PROMPT_MANAGE")
        public String securedMethod() {
            return "ok";
        }

        @HasPermission(anyOf = {"CHAT_SEND", "AGENT_MANAGE"})
        public String anyOfMethod() {
            return "ok";
        }
    }

    private ProceedingJoinPoint mockJoinPointFor(String methodName, Object proceedResult) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        java.lang.reflect.Method method = SecuredService.class.getMethod(methodName);
        SecuredService target = new SecuredService();
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn((Class) SecuredService.class);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.proceed()).thenReturn(proceedResult);
        return joinPoint;
    }

    @Test
    void when_check_withEmptyAuthorities_then_throwsAccessDeniedException() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pwd", List.of())
        );
        ProceedingJoinPoint jp = mockJoinPointFor("securedMethod", "ok");

        assertThatThrownBy(() -> aspect.checkPermission(jp))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void when_check_withRequiredPermission_then_proceeds() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pwd",
                List.of(new SimpleGrantedAuthority("PROMPT_MANAGE")))
        );
        ProceedingJoinPoint jp = mockJoinPointFor("securedMethod", "ok");

        Object result = aspect.checkPermission(jp);

        assertThat(result).isEqualTo("ok");
    }

    @Test
    void when_check_withoutRequiredPermission_then_throwsAccessDeniedException() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("user", "pwd",
                List.of(new SimpleGrantedAuthority("OTHER")))
        );
        ProceedingJoinPoint jp = mockJoinPointFor("securedMethod", "ok");

        assertThatThrownBy(() -> aspect.checkPermission(jp))
            .isInstanceOf(AccessDeniedException.class);
    }
}
