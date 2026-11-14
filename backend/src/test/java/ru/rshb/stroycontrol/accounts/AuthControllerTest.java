package ru.rshb.stroycontrol.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import ru.rshb.stroycontrol.organizations.Organizations;

import java.util.List;
import java.util.UUID;

/** Проверяет выход при общем контексте уже авторизованных параллельных запросов. */
class AuthControllerTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName(
            "Выход инвалидирует сессию, сохраняя principal уже авторизованного параллельного"
                    + " запроса")
    void logoutPreservesInFlightPrincipal() {
        var actor = new AccountView(UUID.randomUUID(), "owner@example.test", AccountRole.CUSTOMER);
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        actor, null, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        var sharedContext = SecurityContextHolder.createEmptyContext();
        sharedContext.setAuthentication(authentication);
        var session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sharedContext);
        var request = new MockHttpServletRequest();
        request.setSession(session);
        var response = new MockHttpServletResponse();
        SecurityContextHolder.setContext(sharedContext);
        var controller =
                new AuthController(
                        mock(AccountService.class),
                        mock(Organizations.class),
                        new HttpSessionSecurityContextRepository(),
                        new HttpSessionCsrfTokenRepository());

        controller.logout(request, response);

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(sharedContext.getAuthentication()).isSameAs(authentication);
        assertThat(sharedContext.getAuthentication().getPrincipal()).isEqualTo(actor);
        assertThat(response.getHeader("Set-Cookie"))
                .contains("JSESSIONID=;", "Max-Age=0", "HttpOnly");
    }
}
