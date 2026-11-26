-- Локальные иллюстрации разрешены только для поставляемых синтетических демо-сценариев.
ALTER TABLE stage_journal_photos
    DROP CONSTRAINT stage_journal_photos_external_url_check,
    ADD CONSTRAINT stage_journal_photos_external_url_check CHECK (
        external_url ~* '^https://'
        OR external_url IN (
            '/demo/foundation-reinforcement-example.png',
            '/demo/utilities-example.png'
        )
    );
