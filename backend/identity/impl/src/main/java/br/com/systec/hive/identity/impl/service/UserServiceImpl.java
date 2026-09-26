package br.com.systec.hive.identity.impl.service;

import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.exceptions.UserDocumentAlreadyExistsException;
import br.com.systec.hive.identity.api.exceptions.UserDocumentNotProvidedException;
import br.com.systec.hive.identity.api.exceptions.UserNameNotProvidedException;
import br.com.systec.hive.identity.api.exceptions.UserNotFoundException;
import br.com.systec.hive.identity.api.exceptions.UserPasswordNotProvideInformationException;
import br.com.systec.hive.identity.api.exceptions.UserUsernameOrEmailExistException;
import br.com.systec.hive.identity.api.exceptions.UsernameNotProvideInformationException;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.identity.impl.entity.UserEntity;
import br.com.systec.hive.identity.impl.mapper.UserMapper;
import br.com.systec.hive.identity.impl.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;

    public UserServiceImpl(UserRepository repository, PasswordEncoder passwordEncoder, UserMapper mapper) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public User create(User user) {
        UserEntity userToSave = mapper.toEntity(user);

        validateSaveNewUser(userToSave);
        userToSave.setPassword(passwordEncoder.encode(userToSave.getPassword()));

        UserEntity userSaved = repository.save(userToSave);

        return mapper.toDomain(userSaved);
    }

    @Override
    @Transactional
    public User update(User user) {
        UserEntity userBeforeSave = mapper.toEntity(user);
        UserEntity userAfterUpdate = repository.save(userBeforeSave);

        return mapper.toDomain(userAfterUpdate);
    }

    @Transactional
    public void saveTenantId(UUID userId, UUID tenantId) {
        repository.findById(userId).orElseThrow(UserNotFoundException::new);
        repository.updateTenantId(userId, tenantId);
    }

    @Transactional(readOnly = true)
    @Override
    public User findByUsername(String username) {
        UserEntity user = repository.findByUsername(username).orElseThrow(UserNotFoundException::new);
        return mapper.toDomain(user);
    }

    @Override
    @Transactional
    public User delete(User obj) {
        return null;
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(UUID id) {
        UserEntity userEntity = repository.findById(id).orElseThrow(UserNotFoundException::new);
        return mapper.toDomain(userEntity);
    }

    private void validateSaveNewUser(UserEntity user) {
        if (user.getPassword() == null || user.getPassword().isBlank()
                || user.getPassword().length() < 8 || user.getPassword().length() > 64) {
            throw new UserPasswordNotProvideInformationException();
        }

        if (user.getName() == null || user.getName().isBlank()
                || user.getName().trim().length() < 2 || user.getName().length() > 80) {
            throw new UserNameNotProvidedException();
        }

        if (user.getDocument() == null || user.getDocument().isBlank()
                || user.getDocument().length() < 11 || user.getDocument().length() > 18) {
            throw new UserDocumentNotProvidedException();
        }

        if (user.getUsername() == null || user.getUsername().isBlank()) {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                throw new UsernameNotProvideInformationException();
            }
            user.setUsername(user.getEmail());
        }

        if (!user.getUsername().matches("^[a-zA-Z0-9._]+$")) {
            throw new IllegalArgumentException("username: must match ^[a-zA-Z0-9._]+$");
        }

        if (user.getEmail() == null || user.getEmail().isBlank()
                || !user.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("email: must be a well-formed email address");
        }

        Optional<UserEntity> userExist = repository.findByUsernameOrEmail(user.getUsername(), user.getEmail());
        if (userExist.isPresent()) {
            throw new UserUsernameOrEmailExistException();
        }

        Optional<UserEntity> documentExist = repository.findByDocument(user.getDocument());
        if (documentExist.isPresent()) {
            throw new UserDocumentAlreadyExistsException();
        }
    }
}
