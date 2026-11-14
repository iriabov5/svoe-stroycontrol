package ru.rshb.stroycontrol.accounts;

/** Роль аккаунта. У аккаунта одна роль на всё время жизни записи. */
public enum AccountRole {
    /** Владеет карточками домов и назначает подрядчика. */
    CUSTOMER,
    /** Получает доступ к назначенным ему карточкам. */
    CONTRACTOR
}
