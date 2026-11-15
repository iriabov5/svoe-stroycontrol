package ru.rshb.stroycontrol.common;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Регистрирует журнал запросов перед Spring Security, включая окончательную обработку ошибки. */
@Configuration
class RequestLoggingConfig {
    /**
     * Включает обработку первоначального запроса и повторной диспетчеризации ошибки контейнером.
     *
     * @return регистрация фильтра с приоритетом перед цепочкой безопасности
     */
    @Bean
    FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilter() {
        var registration = new FilterRegistrationBean<>(new RequestLoggingFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ERROR);
        return registration;
    }
}
