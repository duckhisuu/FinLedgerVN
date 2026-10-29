package vn.finledger.auth.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class CurrentUserProvider {

    public Optional<AuthenticatedUser> findCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<UUID> findUserId() {
        return findCurrentUser().map(AuthenticatedUser::id);
    }

    public UUID requireUserId() {
        return findUserId().orElseThrow(() ->
                new AuthenticationCredentialsNotFoundException("Vui lòng đăng nhập để tiếp tục."));
    }
}

