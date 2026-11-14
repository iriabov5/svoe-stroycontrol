package ru.rshb.stroycontrol.accounts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.organizations.Organizations;

import java.util.List;
import java.util.Map;

/**
 * Открывает операции входа, регистрации и выхода с сессией и CSRF-защитой.
 *
 * <p>При входе заменяет ID сессии и CSRF-токен; пароль не попадает в модель аккаунта или сессию.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {
    private final AccountService accounts;
    private final Organizations organizations;
    private final HttpSessionSecurityContextRepository contexts;
    private final HttpSessionCsrfTokenRepository csrf;

    /**
     * CSRF-токен сессии.
     *
     * @param token значение для заголовка X-CSRF-TOKEN
     */
    record CsrfResponse(String token) {}

    /**
     * Поля регистрации без служебных данных аккаунта.
     *
     * @param email email нового аккаунта
     * @param password пароль, передаваемый только сервису хеширования
     * @param role роль заказчика или подрядчика
     */
    record Registration(String email, String password, AccountRole role, String displayName) {}

    /**
     * Учётные данные однократного запроса входа; не сохраняются в сессии.
     *
     * @param email email аккаунта
     * @param password пароль для проверки хеша
     */
    record Credentials(String email, String password) {}

    /**
     * Возвращает CSRF-токен текущей браузерной сессии.
     *
     * @param token CSRF-токен текущей сессии
     * @return токен, обязательный для изменяющих запросов браузера
     */
    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken());
    }

    /**
     * Регистрирует аккаунт без автоматического входа в сессию.
     *
     * <p>Пароль передаётся только сервису хеширования; ответ содержит безопасную модель аккаунта.
     *
     * @param registration поля регистрации из JSON-тела
     * @return ID, email и роль зарегистрированного аккаунта
     * @throws ApiException при неверных данных или занятом email
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AccountView register(@RequestBody Registration registration) {
        return accounts.register(
                registration.email(),
                registration.password(),
                registration.role(),
                registration.displayName());
    }

    /**
     * Аутентифицирует аккаунт и сохраняет новый контекст безопасности в сессии.
     *
     * <p>При успешном входе меняются ID сессии и CSRF-токен для защиты от фиксации сессии.
     *
     * @param credentials email и пароль из JSON-тела
     * @param request HTTP-запрос браузера
     * @param response HTTP-ответ браузеру
     * @return безопасная модель вошедшего аккаунта
     * @throws ApiException при неверных учётных данных
     */
    @PostMapping("/login")
    AccountView login(
            @RequestBody Credentials credentials,
            HttpServletRequest request,
            HttpServletResponse response) {
        var account = accounts.authenticate(credentials.email(), credentials.password());
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        account,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())));
        new ChangeSessionIdAuthenticationStrategy()
                .onAuthentication(authentication, request, response);
        new CsrfAuthenticationStrategy(csrf).onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return account.withMembership(organizations.membership(account.id()));
    }

    /**
     * Возвращает аккаунт из аутентифицированной сессии.
     *
     * @param account аккаунт текущей аутентифицированной сессии
     * @return ID, email и роль текущего аккаунта
     */
    @GetMapping("/me")
    AccountView me(@AuthenticationPrincipal AccountView account) {
        return accounts.profile(account.id())
                .withMembership(organizations.membership(account.id()));
    }

    /**
     * Завершает сессию и удаляет её cookie у браузера.
     *
     * <p>Изменяющий запрос требует действующего CSRF-токена. Сессия инвалидируется, а контекст
     * текущего потока очищается. Уже авторизованный параллельный запрос сохраняет свой профиль до
     * завершения, поэтому выход не превращает его principal в null после проверки доступа.
     *
     * @param request HTTP-запрос браузера
     * @param response HTTP-ответ браузеру
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        var handler = new SecurityContextLogoutHandler();
        handler.setSecurityContextRepository(contexts);
        // Сессионный SecurityContext может уже использоваться параллельным запросом.
        // Инвалидация сессии запрещает новые запросы; общий объект не обнуляем в середине старых.
        handler.setClearAuthentication(false);
        handler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        response.addHeader(
                "Set-Cookie",
                "JSESSIONID=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
                        + (request.isSecure() ? "; Secure" : ""));
    }

    /**
     * Сохраняет собственное имя с проверкой версии профиля.
     *
     * @param actor аккаунт сессии
     * @param body версия и имя
     * @return обновлённый профиль с действующим членством
     */
    @PatchMapping("/profile")
    AccountView profile(
            @AuthenticationPrincipal AccountView actor, @RequestBody Map<String, Object> body) {
        return accounts.updateProfile(actor.id(), body)
                .withMembership(organizations.membership(actor.id()));
    }
}
