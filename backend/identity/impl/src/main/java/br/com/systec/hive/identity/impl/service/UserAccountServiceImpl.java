package br.com.systec.hive.identity.impl.service;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.api.service.TenantService;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.domain.UserAccount;
import br.com.systec.hive.identity.api.service.UserAccountService;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.identity.impl.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountServiceImpl implements UserAccountService {
    private static final Logger log = LoggerFactory.getLogger(UserAccountServiceImpl.class);

    private final UserService userService;
    private final TenantService tenantService;

    public UserAccountServiceImpl(UserService userService, TenantService tenantService) {
        this.userService = userService;
        this.tenantService = tenantService;
    }

    @Override
    @Transactional
    public void create(UserAccount userAccount) {
        log.info("@@@ Criando usuario: {}", userAccount.getName());

        User userBeforeCreate = UserAccountMapper.of().toUser(userAccount);
        User userSaved = userService.create(userBeforeCreate);

        Tenant tenantBeforeCreate = UserAccountMapper.of().toTenant(userAccount);
        tenantBeforeCreate.setOwnerId(userSaved.getId());

        Tenant tenantSaved = tenantService.create(tenantBeforeCreate);
        userSaved.setTenantId(tenantSaved.getId());

        userService.saveTenantId(userSaved.getId(), tenantSaved.getId());
    }
}
