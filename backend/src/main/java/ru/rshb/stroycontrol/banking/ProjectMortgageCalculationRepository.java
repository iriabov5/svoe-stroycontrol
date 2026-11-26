package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Хранит снимки своего модуля, доступ проверяет сервис планирования. */
interface ProjectMortgageCalculationRepository
        extends JpaRepository<ProjectMortgageCalculation, UUID> {}
