package ru.rshb.stroycontrol.journal;

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
 * Хранит внешний HTTPS-адрес либо известную локальную демо-иллюстрацию, подпись и позицию фото.
 *
 * <p>Изображение доступно по внешней ссылке либо поставляется frontend как строго разрешённый
 * синтетический пример. Сущность не хранит байты и не гарантирует неизменность оригинала. Права
 * наследуются от публикации и проверяются сервисом журнала.
 */
@Entity
@Table(name = "stage_journal_photos")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class JournalPhoto {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "entry_id")
    private UUID entryId;

    @Column(name = "position")
    private int position;

    @Column(name = "external_url")
    private String externalUrl;

    @Column(name = "caption")
    private String caption;
}
