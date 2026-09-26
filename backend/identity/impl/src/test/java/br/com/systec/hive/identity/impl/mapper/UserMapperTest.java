package br.com.systec.hive.identity.impl.mapper;

import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.impl.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserMapperTest {

    private final UserMapper mapper = Mappers.getMapper(UserMapper.class);

    @Test
    void when_toEntity_then_returnEntity() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName("Test User");
        user.setUsername("testuser");
        user.setEmail("test@test.com");
        user.setPassword("password");
        user.setTenantId(UUID.randomUUID());

        UserEntity result = mapper.toEntity(user);

        assertNotNull(result);
        assertEquals(user.getId(), result.getId());
        assertEquals(user.getName(), result.getName());
        assertEquals(user.getUsername(), result.getUsername());
        assertEquals(user.getEmail(), result.getEmail());
        assertEquals(user.getPassword(), result.getPassword());
        assertEquals(user.getTenantId(), result.getTenantId());
    }

    @Test
    void when_toDomain_then_returnDomain() {
        UserEntity entity = new UserEntity();
        entity.setId(UUID.randomUUID());
        entity.setName("Test User");
        entity.setUsername("testuser");
        entity.setEmail("test@test.com");
        entity.setPassword("password");
        entity.setTenantId(UUID.randomUUID());

        User result = mapper.toDomain(entity);

        assertNotNull(result);
        assertEquals(entity.getId(), result.getId());
        assertEquals(entity.getName(), result.getName());
        assertEquals(entity.getUsername(), result.getUsername());
        assertEquals(entity.getEmail(), result.getEmail());
        assertEquals(entity.getPassword(), result.getPassword());
        assertEquals(entity.getTenantId(), result.getTenantId());
    }
}
