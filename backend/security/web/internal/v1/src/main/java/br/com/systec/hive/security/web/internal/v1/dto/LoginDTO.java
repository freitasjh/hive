package br.com.systec.hive.security.web.internal.v1.dto;

import br.com.systec.hive.security.api.validation.ValidLogin;

//@ValidLogin
public record LoginDTO(
        String username,
        String password) {
}