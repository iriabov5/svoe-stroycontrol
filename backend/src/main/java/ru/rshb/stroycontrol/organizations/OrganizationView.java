package ru.rshb.stroycontrol.organizations;

import java.util.UUID;

/**
 * Реквизиты, указанные подрядчиком, без утверждения о проверке ФНС.
 *
 * @param id организация
 * @param ownerAccountId основной контакт и руководитель
 * @param type организация либо ИП
 * @param displayName название для показа
 * @param legalName полное название
 * @param inn ИНН
 * @param address адрес
 * @param phone телефон либо null
 * @param version ожидаемая версия изменений
 */
public record OrganizationView(
        UUID id,
        UUID ownerAccountId,
        String type,
        String displayName,
        String legalName,
        String inn,
        String address,
        String phone,
        long version) {}
