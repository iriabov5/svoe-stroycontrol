ALTER TABLE attachments ADD COLUMN upload_started_at timestamptz;
UPDATE attachments SET upload_started_at=created_at;
ALTER TABLE attachments ALTER COLUMN upload_started_at SET NOT NULL;
DROP INDEX attachment_cleanup_idx;
CREATE INDEX attachment_cleanup_idx ON attachments(upload_started_at) WHERE retained=false AND state<>'DELETED';
