package ru.rshb.stroycontrol.stages;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** Задаёт общие часы приложения для фактических дат и сроков строительных этапов. */
@Configuration
class StageClockConfig {
    /**
     * Создаёт единые часы для дат строительства в московском часовом поясе.
     *
     * @return системные часы часового пояса Europe/Moscow
     */
    @Bean
    Clock stageClock() {
        return Clock.system(ZoneId.of("Europe/Moscow"));
    }
}
