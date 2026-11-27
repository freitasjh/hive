package br.com.systec.hive.config;

import br.com.systec.hive.ai.api.port.TurnPromptPolicy;
import br.com.systec.hive.ai.api.port.TurnStreamGateway;
import br.com.systec.hive.ai.impl.LangChain4jTurnStreamGateway;
import br.com.systec.hive.ai.impl.StreamingMetrics;
import br.com.systec.hive.provider.api.LlmStreamingClientFactory;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * Production wire for the streaming turn (ADR-001 review finding). A missing policy, factory or
 * scheduler fails fast at startup rather than at the first SSE request.
 */
@Configuration
public class StreamingConfig {

    @Bean
    public StreamingMetrics streamingMetrics() {
        return new StreamingMetrics();
    }

    @Bean(destroyMethod = "shutdownNow")
    public ScheduledExecutorService streamScheduler() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "hive-stream-release");
            thread.setDaemon(true);
            return thread;
        });
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    @Bean
    public TurnStreamGateway turnStreamGateway(TurnPromptPolicy promptPolicy,
                                               StreamingMetrics metrics,
                                               @Qualifier("streamScheduler") ScheduledExecutorService scheduler,
                                               LlmStreamingClientFactory<StreamingChatModel> clientFactory) {
        Objects.requireNonNull(promptPolicy, "TurnPromptPolicy bean is required");
        Objects.requireNonNull(clientFactory, "LlmStreamingClientFactory bean is required");
        return new LangChain4jTurnStreamGateway(clientFactory, promptPolicy, metrics, scheduler);
    }
}
