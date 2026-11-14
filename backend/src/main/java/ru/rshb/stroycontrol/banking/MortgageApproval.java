package ru.rshb.stroycontrol.banking;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Состояние демонстрационного одобрения для владельца карточки.
 *
 * @param status FOUND, EXPIRED, NOT_FOUND или NOT_CONNECTED
 * @param snapshot синтетический снимок или null, если его нет
 */
record MortgageApproval(String status, MortgageSnapshotView snapshot) {}

/**
 * Кредитный снимок без связей с карточкой и владельцем.
 *
 * @param id идентификатор снимка
 * @param program название демонстрационной программы
 * @param amount сумма одобрения
 * @param termMonths срок в месяцах
 * @param rate ставка
 * @param decisionDate дата решения
 * @param validUntil дата окончания действия
 * @param fetchedAt время получения из Mock API в UTC
 * @param isDemo признак синтетических данных
 */
record MortgageSnapshotView(
        UUID id,
        String program,
        BigDecimal amount,
        int termMonths,
        BigDecimal rate,
        LocalDate decisionDate,
        LocalDate validUntil,
        Instant fetchedAt,
        @JsonProperty("isDemo") boolean isDemo) {}
