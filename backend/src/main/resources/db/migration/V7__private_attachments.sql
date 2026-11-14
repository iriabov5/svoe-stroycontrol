CREATE TABLE attachments (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES house_projects(id),stage_id uuid REFERENCES construction_stages(id),actor_id uuid NOT NULL REFERENCES accounts(id),
 idempotency_key uuid NOT NULL,purpose varchar(16) NOT NULL CHECK(purpose IN ('JOURNAL','BANK')), request_hash varchar(64) NOT NULL,object_key varchar(250) NOT NULL UNIQUE,
 name varchar(180) NOT NULL,content_type varchar(80) NOT NULL,size bigint NOT NULL CHECK(size>0 AND size<=20971520),sha256 varchar(64) NOT NULL,caption varchar(300) NOT NULL,
 state varchar(16) NOT NULL CHECK(state IN ('UPLOADING','READY','FAILED','DELETING','DELETED')),created_at timestamptz NOT NULL,retained boolean NOT NULL DEFAULT false,
 UNIQUE(actor_id,project_id,idempotency_key),CHECK((purpose='JOURNAL' AND stage_id IS NOT NULL) OR (purpose='BANK' AND stage_id IS NULL))
);
CREATE INDEX attachment_cleanup_idx ON attachments(created_at) WHERE retained=false AND state<>'DELETED';
ALTER TABLE stage_journal_entries ADD COLUMN attachment_ids jsonb NOT NULL DEFAULT '[]';
