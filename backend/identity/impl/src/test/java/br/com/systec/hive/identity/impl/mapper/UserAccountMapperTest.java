package br.com.systec.hive.identity.impl.mapper;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.domain.UserAccount;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserAccountMapperTest {

    private final UserAccountMapper mapper = UserAccountMapper.of();

    @Test
    void when_toUser_then_returnUser() {
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername("testuser");
        userAccount.setPassword("password");
        userAccount.setName("Test User");
        userAccount.setDocument("123456789");
        userAccount.setEmail("test@example.com");

        User result = mapper.toUser(userAccount);

        assertNotNull(result);
        assertEquals(userAccount.getUsername(), result.getUsername());
        assertEquals(userAccount.getPassword(), result.getPassword());
        assertEquals(userAccount.getName(), result.getName());
        assertEquals(userAccount.getDocument(), result.getDocument());
        assertEquals(userAccount.getEmail(), result.getEmail());
    }

    @Test
    void when_toTenant_then_returnTenant() {
        UserAccount userAccount = new UserAccount();
        userAccount.setName("Test Tenant");

        Tenant result = mapper.toTenant(userAccount);

        assertNotNull(result);
        assertEquals(userAccount.getName(), result.getName());
    }
}
