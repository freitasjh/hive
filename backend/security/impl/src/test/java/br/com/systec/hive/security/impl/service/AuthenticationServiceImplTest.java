package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.exceptions.UserNotFoundException;
import br.com.systec.hive.identity.api.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceImplTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private AuthenticationServiceImpl service;

    @Test
    void when_loadUserByUsername_withExistingUser_then_returnUserDetails() {
        String username = "testuser";
        User user = new User();
        user.setUsername(username);

        when(userService.findByUsername(username)).thenReturn(user);

        UserDetails result = service.loadUserByUsername(username);

        assertNotNull(result);
        assertEquals(username, result.getUsername());
    }

    @Test
    void when_loadUserByUsername_with_unknownUser_then_throwUsernameNotFound() {
        String username = "noexiste_xyz";
        when(userService.findByUsername(username)).thenThrow(new UserNotFoundException());

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername(username));
    }

    @Test
    void when_loadUserByUsername_withException_then_throwBaseException() {
        String username = "testuser";
        when(userService.findByUsername(username)).thenThrow(new RuntimeException("Error"));

        assertThrows(BaseException.class, () -> service.loadUserByUsername(username));
    }
}
