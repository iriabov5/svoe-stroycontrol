ALTER TABLE house_projects ADD COLUMN completion_version bigint NOT NULL DEFAULT 0 CHECK(completion_version>=0);
ALTER TABLE house_projects ADD COLUMN completion_key uuid;
ALTER TABLE house_projects ADD COLUMN completion_hash varchar(64);
ALTER TABLE house_projects ADD COLUMN completed_at timestamptz;
ALTER TABLE house_projects ADD COLUMN completed_by_snapshot jsonb;
ALTER TABLE house_projects ADD CONSTRAINT completion_has_author CHECK((completed_at IS NULL)=(completed_by_snapshot IS NULL));
CREATE TABLE acceptance_submissions (
 id uuid PRIMARY KEY,project_id uuid NOT NULL REFERENCES house_projects(id),stage_id uuid NOT NULL REFERENCES construction_stages(id),cycle_number integer NOT NULL CHECK(cycle_number>0),summary text NOT NULL CHECK(length(btrim(summary))>0 AND char_length(summary)<=5000),
 state varchar(16) NOT NULL CHECK(state IN ('OPEN','ACCEPTED','RETURNED')),version bigint NOT NULL DEFAULT 0 CHECK(version>=0),evidence jsonb NOT NULL,author_snapshot jsonb NOT NULL,created_at timestamptz NOT NULL,
 decision varchar(16) CHECK(decision IN ('ACCEPT','RETURN')),comment text,decided_by jsonb,decided_at timestamptz,
 UNIQUE(stage_id,cycle_number),CHECK(coalesce((state='OPEN' AND decision IS NULL AND decided_at IS NULL AND decided_by IS NULL) OR (state='ACCEPTED' AND decision='ACCEPT' AND decided_at IS NOT NULL AND decided_by IS NOT NULL) OR (state='RETURNED' AND decision='RETURN' AND coalesce(length(btrim(comment)),0)>0 AND decided_at IS NOT NULL AND decided_by IS NOT NULL),false))
);
CREATE UNIQUE INDEX acceptance_one_open_idx ON acceptance_submissions(stage_id) WHERE state='OPEN';
CREATE UNIQUE INDEX acceptance_one_accepted_idx ON acceptance_submissions(stage_id) WHERE state='ACCEPTED';
CREATE TABLE acceptance_commands(id uuid PRIMARY KEY,actor_id uuid NOT NULL REFERENCES accounts(id),project_id uuid NOT NULL REFERENCES house_projects(id),operation varchar(100) NOT NULL,idempotency_key uuid NOT NULL,request_hash varchar(64) NOT NULL,result jsonb NOT NULL,created_at timestamptz NOT NULL,UNIQUE(actor_id,project_id,operation,idempotency_key));
