package br.com.systec.hive.assistant.impl.util;

import br.com.systec.hive.identity.api.domain.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AccessDeniedException("Access denied: no authentication");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            UUID id = user.getId();
            if (id == null) {
                throw new AccessDeniedException("Access denied: user id is null");
            }
            return id;
        }
        throw new AccessDeniedException("Access denied: invalid principal type " + principal.getClass().getName());
    }
}
