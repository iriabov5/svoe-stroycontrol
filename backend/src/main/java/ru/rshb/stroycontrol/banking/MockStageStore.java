package ru.rshb.stroycontrol.banking;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.common.CommandFingerprint;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Отделяет HTTP симулятора от транзакций уникальных квитанций. */
@Service
@RequiredArgsConstructor
class MockStageStore {
    private static final String STATE_NEEDS_INFORMATION = "NEEDS_INFORMATION";

    private final MockStageReceiptRepository repository;
    private final CommandFingerprint fingerprint;
    private final Clock clock;

    /**
     * Сохраняет один пакет по ключу; несовпадающее содержимое отклоняется.
     *
     * @param key банковский ключ
     * @param body синтетический пакет
     * @param scenario сценарий локального стенда
     * @return квитанция получения или уже принятое решение
     */
    @Transactional
    BankStageGateway.Receipt receive(UUID key, Map<String, Object> body, String scenario) {
        String hash = fingerprint.of(key.toString(), body);
        var old = repository.findById(key);
        if (old.isPresent()) {
            if (!old.get().getPayloadHash().equals(hash))
                throw new IllegalArgumentException("key conflict");
            return view(old.get());
        }
        boolean needs =
                scenario.equals(STATE_NEEDS_INFORMATION)
                        && body.get("revision") instanceof Number n
                        && n.intValue() == 1;
        var row =
                MockStageReceipt.builder()
                        .id(key)
                        .payloadHash(hash)
                        .targetState(needs ? STATE_NEEDS_INFORMATION : "ACCEPTED")
                        .reason(
                                needs
                                        ? "Добавьте пояснение и документ по выполненному этапу"
                                        : null)
                        .receivedAt(clock.instant())
                        .build();
        return view(repository.saveAndFlush(row));
    }

    /**
     * Возвращает результат проверки после факта получения.
     *
     * @param key ключ
     * @return квитанция либо null при отсутствии доставки
     */
    @Transactional
    BankStageGateway.Receipt poll(UUID key) {
        var row = repository.findById(key).orElse(null);
        if (row == null) return null;
        row.setState(row.getTargetState());
        return view(row);
    }

    private BankStageGateway.Receipt view(MockStageReceipt row) {
        return new BankStageGateway.Receipt(
                row.getState(),
                "DEMO-" + row.getId(),
                row.getState().equals(STATE_NEEDS_INFORMATION) ? row.getReason() : null);
    }
}
