package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountRole;

import java.util.List;
import java.util.UUID;

/** Записывает характеристики только собственных синтетических объектов явного dev-запуска. */
@Service
@Profile("construction-demo")
@RequiredArgsConstructor
class ConstructionProfileSeed {
    private final ProjectRepository projects;
    private final ConstructionProfileRepository profiles;
    private final ProjectAuthorization authorization;

    /**
     * Атомарно заполняет только набор с совпадающими владельцем и уникальным маркером.
     *
     * @param ids только что созданные seed-проекты
     * @param email синтетический владелец
     * @param marker маркер из названия набора
     * @throws IllegalArgumentException если хотя бы одна карточка не принадлежит набору
     */
    @Transactional
    public void seed(List<UUID> ids, String email, String marker) {
        if (ids.isEmpty() || !marker.matches("[a-z0-9-]{8,64}") || !email.endsWith("@example.test"))
            throw new IllegalArgumentException("Проверьте синтетический набор");
        var all = projects.findAllById(ids);
        if (all.size() != ids.size())
            throw new IllegalArgumentException("Карточки набора отсутствуют");
        for (var p : all) {
            var owner = authorization.accounts.profile(p.getCustomerId());
            if (owner.role() != AccountRole.CUSTOMER
                    || !owner.email().equals(email)
                    || !p.getName().startsWith("Демо · ")
                    || !p.getName().endsWith(marker))
                throw new IllegalArgumentException("Карточка не принадлежит синтетическому набору");
        }
        for (var p : all)
            profiles.save(
                    ConstructionProfileData.builder()
                            .projectId(p.getId())
                            .wallMaterial("Каркас с утеплением · демопример")
                            .foundationType("Монолитное основание · демопример")
                            .roofType("Металлическая кровля · демопример")
                            .build());
        profiles.flush();
    }
}
