package ru.rshb.stroycontrol.banking;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

/**
 * Выполняет только HTTP-вызовы локального банка; не удерживает транзакцию или блокировку проекта.
 */
@Component
@RequiredArgsConstructor
class BankStageGateway {
    private final StageBankMockApi mock;
    private final RestClient client;

    /**
     * Восстанавливает результат по ключу, затем при необходимости доставляет тот же пакет.
     *
     * @param key стабильный банковский ключ
     * @param revision редакция пакета
     * @param packet неизменяемый состав
     * @return получение либо результат проверки
     */
    Receipt deliver(UUID key, int revision, BankStagePacket packet) {
        try {
            return client.get().uri(mock.url() + "/" + key).retrieve().body(Receipt.class);
        } catch (HttpClientErrorException.NotFound _) {
            return client.post()
                    .uri(mock.url() + "/" + key)
                    .body(Map.of("revision", revision, "packet", packet, "isDemo", true))
                    .retrieve()
                    .body(Receipt.class);
        }
    }

    /**
     * Квитанция, передающая только демонстрационный результат.
     *
     * @param state RECEIVED, ACCEPTED либо NEEDS_INFORMATION
     * @param reference ID квитанции
     * @param reason причина дополнения либо null
     */
    record Receipt(String state, String reference, String reason) {}
}
