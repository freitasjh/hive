package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.security.api.domain.Login;
import br.com.systec.hive.security.api.domain.LoginAuthenticate;
import br.com.systec.hive.security.api.exceptions.InvalidCredentialsException;
import br.com.systec.hive.security.api.service.SecurityTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityTokenService securityTokenService;

    @InjectMocks
    private LoginServiceImpl service;

    @Test
    void when_login_with_validCredentials_then_returnLoginAuthenticate() {
        Login login = new Login("user", "pass");
        Authentication authentication = mock(Authentication.class);
        LoginAuthenticate expected = new LoginAuthenticate(null, "token", "Bearer", "", "refreshToken");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(authentication);
        when(securityTokenService.generateTokenAndRefreshToken(authentication)).thenReturn(expected);

        LoginAuthenticate result = service.login(login);

        assertNotNull(result);
        assertEquals(expected, result);
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
        verify(securityTokenService).generateTokenAndRefreshToken(authentication);
    }

    @Test
    void when_login_with_invalidCredentials_then_throw401() {
        Login login = new Login("user", "wrong-pass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> service.login(login))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas")
                .satisfies(ex -> assertThat(((InvalidCredentialsException) ex).getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(securityTokenService, never()).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_login_with_unknownUser_then_throw401() {
        Login login = new Login("noexiste_xyz", "errada123");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new InternalAuthenticationServiceException("User not found"));

        assertThatThrownBy(() -> service.login(login))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Credenciais inválidas");
        verify(securityTokenService, never()).generateTokenAndRefreshToken(any());
    }

    @Test
    void when_refreshToken_then_returnNewToken() {
        LoginAuthenticate expected = new LoginAuthenticate(null, "new-token", "Bearer", "", "new-refreshToken");
        when(securityTokenService.generateNewAccessTokenByRefreshToken()).thenReturn(expected);

        LoginAuthenticate result = service.refreshToken();

        assertNotNull(result);
        assertEquals(expected, result);
        verify(securityTokenService).generateNewAccessTokenByRefreshToken();
    }
}
