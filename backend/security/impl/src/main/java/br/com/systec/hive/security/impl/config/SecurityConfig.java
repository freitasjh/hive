package br.com.systec.hive.security.impl.config;

import br.com.systec.hive.publicapi.api.service.PublicApiKeyAuthenticator;
import br.com.systec.hive.publicapi.impl.service.PublicClientTokenIssuer;
import br.com.systec.hive.security.impl.filter.PublicApiSecurityFilter;
import br.com.systec.hive.security.impl.filter.SecurityFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity()
public class SecurityConfig {

    private final SecurityFilter securityFilter;
    private final PublicClientTokenIssuer publicClientTokenIssuer;
    private final RedisTemplate<String, Object> redisTemplate;

    public SecurityConfig(SecurityFilter securityFilter,
                          PublicClientTokenIssuer publicClientTokenIssuer,
                          RedisTemplate<String, Object> redisTemplate) {
        this.securityFilter = securityFilter;
        this.publicClientTokenIssuer = publicClientTokenIssuer;
        this.redisTemplate = redisTemplate;
    }

    @Bean
    @org.springframework.core.annotation.Order(1)
    public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http,
                                                                   ObjectProvider<PublicApiKeyAuthenticator> keyAuthenticator) throws Exception {
        PublicApiSecurityFilter publicApiSecurityFilter = new PublicApiSecurityFilter(publicClientTokenIssuer, redisTemplate, keyAuthenticator);
        return http
                .securityMatcher("/v1/public/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/v1/public/auth/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, excep) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":{\"message\":\"Unauthorized\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}");
                        })
                        .accessDeniedHandler((req, res, excep) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":{\"message\":\"Forbidden\",\"type\":\"invalid_request_error\",\"code\":\"insufficient_quota\"}}");
                        })
                )
                .addFilterBefore(publicApiSecurityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.POST, "/v1/auth/**", "/v1/user-accounts/create").permitAll()
                        .requestMatchers("/v1/public/**").denyAll()
                        // ADR-005 Decisão 7: scrape somente por loopback; fora ⇒ 403 pelo accessDeniedHandler.
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                        .access(new WebExpressionAuthorizationManager(
                                "hasIpAddress('127.0.0.1') or hasIpAddress('::1')"))
                        // Probes seguem públicos; os demais actuator caem em authenticated() e 404 pela exposure list.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(
                                "/**.html",
                                "/webjars/**",
                                "/configuration/**",
                                "/manager/**",
                                "/notification/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                ).exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, excep) -> {
                            // Decisão 7: IP fora da loopback é negação de scrape (403), não falta de token.
                            String uri = req.getRequestURI();
                            String contextPath = req.getContextPath();
                            String path = contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
                                    ? uri.substring(contextPath.length())
                                    : uri;
                            if ("GET".equals(req.getMethod())
                                    && "/actuator/prometheus".equals(path)) {
                                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                res.setContentType("application/json");
                                res.getWriter().write("{\"error\": \"Acesso negado\"}");
                                return;
                            }
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.getWriter().write("{\"error\": \"Token inválido ou expirado\"}");
                        })
                        .accessDeniedHandler((req, res, excep) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.getWriter().write("{\"error\": \"Acesso negado\"}");
                        })
                )
                .addFilterBefore(securityFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration(SecurityFilter securityFilter) {
        FilterRegistrationBean<SecurityFilter> registration = new FilterRegistrationBean<>(securityFilter);
        registration.setEnabled(false);
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        return registration;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}