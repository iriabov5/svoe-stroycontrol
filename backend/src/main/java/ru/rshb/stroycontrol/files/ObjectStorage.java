package ru.rshb.stroycontrol.files;

/** Изолирует S3 от прикладных транзакций и позволяет проверять сбои независимо от БД. */
interface ObjectStorage {
    /**
     * Записывает содержимое под серверным ключом; повтор тех же байтов безопасен.
     *
     * @param key закрытый ключ
     * @param contentType проверенный MIME
     * @param hash SHA-256
     * @param bytes байты
     */
    void put(String key, String contentType, String hash, byte[] bytes);

    /**
     * Читает объект после проверки авторизации вызывающим сервисом.
     *
     * @param key закрытый ключ
     * @return содержимое
     */
    byte[] get(String key);

    /**
     * Проверяет хеш уже записанного объекта при восстановлении неизвестного исхода.
     *
     * @param key ключ
     * @param hash ожидаемый хеш
     * @return совпал ли объект
     */
    boolean matches(String key, String hash);

    /**
     * Удаляет только доказанно временный объект.
     *
     * @param key ключ после фиксации DELETING
     */
    void delete(String key);
}
