package br.com.systec.hive.integrationtest.support;

import br.com.systec.hive.HiveApplication;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

@SpringBootTest(classes = HiveApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class WebIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;
    static final GenericContainer<?> REDIS;

    static {
        POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");
        POSTGRES.start();
        REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379);
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    @Autowired
    protected JwtTestHelper jwtTestHelper;

    @BeforeEach
    protected void resetDatabase() {
        databaseCleaner.reset();
    }

    protected void seedTenantAndUser(UUID tenantId, String username, String rawPassword) {
        UUID userId = UUID.randomUUID();
        String passwordHash = new BCryptPasswordEncoder().encode(rawPassword);
        jdbcTemplate.update(
                "INSERT INTO tenant (id, name, description, active, owner_id) VALUES (?, ?, ?, ?, ?)",
                tenantId, "IT Tenant", "seeded by IT", true, userId);
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, first_name, last_name, password, is_enabled, tenant_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                userId, username, username + "@example.com", "IT", "User", passwordHash, true, tenantId);
    }
}
