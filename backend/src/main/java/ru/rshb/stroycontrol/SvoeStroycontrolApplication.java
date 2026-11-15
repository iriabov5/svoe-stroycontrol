package ru.rshb.stroycontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Запускает единый Spring Boot backend сопровождения строительства.
 *
 * <p>Вход проверяет модуль аккаунтов. Автонастройка встроенного пользователя отключена, чтобы
 * Spring Boot не создавал дополнительную учётную запись со случайным паролем.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class SvoeStroycontrolApplication {
    /**
     * Запускает backend с настройками Spring Boot из окружения и аргументов.
     *
     * @param args аргументы командной строки Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(SvoeStroycontrolApplication.class, args);
    }
}
