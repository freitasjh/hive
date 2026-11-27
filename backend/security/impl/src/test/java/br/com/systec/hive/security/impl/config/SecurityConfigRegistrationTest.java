package br.com.systec.hive.security.impl.config;

import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.service.AuthenticationService;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import br.com.systec.hive.security.api.service.TokenCacheService;
import br.com.systec.hive.security.impl.filter.SecurityFilter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/** O filtro deve entrar na cadeia apenas via SecurityConfig, nunca por component scan. */
class SecurityConfigRegistrationTest {

    @Test
    void when_securityFilterIsConfiguredBean_then_itIsNotAComponentScannedFilter() throws Exception {
        assertThat(SecurityFilter.class.getAnnotation(Component.class)).isNull();

        Method factory = SecurityConfig.class.getDeclaredMethod("securityFilter",
                SecurityTokenService.class, AuthenticationService.class,
                TokenCacheService.class, UserService.class);
        assertThat(factory.getAnnotation(Bean.class)).isNotNull();
    }
}
