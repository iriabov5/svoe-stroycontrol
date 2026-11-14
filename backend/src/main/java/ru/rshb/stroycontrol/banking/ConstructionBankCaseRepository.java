package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Хранит одно синтетическое строительное дело на проект. */
interface ConstructionBankCaseRepository extends JpaRepository<ConstructionBankCase, UUID> {}
