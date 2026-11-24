package br.com.systec.hive.integrationtest;

import br.com.systec.hive.commons.impl.security.TenantContext;
import br.com.systec.hive.integrationtest.support.WebIntegrationTest;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientCreateDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPageDTO;
import br.com.systec.hive.publicapi.api.dto.PublicApiClientPromptBindingDTO;
import br.com.systec.hive.publicapi.api.service.PublicApiClientAdminService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bugfix N+1 da listagem admin: Hibernate Statistics nativo mede queries em banco real.
 * R1 (antes do fix): 42 queries com 20 clients e 202 com 100 — padrão 2N + 2.
 * Depois do fix: 4 queries, independente do tamanho da página.
 */
class PublicApiClientAdminQueryIT extends WebIntegrationTest {

    @Autowired
    private PublicApiClientAdminService adminService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @DynamicPropertySource
    static void enableStatistics(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Test
    void when_listTwentyClients_then_queryCountIsConstantAndContractPreserved() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, 20);
        PageResult result = measure(tenant, 20);
        System.out.println("N1_AFTER_FIX size=20 queries=" + result.queries());
        assertThat(result.queries()).isEqualTo(4L);
        assertContract(result.page(), 20);
    }

    @Test
    void when_listOneHundredClients_then_queryCountIsConstantAndContractPreserved() throws Exception {
        UUID tenant = UUID.randomUUID();
        seed(tenant, 100);
        PageResult result = measure(tenant, 100);
        System.out.println("N1_AFTER_FIX size=100 queries=" + result.queries());
        assertThat(result.queries()).isEqualTo(4L);
        assertContract(result.page(), 100);
    }

    private void seed(UUID tenant, int count) throws Exception {
        TenantContext.runWithTenant(tenant, () -> {
            for (int i = 0; i < count; i++) {
                PublicApiClientCreateDTO dto = new PublicApiClientCreateDTO();
                dto.setLabel("client-" + i);
                dto.setGrants(List.of("PUBLIC_CHAT_COMPLETE"));
                dto.setPrompts(List.of(new PublicApiClientPromptBindingDTO("p1", "FOLLOW_ACTIVE", null)));
                adminService.create(dto);
            }
            return null;
        });
    }

    private PageResult measure(UUID tenant, int size) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        PublicApiClientPageDTO[] holder = new PublicApiClientPageDTO[1];
        TenantContext.runWithTenant(tenant, () -> {
            holder[0] = adminService.list(0, size);
            return null;
        });
        return new PageResult(stats.getPrepareStatementCount(), holder[0]);
    }

    private static void assertContract(PublicApiClientPageDTO page, int expectedSize) {
        assertThat(page.getContent()).hasSize(expectedSize);
        assertThat(page.getContent()).allSatisfy(item -> {
            assertThat(item.getGrants()).containsExactly("PUBLIC_CHAT_COMPLETE");
            assertThat(item.getPrompts()).hasSize(1);
            assertThat(item.getPrompts().get(0).getPromptCode()).isEqualTo("p1");
        });
    }

    private record PageResult(long queries, PublicApiClientPageDTO page) {
    }
}
