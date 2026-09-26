package br.com.systec.hive.commons.impl.security;

import br.com.systec.hive.commons.api.exceptions.TenantNotFountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
public class TenantAspect {
    private static final Logger log = LoggerFactory.getLogger(TenantAspect.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Before("execution(* org.springframework.data.jpa.repository.JpaRepository+.*(..)) || " +
            "execution(* org.springframework.data.jpa.repository.JpaSpecificationExecutor+.*(..))")
    public void enableTenantFilter() {
        try {
            UUID tenantId = TenantContext.getCurrentTenant();
            if (tenantId != null) {
                log.warn("@@@ Setando o tenant no filtro {}", tenantId);
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantId);
            }
        } catch (TenantNotFountException e) {
            log.warn("@@@ Tenant not in context, skipping tenant filter (registration/public flow) @@@");
        }
    }
}
