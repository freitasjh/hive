package br.com.systec.hive.security.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * Anotação customizada para validar o LoginDTO.
 * Permite centralizar regras complexas e manter o DTO limpo.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {}) // A implementação será ligada via Spring
@Documented
public @interface ValidLogin {
    String message() default "Dados de login inválidos";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
