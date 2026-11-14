package ru.rshb.stroycontrol.banking;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/** Настраивает локальный банковский адаптер и короткие транзакции по обе стороны HTTP-вызова. */
@Configuration
class BankingConfig {
    /**
     * Создаёт клиент локального Mock API без банковских учётных данных.
     *
     * @return потокобезопасный клиент для синтетических ответов
     */
    @Bean
    RestClient bankRestClient() {
        var factory =
                new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Создаёт границу фиксации импорта без удержания блокировки во время HTTP-вызова.
     *
     * @param transactions менеджер общей JPA-транзакции приложения
     * @return шаблон транзакций с настройками по умолчанию
     */
    @Bean
    TransactionTemplate bankTransactions(PlatformTransactionManager transactions) {
        return new TransactionTemplate(transactions);
    }
}
