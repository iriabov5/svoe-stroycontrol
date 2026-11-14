ALTER TABLE accounts ADD COLUMN display_name varchar(120);
ALTER TABLE accounts ADD COLUMN profile_version bigint NOT NULL DEFAULT 0 CHECK(profile_version>=0);
ALTER TABLE accounts ADD CONSTRAINT account_display_name_valid CHECK(display_name IS NULL OR (char_length(display_name) BETWEEN 2 AND 120 AND display_name=btrim(display_name)));
CREATE TABLE contractor_organizations(
 id uuid PRIMARY KEY,owner_account_id uuid NOT NULL UNIQUE REFERENCES accounts(id),type varchar(20) NOT NULL CHECK(type IN ('LEGAL_ENTITY','SOLE_TRADER')),
 display_name varchar(160) NOT NULL CHECK(length(btrim(display_name))>0),legal_name varchar(500) NOT NULL CHECK(length(btrim(legal_name))>0),
 inn varchar(12) NOT NULL UNIQUE CHECK((type='LEGAL_ENTITY' AND inn ~ '^[0-9]{10}$') OR (type='SOLE_TRADER' AND inn ~ '^[0-9]{12}$')),
 address varchar(500) NOT NULL CHECK(length(btrim(address))>0),phone varchar(40),version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 creation_key uuid NOT NULL,creation_hash char(64) NOT NULL,created_at timestamptz NOT NULL);
CREATE TABLE contractor_memberships(id uuid PRIMARY KEY,organization_id uuid NOT NULL REFERENCES contractor_organizations(id),account_id uuid NOT NULL REFERENCES accounts(id),
 position varchar(120) NOT NULL CHECK(length(btrim(position))>0),role varchar(10) NOT NULL CHECK(role IN ('OWNER','MEMBER')),state varchar(10) NOT NULL CHECK(state IN ('ACTIVE','REMOVED')),
 version bigint NOT NULL DEFAULT 0 CHECK(version>=0),created_at timestamptz NOT NULL,removed_at timestamptz);
CREATE UNIQUE INDEX memberships_one_active ON contractor_memberships(account_id) WHERE state='ACTIVE';
CREATE UNIQUE INDEX memberships_one_owner ON contractor_memberships(organization_id) WHERE role='OWNER' AND state='ACTIVE';
CREATE TABLE contractor_invitations(id uuid PRIMARY KEY,organization_id uuid NOT NULL REFERENCES contractor_organizations(id),recipient_account_id uuid NOT NULL REFERENCES accounts(id),created_by uuid NOT NULL REFERENCES accounts(id),
 position varchar(120) NOT NULL CHECK(length(btrim(position))>0),state varchar(12) NOT NULL CHECK(state IN ('PENDING','ACCEPTED','DECLINED','CANCELLED')),version bigint NOT NULL DEFAULT 0 CHECK(version>=0),
 idempotency_key uuid NOT NULL,request_hash char(64) NOT NULL,created_at timestamptz NOT NULL,decided_at timestamptz,UNIQUE(created_by,idempotency_key));
CREATE UNIQUE INDEX invitations_one_pending ON contractor_invitations(organization_id,recipient_account_id) WHERE state='PENDING';
ALTER TABLE house_projects ADD COLUMN contractor_organization_id uuid REFERENCES contractor_organizations(id);
ALTER TABLE house_projects ADD COLUMN team_version bigint NOT NULL DEFAULT 0 CHECK(team_version>=0);
CREATE TABLE project_contractor_members(id uuid PRIMARY KEY,project_id uuid NOT NULL REFERENCES house_projects(id),membership_id uuid NOT NULL REFERENCES contractor_memberships(id),
 documents boolean NOT NULL,works boolean NOT NULL,CHECK(documents OR works),UNIQUE(project_id,membership_id));
CREATE INDEX project_membership_idx ON project_contractor_members(membership_id);
ALTER TABLE construction_proposals ADD COLUMN created_by_snapshot jsonb;
ALTER TABLE construction_proposals ADD COLUMN submitted_by_id uuid REFERENCES accounts(id);
ALTER TABLE construction_proposals ADD COLUMN submitted_by_snapshot jsonb;
ALTER TABLE construction_contracts ADD COLUMN created_by_snapshot jsonb;
ALTER TABLE construction_contracts ADD COLUMN submitted_by_id uuid REFERENCES accounts(id);
ALTER TABLE construction_contracts ADD COLUMN submitted_by_snapshot jsonb;
ALTER TABLE proposal_decisions ADD COLUMN actor_snapshot jsonb;
ALTER TABLE contract_decisions ADD COLUMN actor_snapshot jsonb;
ALTER TABLE stage_journal_entries ADD COLUMN author_snapshot jsonb;
ALTER TABLE stage_journal_responses ADD COLUMN author_snapshot jsonb;

CREATE TABLE contractor_invitation_keys(
 id uuid PRIMARY KEY,actor_id uuid NOT NULL REFERENCES accounts(id),idempotency_key uuid NOT NULL,
 invitation_id uuid NOT NULL REFERENCES contractor_invitations(id),request_hash char(64) NOT NULL,
 UNIQUE(actor_id,idempotency_key));
