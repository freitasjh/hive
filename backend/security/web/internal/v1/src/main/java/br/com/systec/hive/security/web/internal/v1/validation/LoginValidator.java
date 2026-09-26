package br.com.systec.hive.security.web.internal.v1.validation;

import br.com.systec.hive.security.api.validation.ValidLogin;
import br.com.systec.hive.security.web.internal.v1.dto.LoginDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

/**
 * Validador customizado para LoginDTO.
 * Permite validações complexas sem poluir o Record (DTO).
 */
@Component
public class LoginValidator implements ConstraintValidator<ValidLogin, LoginDTO> {

    @Override
    public boolean isValid(LoginDTO loginDTO, ConstraintValidatorContext context) {
        if (loginDTO == null) return true;

        boolean isValid = true;
        context.disableDefaultConstraintViolation(); // Desabilita mensagem padrão da anotação

        // Validação de Username (ex: não pode ser nulo ou vazio)
        if (loginDTO.username() == null || loginDTO.username().isBlank()) {
            addViolation(context, "username", "Usuário é obrigatório");
            isValid = false;
        } else if (loginDTO.username().length() < 3) {
            addViolation(context, "username", "Usuário deve ter no mínimo 3 caracteres");
            isValid = false;
        }

        // Validação de Senha (ex: política de complexidade básica)
        if (loginDTO.password() == null || loginDTO.password().isBlank()) {
            addViolation(context, "password", "Senha é obrigatória");
            isValid = false;
        }

        // Aqui você pode adicionar validações extras:
        // - Sanitização de caracteres especiais (OWASP)
        // - Verificação se o usuário existe no BD (se necessário nesta fase)
        // - Verificação de domínio/inquilino (se passado no payload)

        return isValid;
    }

    private void addViolation(ConstraintValidatorContext context, String field, String message) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
