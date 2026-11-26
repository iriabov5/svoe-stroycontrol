package ru.rshb.stroycontrol.accounts;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.Input;
import ru.rshb.stroycontrol.common.PreparationInput;

import java.sql.SQLException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Управляет регистрацией и аутентификацией заказчиков и подрядчиков.
 *
 * <p>Email нормализуется перед поиском и сохранением, пароль хранится только в виде хеша.
 * Регистрация фиксируется одной транзакцией; уникальность email дополнительно обеспечивает БД.
 * Проверка неизвестного аккаунта также выполняет проверку хеша, не раскрывая наличие email.
 */
@Service
@RequiredArgsConstructor
class AccountService implements Accounts {
    private static final String FIELD_DISPLAY_NAME = "displayName";

    private final AccountRepository repository;
    private final PasswordEncoder encoder;

    // Вычисляется один раз при первом неизвестном email; дальнейшие попытки проверяют тот же хеш.
    @Getter(value = AccessLevel.PRIVATE, lazy = true)
    private final String dummyHash = encoder.encode("unknown-account-password");

    /**
     * Регистрирует аккаунт с нормализованным email и хешем пароля.
     *
     * <p>Регистрация атомарна. Конкурирующие регистрации одного email разрешает уникальный индекс
     * БД; только его нарушение преобразуется в EMAIL_ALREADY_EXISTS.
     *
     * @param email email аккаунта; нормализуется перед поиском или сохранением
     * @param password исходный пароль, который не сохраняется в открытом виде
     * @param role роль создаваемого аккаунта
     * @return созданный аккаунт без пароля и хеша
     * @throws ApiException при неверных полях или EMAIL_ALREADY_EXISTS для занятого email
     */
    @Transactional
    AccountView registerAccount(String email, String password, AccountRole role) {
        return registerAccount(email, password, role, null);
    }

    /**
     * Регистрирует личный аккаунт с необязательным именем для совместимости старого API.
     *
     * @param email адрес входа
     * @param password пароль для хеширования
     * @param role роль аккаунта
     * @param displayName имя либо null в прежнем клиенте
     * @return профиль без секрета
     * @throws ApiException при неверных полях или занятом email
     */
    @Transactional
    AccountView register(String email, String password, AccountRole role, String displayName) {
        return registerAccount(email, password, role, displayName);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private AccountView registerAccount(
            String email, String password, AccountRole role, String displayName) {
        var normalized = Input.email(email);
        Input.password(password);
        if (role == null) throw new ApiException(ApiError.VALIDATION_ERROR);
        try {
            var account =
                    Account.builder()
                            .email(normalized)
                            .role(role)
                            .passwordHash(Objects.requireNonNull(encoder.encode(password)))
                            .displayName(displayName == null ? null : profileName(displayName))
                            .build();
            return repository.saveAndFlush(account).view();
        } catch (DataIntegrityViolationException exception) {
            // Только уникальный ключ email превращается в ожидаемый конфликт регистрации.
            var cause = exception.getMostSpecificCause();
            if (cause instanceof SQLException sql
                    && "23505".equals(sql.getSQLState())
                    && sql.getMessage().contains("accounts_email_key")) {
                throw new ApiException(ApiError.EMAIL_ALREADY_EXISTS);
            }
            throw exception;
        }
    }

    /**
     * Проверяет пароль и возвращает безопасную модель аккаунта.
     *
     * <p>Даже для неизвестного email выполняется проверка хеша. Пароль и хеш не включаются в
     * результат; неверный пароль и отсутствующий аккаунт дают одинаковую публичную ошибку.
     *
     * @param email email аккаунта; нормализуется перед поиском или сохранением
     * @param password исходный пароль, который не сохраняется в открытом виде
     * @return проверенный аккаунт без пароля и хеша
     * @throws ApiException с INVALID_CREDENTIALS при неверном email или пароле либо
     *     VALIDATION_ERROR при неверном формате
     */
    @Transactional(readOnly = true)
    AccountView authenticate(String email, String password) {
        var normalized = Input.email(email);
        Input.password(password);
        var account = repository.findByEmail(normalized);
        // Даже неизвестный email проходит дорогую проверку хеша.
        var matches =
                encoder.matches(
                        password,
                        account.map(Account::getPasswordHash).orElseGet(this::getDummyHash));
        if (account.isEmpty() || !matches) throw new ApiException(ApiError.INVALID_CREDENTIALS);
        return account.get().view();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<AccountView> findContractor(String email) {
        return repository
                .findByEmail(Input.email(email))
                .filter(account -> account.getRole() == AccountRole.CONTRACTOR)
                .map(Account::view);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AccountView profile(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ApiException(ApiError.AUTH_REQUIRED))
                .view();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public AccountView lock(UUID id) {
        return repository
                .lockById(id)
                .orElseThrow(() -> new ApiException(ApiError.AUTH_REQUIRED))
                .view();
    }

    /**
     * Сохраняет имя своего аккаунта под блокировкой и проверкой версии.
     *
     * @param id аутентифицированный аккаунт
     * @param body имя и ожидаемая версия
     * @return актуальный безопасный профиль
     * @throws ApiException при неверном имени либо устаревшей версии
     */
    @Transactional
    AccountView updateProfile(UUID id, Map<String, Object> body) {
        PreparationInput.keys(body, "version", FIELD_DISPLAY_NAME);
        var account =
                repository.lockById(id).orElseThrow(() -> new ApiException(ApiError.AUTH_REQUIRED));
        if (account.getProfileVersion() != PreparationInput.version(body.get("version")))
            throw new ApiException(ApiError.STALE_PROFILE_VERSION);
        account.setDisplayName(profileName(body.get(FIELD_DISPLAY_NAME)));
        account.setProfileVersion(account.getProfileVersion() + 1);
        repository.flush();
        return account.view();
    }

    /**
     * Проверяет имя без искусственного разбора ФИО.
     *
     * @param raw исходное значение
     * @return имя после удаления крайних пробелов
     * @throws ApiException с ошибкой displayName при пустом или слишком коротком имени
     */
    private String profileName(Object raw) {
        String name =
                PreparationInput.fieldValue(
                        FIELD_DISPLAY_NAME, () -> PreparationInput.text(raw, 120));
        PreparationInput.field(
                name != null && name.codePointCount(0, name.length()) >= 2,
                FIELD_DISPLAY_NAME,
                "Укажите имя длиной от 2 до 120 символов");
        return name;
    }
}
