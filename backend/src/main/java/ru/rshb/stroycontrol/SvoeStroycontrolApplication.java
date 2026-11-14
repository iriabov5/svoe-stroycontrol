package ru.rshb.stroycontrol;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Запускает единый Spring Boot backend сопровождения строительства. */
@SpringBootApplication
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
