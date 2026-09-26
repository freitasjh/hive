package br.com.systec.hive.identity.impl.service;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.api.service.TenantService;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.domain.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private TenantService tenantService;

    @InjectMocks
    private UserAccountServiceImpl service;

    @Test
    void when_create_then_success() {
        UserAccount userAccount = new UserAccount();
        userAccount.setName("Test User");
        userAccount.setUsername("testuser");
        userAccount.setPassword("Password123");
        userAccount.setEmail("test@test.com");
        userAccount.setDocument("12345678900");

        User userSaved = new User();
        userSaved.setId(UUID.randomUUID());
        userSaved.setName(userAccount.getName());

        Tenant tenantSaved = new Tenant();
        tenantSaved.setId(UUID.randomUUID());
        tenantSaved.setName(userAccount.getName());

        when(userService.create(any(User.class))).thenReturn(userSaved);
        when(tenantService.create(any(Tenant.class))).thenReturn(tenantSaved);

        service.create(userAccount);

        verify(userService).create(any(User.class));
        verify(tenantService).create(any(Tenant.class));
        verify(userService).saveTenantId(userSaved.getId(), tenantSaved.getId());

        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        verify(tenantService).create(tenantCaptor.capture());
        assertEquals(userSaved.getId(), tenantCaptor.getValue().getOwnerId());
    }
}
