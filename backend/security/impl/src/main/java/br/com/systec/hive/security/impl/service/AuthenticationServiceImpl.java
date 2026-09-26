package br.com.systec.hive.security.impl.service;

import br.com.systec.hive.commons.api.exceptions.BaseException;
import br.com.systec.hive.commons.api.exceptions.ObjectNotFoundException;
import br.com.systec.hive.identity.api.service.UserService;
import br.com.systec.hive.security.api.service.AuthenticationService;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationServiceImpl implements AuthenticationService {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationServiceImpl.class);
    private final UserService userService;

    public AuthenticationServiceImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public @NullMarked UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        try {
            log.info("@@@ Realizando a busca do usuario @@@");
            return userService.findByUsername(username);
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (ObjectNotFoundException e) {
            throw new UsernameNotFoundException(e.getMessage(), e);
        } catch (Exception e) {
            throw new BaseException("Erro ao tentar encontrar o usuario", e);
        }
    }
}
