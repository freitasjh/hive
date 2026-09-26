package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.impl.fake.PrefixAwareEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbEmbeddingServiceTenantCacheTest {

    @Mock
    private KbEmbeddingModelFactory modelFactory;

    @InjectMocks
    private KbEmbeddingService service;

    @Test
    @SuppressWarnings("unchecked")
    void when_resolveModel_with_differentTenants_then_isolatedEntries() throws Exception {
        when(modelFactory.create(any(), any())).thenAnswer(inv -> new PrefixAwareEmbeddingModel("nomic", 768));
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        Method resolveModel = KbEmbeddingService.class.getDeclaredMethod("resolveModel", UUID.class, String.class);
        resolveModel.setAccessible(true);

        Object modelA1 = resolveModel.invoke(service, tenantA, "nomic-embed-text");
        Object modelA2 = resolveModel.invoke(service, tenantA, "nomic-embed-text");
        Object modelB = resolveModel.invoke(service, tenantB, "nomic-embed-text");

        assertThat(modelA1).isSameAs(modelA2);
        assertThat(modelB).isNotSameAs(modelA1);
        java.lang.reflect.Field cacheField = KbEmbeddingService.class.getDeclaredField("modelCache");
        cacheField.setAccessible(true);
        Map<String, ?> cache = (Map<String, ?>) cacheField.get(service);
        assertThat(cache).containsKeys(tenantA + ":nomic-embed-text", tenantB + ":nomic-embed-text");
    }
}
