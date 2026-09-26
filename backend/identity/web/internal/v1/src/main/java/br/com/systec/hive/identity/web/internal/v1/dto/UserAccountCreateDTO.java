package br.com.systec.hive.identity.web.internal.v1.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserAccountCreateDTO(
        @NotBlank(message = "name: must not be blank")
        @Size(min = 2, max = 80, message = "name: size must be between 2 and 80")
        String name,

        @NotBlank(message = "username: must not be blank")
        @Size(min = 3, max = 40, message = "username: size must be between 3 and 40")
        @Pattern(regexp = "^[a-zA-Z0-9._]+$", message = "username: must match ^[a-zA-Z0-9._]+$")
        String username,

        @NotBlank(message = "password: must not be blank")
        @Size(min = 8, max = 64, message = "password: size must be between 8 and 64")
        String password,

        @NotBlank(message = "email: must not be blank")
        @Email(message = "email: must be a well-formed email address")
        @Size(max = 255, message = "email: size must be less than 255")
        String email,

        @NotBlank(message = "document: must not be blank")
        @Size(min = 11, max = 18, message = "document: size must be between 11 and 18")
        String document) {}
