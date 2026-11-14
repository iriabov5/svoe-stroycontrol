package ru.rshb.stroycontrol.files;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.Map;

/** Хранит байты в закрытом S3-бакете, не создавая публичных ACL или внешних ссылок. */
@Component
@RequiredArgsConstructor
class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;

    @Value("${storage.bucket:stroycontrol-private}")
    private String bucket;

    private volatile boolean initialized;

    /** Создаёт закрытый бакет при первой загрузке; сетевой сбой не блокирует запуск приложения. */
    private synchronized void initialize() {
        if (initialized) return;
        try {
            client.headBucket(b -> b.bucket(bucket));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) throw e;
            try {
                client.createBucket(b -> b.bucket(bucket));
            } catch (S3Exception race) {
                if (race.statusCode() != 409) throw race;
            }
        }
        initialized = true;
    }

    /** {@inheritDoc} */
    @Override
    public void put(String key, String contentType, String hash, byte[] bytes) {
        initialize();
        client.putObject(
                b ->
                        b.bucket(bucket)
                                .key(key)
                                .contentType(contentType)
                                .metadata(Map.of("sha256", hash)),
                RequestBody.fromBytes(bytes));
    }

    /** {@inheritDoc} */
    @Override
    public byte[] get(String key) {
        return client.getObjectAsBytes(b -> b.bucket(bucket).key(key)).asByteArray();
    }

    /** {@inheritDoc} */
    @Override
    public boolean matches(String key, String hash) {
        try {
            return hash.equals(
                    client.headObject(b -> b.bucket(bucket).key(key)).metadata().get("sha256"));
        } catch (S3Exception e) {
            if (e.statusCode() == 404) return false;
            throw e;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void delete(String key) {
        client.deleteObject(b -> b.bucket(bucket).key(key));
    }
}
