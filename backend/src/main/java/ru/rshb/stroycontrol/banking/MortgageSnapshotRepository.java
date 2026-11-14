package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Хранит неизменяемые синтетические одобрения; права на карточку проверяет банковский сервис. */
interface MortgageSnapshotRepository extends JpaRepository<MortgageSnapshot, UUID> {}
