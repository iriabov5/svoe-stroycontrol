CREATE TABLE project_plot_checks (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES house_projects(id), owner_id uuid NOT NULL REFERENCES accounts(id),
 cadastral_number varchar(50) NOT NULL CHECK(cadastral_number ~ '^[0-9]{2}:[0-9]{2}:[0-9]{6,7}:[0-9]{1,7}$'),
 request_version bigint NOT NULL CHECK(request_version >= 0),
 status varchar(20) NOT NULL CHECK(status IN ('ELIGIBLE','INELIGIBLE','NOT_FOUND')),
 address varchar(300), region varchar(200), land_category varchar(200), permitted_use varchar(200), reason varchar(1000),
 terms_version varchar(50), rate numeric(6,3) CHECK(rate >= 0), checked_at timestamptz NOT NULL,
 is_demo boolean NOT NULL CHECK(is_demo), UNIQUE(project_id,id),
 CHECK ((status = 'ELIGIBLE' AND address IS NOT NULL AND region IS NOT NULL AND land_category IS NOT NULL AND permitted_use IS NOT NULL AND rate IS NOT NULL AND terms_version IS NOT NULL AND reason IS NULL)
     OR (status = 'INELIGIBLE' AND address IS NOT NULL AND region IS NOT NULL AND land_category IS NOT NULL AND permitted_use IS NOT NULL AND reason IS NOT NULL AND rate IS NULL AND terms_version IS NULL)
     OR (status = 'NOT_FOUND' AND address IS NULL AND region IS NULL AND land_category IS NULL AND permitted_use IS NULL AND reason IS NOT NULL AND rate IS NULL AND terms_version IS NULL))
);
CREATE TABLE project_mortgage_calculations (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES house_projects(id), owner_id uuid NOT NULL REFERENCES accounts(id),
 plot_check_id uuid NOT NULL, cost numeric(15,2) NOT NULL CHECK(cost BETWEEN 1000000 AND 15000000 AND mod(cost,100000)=0),
 deposit_percent integer NOT NULL CHECK(deposit_percent BETWEEN 20 AND 80 AND mod(deposit_percent,5)=0),
 deposit_amount numeric(15,2) NOT NULL CHECK(deposit_amount>0), years integer NOT NULL CHECK(years BETWEEN 1 AND 25),
 joint boolean NOT NULL, principal numeric(15,2) NOT NULL CHECK(principal>0), rate numeric(6,3) NOT NULL CHECK(rate>=0),
 credit_limit numeric(15,2) NOT NULL CHECK(credit_limit>0), monthly_payment numeric(15,2) NOT NULL CHECK(monthly_payment>0),
 within_limit boolean NOT NULL, terms_version varchar(50) NOT NULL, calculated_at timestamptz NOT NULL,
 is_demo boolean NOT NULL CHECK(is_demo), UNIQUE(project_id,id),
 FOREIGN KEY(project_id,plot_check_id) REFERENCES project_plot_checks(project_id,id),
 CHECK(principal=cost-deposit_amount), CHECK(deposit_amount=cost*deposit_percent/100),
 CHECK(credit_limit=CASE WHEN joint THEN 12000000 ELSE 6000000 END), CHECK(within_limit=(principal<=credit_limit))
);
CREATE TABLE project_financing_plans (
 project_id uuid PRIMARY KEY REFERENCES house_projects(id), version bigint NOT NULL CHECK(version>=0),
 latest_plot_check_id uuid, latest_calculation_id uuid,
 FOREIGN KEY(project_id,latest_plot_check_id) REFERENCES project_plot_checks(project_id,id),
 FOREIGN KEY(project_id,latest_calculation_id) REFERENCES project_mortgage_calculations(project_id,id)
);
CREATE TABLE financing_planning_commands (
 id uuid PRIMARY KEY, project_id uuid NOT NULL REFERENCES house_projects(id), owner_id uuid NOT NULL REFERENCES accounts(id),
 idempotency_key uuid NOT NULL, operation varchar(20) NOT NULL CHECK(operation IN ('CHECK','CALCULATE')),
 request_hash varchar(64) NOT NULL, result jsonb NOT NULL, UNIQUE(project_id,owner_id,idempotency_key)
);
