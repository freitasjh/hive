package br.com.systec.hive.identity.api.service;

import br.com.systec.hive.commons.api.service.CrudService;
import br.com.systec.hive.identity.api.domain.User;

import java.util.Optional;
import java.util.UUID;

public interface UserService extends CrudService<User> {

    void saveTenantId(UUID userId, UUID tenantId);

    User findByUsername(String username);

    User findById(UUID id);
}
