package br.com.systec.hive.knowledge.impl.service;

import br.com.systec.hive.knowledge.impl.fake.PrefixAwareEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KbEmbeddingServiceEvictionTest {

    @Mock
    private KbEmbeddingModelFactory modelFactory;

    @Test
    void when_resolveModel_overCapacity_then_evictsOldestAndBoundsSize() throws Exception {
        when(modelFactory.create(any(), any()))
                .thenAnswer(inv -> new PrefixAwareEmbeddingModel("nomic", 768));
        KbEmbeddingService svc = new KbEmbeddingService(modelFactory);
        Method resolveModel = KbEmbeddingService.class.getDeclaredMethod("resolveModel", UUID.class, String.class);
        resolveModel.setAccessible(true);

        int total = KbEmbeddingService.MAX_CACHE_SIZE + 20;
        for (int i = 0; i < total; i++) {
            resolveModel.invoke(svc, UUID.randomUUID(), "nomic-embed-text-" + i);
        }

        assertThat(svc.cacheSize()).isLessThanOrEqualTo(KbEmbeddingService.MAX_CACHE_SIZE);
    }
}
