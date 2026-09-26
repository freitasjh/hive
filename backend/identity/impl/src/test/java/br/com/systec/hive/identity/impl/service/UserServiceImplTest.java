package br.com.systec.hive.identity.impl.service;

import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.exceptions.UserDocumentAlreadyExistsException;
import br.com.systec.hive.identity.api.exceptions.UserDocumentNotProvidedException;
import br.com.systec.hive.identity.api.exceptions.UserNameNotProvidedException;
import br.com.systec.hive.identity.api.exceptions.UserNotFoundException;
import br.com.systec.hive.identity.api.exceptions.UserPasswordNotProvideInformationException;
import br.com.systec.hive.identity.api.exceptions.UserUsernameOrEmailExistException;
import br.com.systec.hive.identity.api.exceptions.UsernameNotProvideInformationException;
import br.com.systec.hive.identity.impl.entity.UserEntity;
import br.com.systec.hive.identity.impl.mapper.UserMapper;
import br.com.systec.hive.identity.impl.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper mapper;

    @InjectMocks
    private UserServiceImpl service;

    private UserEntity validEntity() {
        UserEntity e = new UserEntity();
        e.setName("Joao Silva");
        e.setUsername("joao.silva");
        e.setPassword("Password123");
        e.setEmail("joao@example.com");
        e.setDocument("12345678900");
        return e;
    }

    @Test
    void when_create_withValidUser_then_success() {
        User user = new User();
        user.setName("Joao Silva");
        user.setUsername("testuser");
        user.setPassword("Password123");
        user.setEmail("test@test.com");
        user.setDocument("12345678900");

        UserEntity userEntity = validEntity();
        userEntity.setUsername("testuser");
        userEntity.setEmail("test@test.com");

        when(mapper.toEntity(user)).thenReturn(userEntity);
        when(repository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findByDocument(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
        when(repository.save(any(UserEntity.class))).thenReturn(userEntity);
        when(mapper.toDomain(any(UserEntity.class))).thenReturn(user);

        User result = service.create(user);

        assertNotNull(result);
        verify(repository).save(any(UserEntity.class));
        assertEquals("encodedPassword", userEntity.getPassword());
    }

    @Test
    void when_create_withMissingPassword_then_throwException() {
        User user = new User();
        user.setUsername("testuser");

        UserEntity userEntity = new UserEntity();
        userEntity.setUsername("testuser");
        userEntity.setPassword(null);
        userEntity.setName("Joao");
        userEntity.setDocument("12345678900");
        userEntity.setEmail("test@test.com");

        when(mapper.toEntity(user)).thenReturn(userEntity);

        assertThrows(UserPasswordNotProvideInformationException.class, () -> service.create(user));
    }

    @Test
    void when_create_withShortPassword_then_throwException() {
        User user = new User();
        user.setUsername("testuser");
        UserEntity userEntity = validEntity();
        userEntity.setPassword("short");
        when(mapper.toEntity(user)).thenReturn(userEntity);
        assertThrows(UserPasswordNotProvideInformationException.class, () -> service.create(user));
    }

    @Test
    void when_create_withMissingName_then_throwException() {
        User user = new User();
        user.setUsername("testuser");
        UserEntity userEntity = validEntity();
        userEntity.setName("");
        when(mapper.toEntity(user)).thenReturn(userEntity);
        assertThrows(UserNameNotProvidedException.class, () -> service.create(user));
    }

    @Test
    void when_create_withMissingDocument_then_throwException() {
        User user = new User();
        user.setUsername("testuser");
        UserEntity userEntity = validEntity();
        userEntity.setDocument("");
        when(mapper.toEntity(user)).thenReturn(userEntity);
        assertThrows(UserDocumentNotProvidedException.class, () -> service.create(user));
    }

    @Test
    void when_create_withMissingUsername_then_throwException() {
        User user = new User();
        user.setPassword("Password123");

        UserEntity userEntity = validEntity();
        userEntity.setUsername("");
        userEntity.setEmail("");
        userEntity.setPassword("Password123");

        when(mapper.toEntity(user)).thenReturn(userEntity);

        assertThrows(UsernameNotProvideInformationException.class, () -> service.create(user));
    }

    @Test
    void when_create_withExistingUser_then_throwException() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("Password123");
        user.setEmail("test@test.com");
        user.setName("Joao");
        user.setDocument("12345678900");

        UserEntity userEntity = validEntity();
        userEntity.setUsername("testuser");
        userEntity.setEmail("test@test.com");

        when(mapper.toEntity(user)).thenReturn(userEntity);
        when(repository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.of(new UserEntity()));

        assertThrows(UserUsernameOrEmailExistException.class, () -> service.create(user));
    }

    @Test
    void when_create_withDuplicateDocument_then_throwException() {
        User user = new User();
        user.setUsername("newuser");
        user.setPassword("Password123");
        user.setEmail("new@example.com");
        user.setName("Joao Silva");
        user.setDocument("12345678900");

        UserEntity userEntity = validEntity();
        userEntity.setUsername("newuser");
        userEntity.setEmail("new@example.com");

        when(mapper.toEntity(user)).thenReturn(userEntity);
        when(repository.findByUsernameOrEmail(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.findByDocument(anyString())).thenReturn(Optional.of(new UserEntity()));

        assertThrows(UserDocumentAlreadyExistsException.class, () -> service.create(user));
    }

    @Test
    void when_create_withInvalidUsernamePattern_then_throwException() {
        User user = new User();
        UserEntity userEntity = validEntity();
        userEntity.setUsername("invalid username!");
        when(mapper.toEntity(user)).thenReturn(userEntity);
        assertThrows(IllegalArgumentException.class, () -> service.create(user));
    }

    @Test
    void when_create_withInvalidEmail_then_throwException() {
        User user = new User();
        UserEntity userEntity = validEntity();
        userEntity.setEmail("not-an-email");
        when(mapper.toEntity(user)).thenReturn(userEntity);
        assertThrows(IllegalArgumentException.class, () -> service.create(user));
    }

    @Test
    void when_findById_withExistingId_then_returnUser() {
        UUID id = UUID.randomUUID();
        UserEntity userEntity = new UserEntity();
        User user = new User();

        when(repository.findById(id)).thenReturn(Optional.of(userEntity));
        when(mapper.toDomain(userEntity)).thenReturn(user);

        User result = service.findById(id);

        assertNotNull(result);
        assertEquals(user, result);
    }

    @Test
    void when_findById_withNonExistingId_then_throwException() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.findById(id));
    }

    @Test
    void when_findByUsername_withExistingUsername_then_returnUser() {
        String username = "testuser";
        UserEntity userEntity = new UserEntity();
        User user = new User();

        when(repository.findByUsername(username)).thenReturn(Optional.of(userEntity));
        when(mapper.toDomain(userEntity)).thenReturn(user);

        User result = service.findByUsername(username);

        assertNotNull(result);
        assertEquals(user, result);
    }

    @Test
    void when_saveTenantId_withValidData_then_success() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserEntity userEntity = new UserEntity();

        when(repository.findById(userId)).thenReturn(Optional.of(userEntity));

        service.saveTenantId(userId, tenantId);

        verify(repository).findById(userId);
        verify(repository).updateTenantId(userId, tenantId);
        verify(repository, never()).save(any(UserEntity.class));
    }
}
