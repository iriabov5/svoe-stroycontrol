package ru.rshb.stroycontrol.accounts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Аккаунт заказчика или подрядчика. Email хранится нормализованным: без крайних пробелов и в нижнем
 * регистре. В колонке пароля лежит только хеш.
 */
@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class Account {

    @Id
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 254, unique = true)
    @NonNull
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @NonNull
    private AccountRole role;

    @Column(name = "password_hash", nullable = false, length = 255)
    @Getter(AccessLevel.PACKAGE)
    @NonNull
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "display_name", length = 120)
    @Setter(AccessLevel.PACKAGE)
    private String displayName;

    @Column(name = "profile_version", nullable = false)
    @Setter(AccessLevel.PACKAGE)
    private long profileVersion;

    /**
     * Возвращает безопасную модель аккаунта для сессии и публичных ответов.
     *
     * <p>Пароль и хеш никогда не включаются в результат.
     *
     * @return ID, email и роль без секретов
     */
    AccountView view() {
        return new AccountView(id, email, role, displayName, profileVersion, null);
    }
}
