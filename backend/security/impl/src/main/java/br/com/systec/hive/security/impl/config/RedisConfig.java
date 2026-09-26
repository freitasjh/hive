package br.com.systec.hive.security.impl.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuração moderna do Redis para o projeto Hive (Spring Boot 3+ / Java 21).
 * Resolve depreciações e garante suporte a Records e Java Time (JSR310).
 */
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate otimizado para o projeto.
     * Jackson2JsonRedisSerializer é a substituição moderna e segura para GenericJackson2JsonRedisSerializer.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        // Garantindo que o ObjectMapper suporte os novos tipos do Java 8+
        ObjectMapper redisObjectMapper = new ObjectMapper();
        redisObjectMapper.registerModule(new JavaTimeModule());

        // Configura o serializador JSON moderno
        JacksonJsonRedisSerializer<Object> serializer = new JacksonJsonRedisSerializer<>(Object.class);

        return generateTemplate(connectionFactory, serializer);
    }

    private @NonNull RedisTemplate<String, Object> generateTemplate(RedisConnectionFactory connectionFactory, JacksonJsonRedisSerializer<Object> serializer) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Chaves como String legível
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Valores como JSON estruturado
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Template padrão para operações diretas com String.
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
