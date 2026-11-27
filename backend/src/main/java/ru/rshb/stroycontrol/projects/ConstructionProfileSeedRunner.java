package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/** Одноразовая команда разработки; отсутствует в обычном backend и не публикует mutation API. */
@Component
@Profile("construction-demo")
@RequiredArgsConstructor
class ConstructionProfileSeedRunner implements ApplicationRunner {
    private final ConstructionProfileSeed seed;
    private final Environment environment;
    private final ConfigurableApplicationContext context;

    /**
     * Заполняет профиль через транзакционный сервис и завершает только этот dev-процесс.
     *
     * @param args аргументы Spring; нужные значения читаются как обязательные properties
     */
    @Override
    public void run(ApplicationArguments args) {
        var ids =
                Arrays.stream(environment.getRequiredProperty("demo.projects").split(","))
                        .map(UUID::fromString)
                        .toList();
        seed.seed(
                ids,
                environment.getRequiredProperty("demo.owner"),
                environment.getRequiredProperty("demo.marker"));
        System.exit(SpringApplication.exit(context));
    }
}
