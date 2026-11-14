ALTER TABLE house_projects ADD COLUMN process_mode varchar(30) NOT NULL DEFAULT 'LEGACY' CHECK(process_mode IN ('LEGACY','PROPOSAL_REQUIRED'));
ALTER TABLE house_projects ALTER COLUMN process_mode SET DEFAULT 'PROPOSAL_REQUIRED';
ALTER TABLE house_projects ADD COLUMN request_submitted boolean NOT NULL DEFAULT false;
ALTER TABLE house_projects ADD COLUMN approved_proposal_id uuid;
ALTER TABLE house_projects ADD COLUMN approved_contract_id uuid;
CREATE TABLE construction_requests (
 id uuid PRIMARY KEY, project_id uuid NOT NULL UNIQUE REFERENCES house_projects(id), location varchar(300) NOT NULL,
 state varchar(20) NOT NULL CHECK(state IN ('DRAFT','SUBMITTED')),version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 description text,technology varchar(2000),technology_recommendation boolean NOT NULL DEFAULT false,
 materials varchar(2000),materials_recommendation boolean NOT NULL DEFAULT false,
 length_m numeric(8,2) CHECK(length_m>0),width_m numeric(8,2) CHECK(width_m>0),area_m2 numeric(10,2) CHECK(area_m2>0),floors integer CHECK(floors BETWEEN 1 AND 10),
 completion_scope varchar(1000),desired_budget numeric(15,2) CHECK(desired_budget>0),cadastral_number varchar(50),
 desired_start date,desired_finish date,financing_source varchar(40) CHECK(financing_source IN ('OWN_FUNDS','RSHB_RURAL_MORTGAGE')),
 mortgage_snapshot_id uuid,submitted_at timestamptz,CHECK(desired_finish>=desired_start),CHECK(financing_source='RSHB_RURAL_MORTGAGE' OR mortgage_snapshot_id IS NULL)
);
CREATE TABLE construction_proposals (
 id uuid PRIMARY KEY,project_id uuid NOT NULL REFERENCES house_projects(id),request_id uuid NOT NULL REFERENCES construction_requests(id),revision_no integer NOT NULL CHECK(revision_no>0),
 state varchar(30) NOT NULL CHECK(state IN ('DRAFT','SUBMITTED','CHANGES_REQUESTED','APPROVED')),version bigint NOT NULL DEFAULT 0,
 estimate_text text NOT NULL DEFAULT '',terms_text text,total_price numeric(15,2) CHECK(total_price>0),created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,submitted_at timestamptz,
 UNIQUE(project_id,revision_no)
);
CREATE UNIQUE INDEX proposals_open_idx ON construction_proposals(project_id) WHERE state IN ('DRAFT','SUBMITTED');
CREATE UNIQUE INDEX proposals_approved_idx ON construction_proposals(project_id) WHERE state='APPROVED';
CREATE TABLE proposal_images (id uuid PRIMARY KEY,proposal_id uuid NOT NULL REFERENCES construction_proposals(id),position integer NOT NULL CHECK(position>0),url varchar(2048) NOT NULL,caption varchar(300) NOT NULL,UNIQUE(proposal_id,position));
CREATE TABLE proposal_stage_plans (id uuid PRIMARY KEY,proposal_id uuid NOT NULL REFERENCES construction_proposals(id),position integer NOT NULL CHECK(position>0),name varchar(120) NOT NULL,work_type varchar(16) NOT NULL CHECK(work_type IN ('FOUNDATION','WALLS','ROOF','UTILITIES','FINISHING','OTHER')),expected_result varchar(2000) NOT NULL,planned_start date,planned_finish date,CHECK(planned_finish>=planned_start),UNIQUE(proposal_id,position));
CREATE TABLE proposal_decisions (id uuid PRIMARY KEY,proposal_id uuid NOT NULL UNIQUE REFERENCES construction_proposals(id),actor_id uuid NOT NULL REFERENCES accounts(id),decision varchar(30) NOT NULL CHECK(decision IN ('APPROVED','CHANGES_REQUESTED')),comment text,decided_at timestamptz NOT NULL,CHECK(decision<>'CHANGES_REQUESTED' OR length(btrim(comment))>0));
CREATE TABLE construction_contracts (id uuid PRIMARY KEY,project_id uuid NOT NULL REFERENCES house_projects(id),proposal_id uuid NOT NULL REFERENCES construction_proposals(id),revision_no integer NOT NULL CHECK(revision_no>0),state varchar(30) NOT NULL CHECK(state IN ('DRAFT','SUBMITTED','CHANGES_REQUESTED','APPROVED')),version bigint NOT NULL DEFAULT 0,contract_text text NOT NULL DEFAULT '',created_by uuid NOT NULL REFERENCES accounts(id),created_at timestamptz NOT NULL,submitted_at timestamptz,UNIQUE(project_id,revision_no));
CREATE UNIQUE INDEX contracts_open_idx ON construction_contracts(project_id) WHERE state IN ('DRAFT','SUBMITTED');
CREATE UNIQUE INDEX contracts_approved_idx ON construction_contracts(project_id) WHERE state='APPROVED';
CREATE TABLE contract_decisions (id uuid PRIMARY KEY,contract_id uuid NOT NULL UNIQUE REFERENCES construction_contracts(id),actor_id uuid NOT NULL REFERENCES accounts(id),decision varchar(30) NOT NULL CHECK(decision IN ('APPROVED','CHANGES_REQUESTED')),comment text,decided_at timestamptz NOT NULL,CHECK(decision<>'CHANGES_REQUESTED' OR length(btrim(comment))>0));
ALTER TABLE house_projects ADD FOREIGN KEY(approved_proposal_id) REFERENCES construction_proposals(id);
ALTER TABLE house_projects ADD FOREIGN KEY(approved_contract_id) REFERENCES construction_contracts(id);
ALTER TABLE construction_stages ADD COLUMN source_proposal_stage_id uuid UNIQUE REFERENCES proposal_stage_plans(id);
ALTER TABLE construction_stages ADD COLUMN expected_result varchar(2000);
CREATE TABLE mortgage_approval_snapshots(id uuid PRIMARY KEY,project_id uuid NOT NULL REFERENCES house_projects(id),owner_id uuid NOT NULL REFERENCES accounts(id),program varchar(100) NOT NULL,amount numeric(15,2) NOT NULL CHECK(amount>0),term_months integer NOT NULL CHECK(term_months>0),rate numeric(6,2) NOT NULL CHECK(rate>=0),decision_date date NOT NULL,valid_until date NOT NULL CHECK(valid_until>=decision_date),fetched_at timestamptz NOT NULL,is_demo boolean NOT NULL DEFAULT true CHECK(is_demo));
CREATE INDEX mortgage_project_idx ON mortgage_approval_snapshots(project_id);
ALTER TABLE construction_requests ADD FOREIGN KEY(mortgage_snapshot_id) REFERENCES mortgage_approval_snapshots(id);
CREATE TABLE mortgage_import_requests(id uuid PRIMARY KEY,owner_id uuid NOT NULL REFERENCES accounts(id),project_id uuid NOT NULL REFERENCES house_projects(id),idempotency_key uuid NOT NULL,request_version bigint NOT NULL,result_status varchar(30) NOT NULL,snapshot_id uuid REFERENCES mortgage_approval_snapshots(id),UNIQUE(owner_id,project_id,idempotency_key));
