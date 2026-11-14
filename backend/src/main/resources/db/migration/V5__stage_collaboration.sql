ALTER TABLE house_projects ADD COLUMN preparation_closed boolean NOT NULL DEFAULT false;
ALTER TABLE construction_proposals DROP CONSTRAINT construction_proposals_state_check;
ALTER TABLE construction_proposals ADD CONSTRAINT construction_proposals_state_check CHECK(state IN ('DRAFT','SUBMITTED','CHANGES_REQUESTED','APPROVED','REJECTED'));
ALTER TABLE proposal_decisions DROP CONSTRAINT proposal_decisions_decision_check;
ALTER TABLE proposal_decisions ADD CONSTRAINT proposal_decisions_decision_check CHECK(decision IN ('APPROVED','CHANGES_REQUESTED','REJECTED'));
ALTER TABLE proposal_decisions ADD CONSTRAINT proposal_rejection_reason CHECK(decision<>'REJECTED' OR coalesce(length(btrim(comment)),0)>0);
CREATE TABLE stage_journal_entries (
 id uuid PRIMARY KEY,project_id uuid NOT NULL REFERENCES house_projects(id),stage_id uuid NOT NULL REFERENCES construction_stages(id),author_id uuid NOT NULL REFERENCES accounts(id),
 author_email varchar(254) NOT NULL,author_role varchar(16) NOT NULL CHECK(author_role='CONTRACTOR'),
 kind varchar(16) NOT NULL CHECK(kind IN ('NOTE','QUESTION','APPROVAL')),status varchar(16) NOT NULL,
 text text NOT NULL CHECK(char_length(text) BETWEEN 1 AND 10000 AND length(btrim(text))>0),source_entry_id uuid REFERENCES stage_journal_entries(id),
 created_at timestamptz NOT NULL,version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 CHECK((kind='NOTE' AND status='PUBLISHED') OR (kind='QUESTION' AND status IN ('PENDING','ANSWERED')) OR (kind='APPROVAL' AND status IN ('PENDING','APPROVED','DECLINED')))
);
CREATE INDEX journal_stage_order_idx ON stage_journal_entries(stage_id,created_at DESC,id DESC);
CREATE INDEX journal_stage_pending_idx ON stage_journal_entries(stage_id) WHERE status='PENDING';
CREATE TABLE stage_journal_photos(id uuid PRIMARY KEY,entry_id uuid NOT NULL REFERENCES stage_journal_entries(id),position integer NOT NULL CHECK(position>0),external_url varchar(2048) NOT NULL CHECK(external_url ~* '^https://'),caption varchar(300) NOT NULL CHECK(length(btrim(caption))>0),UNIQUE(entry_id,position));
CREATE TABLE stage_journal_responses(id uuid PRIMARY KEY,entry_id uuid NOT NULL UNIQUE REFERENCES stage_journal_entries(id),actor_id uuid NOT NULL REFERENCES accounts(id),text varchar(5000),decision varchar(16) CHECK(decision IN ('APPROVED','DECLINED')),created_at timestamptz NOT NULL,CHECK(text IS NULL OR length(btrim(text))>0),CHECK(coalesce((decision IS NULL AND text IS NOT NULL) OR decision='APPROVED' OR (decision='DECLINED' AND text IS NOT NULL),false)));
CREATE TABLE journal_commands(id uuid PRIMARY KEY,actor_id uuid NOT NULL REFERENCES accounts(id),project_id uuid NOT NULL REFERENCES house_projects(id),operation varchar(16) NOT NULL,idempotency_key uuid NOT NULL,request_hash char(64) NOT NULL,entry_id uuid NOT NULL REFERENCES stage_journal_entries(id),result_json jsonb NOT NULL,created_at timestamptz NOT NULL,UNIQUE(actor_id,project_id,operation,idempotency_key));
CREATE TABLE project_request_reissues(id uuid PRIMARY KEY,source_project_id uuid NOT NULL UNIQUE REFERENCES house_projects(id),target_project_id uuid NOT NULL UNIQUE REFERENCES house_projects(id),actor_id uuid NOT NULL REFERENCES accounts(id),target_contractor_id uuid NOT NULL REFERENCES accounts(id),idempotency_key uuid NOT NULL,request_hash char(64) NOT NULL,created_at timestamptz NOT NULL,UNIQUE(actor_id,idempotency_key),CHECK(source_project_id<>target_project_id));
