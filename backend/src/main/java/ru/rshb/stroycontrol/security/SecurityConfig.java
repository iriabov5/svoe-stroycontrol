package ru.rshb.stroycontrol.security;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiProblems;

/** Настраивает сессионную аутентификацию, CSRF и единые ответы об отказе в доступе. */
@Configuration
class SecurityConfig {
    /**
     * Создаёт Argon2id-кодировщик паролей с параметрами Spring Security.
     *
     * @return кодировщик, которым сервис аккаунтов хеширует пароль
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Хранит CSRF-токен в HTTP-сессии.
     *
     * @return репозиторий токена для изменяющих запросов браузера
     */
    @Bean
    HttpSessionCsrfTokenRepository csrfRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    /**
     * Сохраняет контекст безопасности только явным вызовом после входа.
     *
     * @return сессионное хранилище контекста аутентификации
     */
    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Требует сессию для API, кроме регистрации, входа, выдачи CSRF и проверки здоровья.
     *
     * <p>Неверный CSRF и отсутствие сессии получают контрактный problem+json. Форма и HTTP Basic
     * отключены: вход выполняет контроллер аккаунтов.
     *
     * @param http построитель цепочки фильтров
     * @param problems запись problem+json
     * @param csrf хранилище CSRF-токена сессии
     * @param contexts хранилище контекста безопасности
     * @return собранная цепочка фильтров
     * @throws BeanInitializationException если построитель Spring Security отклонил настройку
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ApiProblems problems,
            HttpSessionCsrfTokenRepository csrf,
            HttpSessionSecurityContextRepository contexts) {
        try {
            return http.csrf(config -> config.csrfTokenRepository(csrf))
                    .securityContext(config ->
                            config.securityContextRepository(contexts).requireExplicitSave(true))
                    .requestCache(config -> config.disable())
                    .formLogin(config -> config.disable())
                    .httpBasic(config -> config.disable())
                    .logout(config -> config.disable())
                    .authorizeHttpRequests(config ->
                            config.requestMatchers(
                                            "/api/v1/auth/csrf",
                                            "/api/v1/auth/register",
                                            "/api/v1/auth/login",
                                            "/actuator/health",
                                            "/actuator/health/**",
                                            "/error")
                                    .permitAll()
                                    .anyRequest()
                                    .authenticated())
                    .exceptionHandling(config ->
                            config.authenticationEntryPoint((request, response, exception) ->
                                            problems.write(
                                                    request, response, ApiError.AUTH_REQUIRED))
                                    .accessDeniedHandler((request, response, exception) ->
                                            problems.write(
                                                    request,
                                                    response,
                                                    exception instanceof CsrfException
                                                            ? ApiError.INVALID_CSRF_TOKEN
                                                            : ApiError.FORBIDDEN)))
                    .build();
        } catch (Exception exception) {
            throw new BeanInitializationException(
                    "Не удалось настроить цепочку безопасности", exception);
        }
    }
}
