package ru.rshb.stroycontrol.stages;

/** Состояние подсказки перед контрольной точкой строительства. */
enum ControlGateStatus {
    BLOCKED,
    WAITING_CUSTOMER,
    WAITING_CONTRACTOR,
    COMPLETED
}
