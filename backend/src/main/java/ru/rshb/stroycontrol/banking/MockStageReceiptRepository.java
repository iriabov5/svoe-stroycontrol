package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Реестр уникальных квитанций только синтетического банка. */
interface MockStageReceiptRepository extends JpaRepository<MockStageReceipt, UUID> {}
