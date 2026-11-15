package ru.rshb.stroycontrol.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;
import java.time.Duration;

/**
 * Настраивает стандартный S3-клиент для закрытого локального бакета; секреты берутся из окружения.
 */
@Configuration
@EnableScheduling
class StorageConfig {
    /**
     * Создаёт клиент без сетевого обращения при запуске приложения.
     *
     * @param endpoint адрес S3
     * @param accessKey ключ доступа
     * @param secretKey секрет
     * @return клиент с закрываемыми HTTP-ресурсами
     */
    @Bean(destroyMethod = "close")
    S3Client s3Client(
            @Value("${storage.endpoint:http://localhost:19001}") String endpoint,
            @Value("${storage.access-key:local-stroycontrol}") String accessKey,
            @Value("${storage.secret-key:local-demo-storage-password}") String secretKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .httpClientBuilder(
                        UrlConnectionHttpClient.builder()
                                .connectionTimeout(Duration.ofSeconds(3))
                                .socketTimeout(Duration.ofSeconds(15)))
                .build();
    }
}
