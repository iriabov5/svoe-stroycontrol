package ru.rshb.stroycontrol.proposals;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Хранит внешнюю HTTPS-ссылку на иллюстрацию сметы, подпись и порядок показа.
 *
 * <p>Байты изображения не сохраняются и не загружаются сервером; доступ к смете проверяет сервис.
 */
@Entity
@Table(name = "proposal_images")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProposalImage {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "proposal_id")
    private UUID proposalId;

    @Column(name = "position")
    private int position;

    @Column(name = "url")
    private String url;

    @Column(name = "caption")
    private String caption;
}
