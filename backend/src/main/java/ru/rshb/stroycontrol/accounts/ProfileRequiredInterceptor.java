package ru.rshb.stroycontrol.accounts;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.util.Set;

/** Требует актуальное имя перед бизнес-записью, сохраняя чтение прежних аккаунтов. */
@Configuration
@RequiredArgsConstructor
class ProfileRequiredInterceptor implements HandlerInterceptor, WebMvcConfigurer {
    private final Accounts accounts;

    /** {@inheritDoc} */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/v1/projects/**", "/api/v1/projects");
    }

    /**
     * Проверяет профиль после Security/CSRF, но до бизнес-операции контроллера.
     *
     * @param request запрос браузера
     * @param response ответ
     * @param handler выбранный обработчик
     * @return true при чтении либо заполненном имени
     * @throws ApiException с PROFILE_REQUIRED при незаполненном имени
     */
    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())) return true;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AccountView account
                && accounts.profile(account.id()).displayName() == null)
            throw new ApiException(ApiError.PROFILE_REQUIRED);
        return true;
    }
}
