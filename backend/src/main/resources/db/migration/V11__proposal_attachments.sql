-- Сохраняем прежние URL; загруженные изображения ссылаются на метаданные файлов.
ALTER TABLE proposal_images ALTER COLUMN url DROP NOT NULL;
ALTER TABLE proposal_images ADD COLUMN attachment_id uuid REFERENCES attachments(id);
ALTER TABLE proposal_images ADD CONSTRAINT proposal_image_source_check
    CHECK ((url IS NOT NULL AND attachment_id IS NULL)
        OR (url IS NULL AND attachment_id IS NOT NULL));
CREATE INDEX proposal_image_attachment_idx ON proposal_images(attachment_id)
    WHERE attachment_id IS NOT NULL;

ALTER TABLE attachments DROP CONSTRAINT attachments_purpose_check;
ALTER TABLE attachments DROP CONSTRAINT attachments_check;
ALTER TABLE attachments ADD CONSTRAINT attachments_purpose_check
    CHECK (purpose IN ('JOURNAL', 'BANK', 'PROPOSAL'));
ALTER TABLE attachments ADD CONSTRAINT attachments_purpose_stage_check
    CHECK ((purpose = 'JOURNAL' AND stage_id IS NOT NULL)
        OR (purpose IN ('BANK', 'PROPOSAL') AND stage_id IS NULL));
