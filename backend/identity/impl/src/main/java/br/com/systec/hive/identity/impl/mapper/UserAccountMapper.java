package br.com.systec.hive.identity.impl.mapper;

import br.com.systec.hive.api.domain.Tenant;
import br.com.systec.hive.identity.api.domain.User;
import br.com.systec.hive.identity.api.domain.UserAccount;

public class UserAccountMapper {

    private UserAccountMapper() {}

    public static UserAccountMapper of() {
        return new UserAccountMapper();
    }

    public User toUser(UserAccount userAccount) {
        User user = new User();
        user.setUsername(userAccount.getUsername());
        user.setPassword(userAccount.getPassword());
        user.setName(userAccount.getName());
        user.setDocument(userAccount.getDocument());
        user.setEmail(userAccount.getEmail());

        return user;
    }

    public Tenant toTenant(UserAccount userAccount) {
        Tenant tenant = new Tenant();
        tenant.setName(userAccount.getName());

        return tenant;
    }
}
