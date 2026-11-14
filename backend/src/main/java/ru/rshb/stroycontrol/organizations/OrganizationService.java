package ru.rshb.stroycontrol.organizations;

import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.text;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;
import static ru.rshb.stroycontrol.organizations.OrganizationInput.required;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.accounts.Accounts;
import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.CommandFingerprint;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Управляет реквизитами и личными сотрудниками подрядчика, не выдавая доступ ко всем стройкам.
 *
 * <p>Запись блокирует организацию; вступление сначала блокирует аккаунт. Ключи повторов и
 * ограничения БД не допускают второго действующего членства и повторного приглашения.
 */
@Service
@RequiredArgsConstructor
class OrganizationService implements Organizations {
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_OWNER = "OWNER";
    private static final String FIELD_IDEMPOTENCY_KEY = "idempotencyKey";
    private static final String FIELD_LEGAL_NAME = "legalName";
    private static final String FIELD_PHONE = "phone";
    private static final String FIELD_ADDRESS = "address";
    private static final String FIELD_DISPLAY_NAME = "displayName";
    private static final String FIELD_VERSION = "version";
    private static final String FIELD_POSITION = "position";
    private static final String STATE_PENDING = "PENDING";

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final InvitationKeyRepository invitationKeys;
    private final Accounts accounts;
    private final CommandFingerprint fingerprint;
    private final Clock clock;

    /**
     * Реквизиты и собственное членство без прав на проекты.
     *
     * @param organization реквизиты либо null
     * @param membership действующее членство либо null
     */
    record Mine(OrganizationView organization, AccountView.Membership membership) {}

    /**
     * Результат создания.
     *
     * @param organization реквизиты
     * @param created создано в текущем запросе
     */
    record Created(OrganizationView organization, boolean created) {}

    /**
     * Результат приглашения.
     *
     * @param invitation приглашение
     * @param created создано в текущем запросе
     */
    record Invited(InvitationView invitation, boolean created) {}

    /**
     * Адресованное аккаунту приглашение без секретов входа.
     *
     * @param id приглашение
     * @param organizationId организация
     * @param organizationName название
     * @param recipientAccountId адресат
     * @param recipientName имя адресата
     * @param recipientEmail адрес входа
     * @param position предложенная должность
     * @param state состояние
     * @param version версия решения
     * @param createdAt дата приглашения
     */
    record InvitationView(
            UUID id,
            UUID organizationId,
            String organizationName,
            UUID recipientAccountId,
            String recipientName,
            String recipientEmail,
            String position,
            String state,
            long version,
            Instant createdAt) {}

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public AccountView.Membership membership(UUID accountId) {
        return findMembership(accountId);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private AccountView.Membership findMembership(UUID accountId) {
        return memberships
                .findByAccountIdAndState(accountId, STATE_ACTIVE)
                .map(m ->
                        new AccountView.Membership(
                                m.getId(),
                                m.getOrganizationId(),
                                organization(m.getOrganizationId()).getDisplayName(),
                                m.getPosition(),
                                m.getRole()))
                .orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public OrganizationView profile(UUID id) {
        return organizationView(id);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private OrganizationView organizationView(UUID id) {
        return view(organization(id));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public OrganizationView assignable(UUID ownerAccountId) {
        var account = accounts.profile(ownerAccountId);
        var org =
                organizations
                        .findByOwnerAccountId(ownerAccountId)
                        .orElseThrow(() -> new ApiException(ApiError.CONTRACTOR_PROFILE_REQUIRED));
        var member = findMembership(ownerAccountId);
        if (account.displayName() == null || member == null || !member.role().equals(STATE_OWNER))
            throw new ApiException(ApiError.CONTRACTOR_PROFILE_REQUIRED);
        return view(org);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OrganizationView lock(UUID id) {
        return lockOrganization(id);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private OrganizationView lockOrganization(UUID id) {
        return view(
                organizations
                        .lockById(id)
                        .orElseThrow(() -> new ApiException(ApiError.ORGANIZATION_NOT_FOUND)));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Member member(UUID organizationId, UUID membershipId) {
        return memberships
                .findById(membershipId)
                .filter(m ->
                        m.getOrganizationId().equals(organizationId)
                                && m.getState().equals(STATE_ACTIVE))
                .map(this::memberView)
                .orElse(null);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<Member> members(UUID organizationId) {
        return activeMembers(organizationId);
    }

    /** Выполняет общую операцию внутри транзакции вызывающего сервиса. */
    private List<Member> activeMembers(UUID organizationId) {
        return memberships
                .findByOrganizationIdAndStateOrderByCreatedAtAsc(organizationId, STATE_ACTIVE)
                .stream()
                .map(this::memberView)
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ActorSnapshot snapshot(UUID accountId) {
        var account = accounts.profile(accountId);
        var member = findMembership(accountId);
        return new ActorSnapshot(
                accountId,
                account.displayName(),
                account.role(),
                member == null ? null : member.organizationId(),
                member == null ? null : member.organizationName(),
                member == null ? null : member.position(),
                clock.instant(),
                account.displayName() == null);
    }

    /**
     * @param actor текущий аккаунт
     * @return свой профиль организации; отсутствие обозначается null
     */
    @Transactional(readOnly = true)
    Mine mine(AccountView actor) {
        contractor(actor);
        var member = findMembership(actor.id());
        return new Mine(member == null ? null : organizationView(member.organizationId()), member);
    }

    /**
     * Создаёт организацию и OWNER-членство одной транзакцией после блокировки аккаунта.
     *
     * @param actor создатель
     * @param body реквизиты и стабильный ключ
     * @return созданный профиль либо тот же объект при повторе
     * @throws ApiException при занятом ИНН, неверных полях или активном членстве
     */
    @Transactional
    Created create(AccountView actor, Map<String, Object> body) {
        contractor(actor);
        var account = accounts.lock(actor.id());
        named(account);
        keys(
                body,
                "type",
                FIELD_DISPLAY_NAME,
                FIELD_LEGAL_NAME,
                "inn",
                FIELD_ADDRESS,
                FIELD_PHONE,
                FIELD_IDEMPOTENCY_KEY);
        UUID key = uuid(body.get(FIELD_IDEMPOTENCY_KEY));
        String hash = fingerprint.of("organization:" + actor.id(), body);
        var existing = organizations.findByOwnerAccountId(actor.id());
        if (existing.isPresent()) {
            var old = existing.get();
            if (!old.getCreationKey().equals(key))
                throw new ApiException(ApiError.MEMBERSHIP_CONFLICT);
            if (!old.getCreationHash().equals(hash))
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            return new Created(view(old), false);
        }
        if (findMembership(actor.id()) != null)
            throw new ApiException(ApiError.MEMBERSHIP_CONFLICT);
        String type = required(body, "type", 20);
        String inn = required(body, "inn", 12);
        OrganizationInput.inn(type, inn);
        if (organizations.existsByInn(inn))
            throw new ApiException(ApiError.ORGANIZATION_ALREADY_EXISTS);
        var org =
                ContractorOrganization.builder()
                        .ownerAccountId(actor.id())
                        .type(type)
                        .inn(inn)
                        .displayName(required(body, FIELD_DISPLAY_NAME, 160))
                        .legalName(required(body, FIELD_LEGAL_NAME, 500))
                        .address(required(body, FIELD_ADDRESS, 500))
                        .phone(text(body.get(FIELD_PHONE), 40))
                        .creationKey(key)
                        .creationHash(hash)
                        .build();
        org = organizations.saveAndFlush(org);
        memberships.saveAndFlush(
                ContractorMembership.builder()
                        .organizationId(org.getId())
                        .accountId(actor.id())
                        .position("Руководитель")
                        .role(STATE_OWNER)
                        .state(STATE_ACTIVE)
                        .build());
        return new Created(view(org), true);
    }

    /**
     * Обновляет разрешённые реквизиты организации с проверкой версии руководителем.
     *
     * @param actor руководитель
     * @param id организация
     * @param body версия и редактируемые реквизиты
     * @return сохранённый профиль
     * @throws ApiException при неверном доступе или версии
     */
    @Transactional
    OrganizationView edit(AccountView actor, UUID id, Map<String, Object> body) {
        var org = owner(actor, id);
        keys(body, FIELD_VERSION, FIELD_DISPLAY_NAME, FIELD_LEGAL_NAME, FIELD_ADDRESS, FIELD_PHONE);
        check(org.getVersion(), body.get(FIELD_VERSION));
        org.setDisplayName(required(body, FIELD_DISPLAY_NAME, 160));
        org.setLegalName(required(body, FIELD_LEGAL_NAME, 500));
        org.setAddress(required(body, FIELD_ADDRESS, 500));
        org.setPhone(text(body.get(FIELD_PHONE), 40));
        org.setVersion(org.getVersion() + 1);
        organizations.flush();
        return view(org);
    }

    /**
     * @param actor действующий сотрудник
     * @param id своя организация
     * @return её активные сотрудники
     * @throws ApiException с ORGANIZATION_NOT_FOUND для постороннего
     */
    @Transactional(readOnly = true)
    List<Member> visibleMembers(AccountView actor, UUID id) {
        ownMember(actor, id);
        return activeMembers(id);
    }

    /**
     * Создаёт приглашение зарегистрированному сотруднику или возвращает результат повтора.
     *
     * @param actor руководитель
     * @param id организация
     * @param body адресат, должность и ключ
     * @return приглашение без отправки email
     * @throws ApiException при неверном адресате, членстве или конфликте ключа
     */
    @Transactional
    Invited invite(AccountView actor, UUID id, Map<String, Object> body) {
        owner(actor, id);
        keys(body, "recipientEmail", FIELD_POSITION, FIELD_IDEMPOTENCY_KEY);
        UUID key = uuid(body.get(FIELD_IDEMPOTENCY_KEY));
        String hash = fingerprint.of(id.toString(), body);
        var previous = invitationKeys.findByActorIdAndIdempotencyKey(actor.id(), key);
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(hash))
                throw new ApiException(ApiError.IDEMPOTENCY_CONFLICT);
            return new Invited(
                    invitationView(
                            invitations.findById(previous.get().getInvitationId()).orElseThrow()),
                    false);
        }
        String email = required(body, "recipientEmail", 320);
        var recipient =
                accounts.findContractor(email)
                        .orElseThrow(() -> new ApiException(ApiError.CONTRACTOR_NOT_FOUND));
        if (findMembership(recipient.id()) != null || actor.id().equals(recipient.id()))
            throw new ApiException(ApiError.MEMBERSHIP_CONFLICT);
        String position = required(body, FIELD_POSITION, 120);
        var pending =
                invitations.findByOrganizationIdAndRecipientAccountIdAndState(
                        id, recipient.id(), STATE_PENDING);
        if (pending.isPresent()) {
            rememberInvitation(actor.id(), key, hash, pending.get().getId());
            return new Invited(invitationView(pending.get()), false);
        }
        var invitation =
                ContractorInvitation.builder()
                        .organizationId(id)
                        .recipientAccountId(recipient.id())
                        .createdBy(actor.id())
                        .position(position)
                        .state(STATE_PENDING)
                        .idempotencyKey(key)
                        .requestHash(hash)
                        .build();
        var saved = invitations.saveAndFlush(invitation);
        rememberInvitation(actor.id(), key, hash, saved.getId());
        return new Invited(invitationView(saved), true);
    }

    /**
     * @param actor адресат
     * @return только его ожидающие приглашения
     */
    @Transactional(readOnly = true)
    List<InvitationView> personalInvitations(AccountView actor) {
        return invitations
                .findByRecipientAccountIdAndStateOrderByCreatedAtDesc(actor.id(), STATE_PENDING)
                .stream()
                .map(this::invitationView)
                .toList();
    }

    /**
     * @param actor руководитель
     * @param id организация
     * @return её приглашения с текущими состояниями
     */
    @Transactional(readOnly = true)
    List<InvitationView> organizationInvitations(AccountView actor, UUID id) {
        ownerRead(actor, id);
        return invitations.findByOrganizationIdOrderByCreatedAtDesc(id).stream()
                .map(this::invitationView)
                .toList();
    }

    /**
     * Фиксирует решение по приглашению; принятие сериализуется блокировкой личного аккаунта.
     *
     * @param actor адресат либо руководитель для отмены
     * @param id приглашение
     * @param action accept, decline либо cancel
     * @param body ожидаемая версия
     * @return прежнее либо только что принятое решение
     * @throws ApiException при подмене адресата, конфликте версии или другом членстве
     */
    @Transactional
    InvitationView decide(AccountView actor, UUID id, String action, Map<String, Object> body) {
        keys(body, FIELD_VERSION);
        var initial =
                invitations
                        .address(id)
                        .orElseThrow(() -> new ApiException(ApiError.INVITATION_NOT_FOUND));
        boolean cancel = action.equals("cancel");
        if (!cancel && !initial.getRecipientAccountId().equals(actor.id()))
            throw new ApiException(ApiError.INVITATION_NOT_FOUND);
        if (!cancel) accounts.lock(actor.id());
        if (cancel) owner(actor, initial.getOrganizationId());
        else lockOrganization(initial.getOrganizationId());
        // Первое чтение только определяет адресата/порядок блокировок; перечитать после блокировки.
        var invitation = invitations.findById(id).orElseThrow();
        String target = "DECLINED";
        if (cancel) target = "CANCELLED";
        else if (action.equals("accept")) target = "ACCEPTED";
        if (invitation.getState().equals(target)) return invitationView(invitation);
        if (!invitation.getState().equals(STATE_PENDING))
            throw new ApiException(ApiError.MEMBERSHIP_CONFLICT);
        check(invitation.getVersion(), body.get(FIELD_VERSION));
        if (target.equals("ACCEPTED")) {
            named(accounts.profile(actor.id()));
            if (findMembership(actor.id()) != null)
                throw new ApiException(ApiError.MEMBERSHIP_CONFLICT);
            memberships.saveAndFlush(
                    ContractorMembership.builder()
                            .organizationId(invitation.getOrganizationId())
                            .accountId(actor.id())
                            .position(invitation.getPosition())
                            .role("MEMBER")
                            .state(STATE_ACTIVE)
                            .build());
        }
        invitation.setState(target);
        invitation.setVersion(invitation.getVersion() + 1);
        invitation.setDecidedAt(clock.instant());
        invitations.saveAndFlush(invitation);
        return invitationView(invitation);
    }

    /**
     * Меняет должность сотрудника с проверкой версии членства.
     *
     * @param actor руководитель
     * @param id организация
     * @param membershipId членство
     * @param body версия/должность
     * @return обновлённый участник
     * @throws ApiException при неверном доступе или версии
     */
    @Transactional
    Member editMember(AccountView actor, UUID id, UUID membershipId, Map<String, Object> body) {
        owner(actor, id);
        keys(body, FIELD_VERSION, FIELD_POSITION);
        var m = ownedMembership(id, membershipId);
        check(m.getVersion(), body.get(FIELD_VERSION));
        m.setPosition(required(body, FIELD_POSITION, 120));
        m.setVersion(m.getVersion() + 1);
        memberships.flush();
        return memberView(m);
    }

    /**
     * Исключает сотрудника, сохраняя историю и запрещая последующий доступ к проектам.
     *
     * @param actor руководитель
     * @param id организация
     * @param membershipId членство
     * @param expected версия
     * @throws ApiException при попытке удалить руководителя или неверной версии
     */
    @Transactional
    void remove(AccountView actor, UUID id, UUID membershipId, long expected) {
        owner(actor, id);
        var m = ownedMembership(id, membershipId);
        if (m.getRole().equals(STATE_OWNER)) throw new ApiException(ApiError.FORBIDDEN);
        if (m.getState().equals("REMOVED")) return;
        check(m.getVersion(), expected);
        m.setState("REMOVED");
        m.setVersion(m.getVersion() + 1);
        m.setRemovedAt(clock.instant());
        memberships.flush();
    }

    private ContractorOrganization organization(UUID id) {
        return organizations
                .findById(id)
                .orElseThrow(() -> new ApiException(ApiError.ORGANIZATION_NOT_FOUND));
    }

    private ContractorOrganization owner(AccountView actor, UUID id) {
        lockOrganization(id);
        return ownerRead(actor, id);
    }

    private ContractorOrganization ownerRead(AccountView actor, UUID id) {
        var org = organization(id);
        if (!org.getOwnerAccountId().equals(actor.id())) {
            var m = findMembership(actor.id());
            if (m == null || !m.organizationId().equals(id))
                throw new ApiException(ApiError.ORGANIZATION_NOT_FOUND);
            throw new ApiException(ApiError.FORBIDDEN);
        }
        return org;
    }

    private void ownMember(AccountView actor, UUID id) {
        var m = findMembership(actor.id());
        if (m == null || !m.organizationId().equals(id))
            throw new ApiException(ApiError.ORGANIZATION_NOT_FOUND);
    }

    private ContractorMembership ownedMembership(UUID id, UUID membershipId) {
        return memberships
                .findById(membershipId)
                .filter(m -> m.getOrganizationId().equals(id))
                .orElseThrow(() -> new ApiException(ApiError.ORGANIZATION_NOT_FOUND));
    }

    private void named(AccountView account) {
        if (account.displayName() == null) throw new ApiException(ApiError.PROFILE_REQUIRED);
    }

    private void contractor(AccountView actor) {
        if (actor.role() != AccountRole.CONTRACTOR) throw new ApiException(ApiError.FORBIDDEN);
    }

    private void check(long actual, Object expected) {
        if (actual != version(expected))
            throw new ApiException(ApiError.STALE_ORGANIZATION_VERSION);
    }

    private OrganizationView view(ContractorOrganization o) {
        return new OrganizationView(
                o.getId(),
                o.getOwnerAccountId(),
                o.getType(),
                o.getDisplayName(),
                o.getLegalName(),
                o.getInn(),
                o.getAddress(),
                o.getPhone(),
                o.getVersion());
    }

    private Member memberView(ContractorMembership m) {
        var a = accounts.profile(m.getAccountId());
        return new Member(
                m.getId(),
                m.getOrganizationId(),
                a.id(),
                a.displayName(),
                a.email(),
                m.getPosition(),
                m.getRole(),
                m.getState(),
                m.getVersion());
    }

    private InvitationView invitationView(ContractorInvitation i) {
        var a = accounts.profile(i.getRecipientAccountId());
        return new InvitationView(
                i.getId(),
                i.getOrganizationId(),
                organization(i.getOrganizationId()).getDisplayName(),
                a.id(),
                a.displayName(),
                a.email(),
                i.getPosition(),
                i.getState(),
                i.getVersion(),
                i.getCreatedAt());
    }

    /**
     * Фиксирует ключ и отпечаток с результатом в общей транзакции приглашения.
     *
     * @param actor автор команды
     * @param key ключ повтора
     * @param hash отпечаток тела и организации
     * @param invitation идентификатор нового либо уже открытого приглашения
     */
    private void rememberInvitation(UUID actor, UUID key, String hash, UUID invitation) {
        invitationKeys.saveAndFlush(
                InvitationKey.builder()
                        .actorId(actor)
                        .idempotencyKey(key)
                        .requestHash(hash)
                        .invitationId(invitation)
                        .build());
    }
}
