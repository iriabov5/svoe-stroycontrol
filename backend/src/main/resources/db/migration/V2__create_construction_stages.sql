CREATE TABLE construction_stages (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES house_projects(id),
    name varchar(120) NOT NULL,
    work_type varchar(16) NOT NULL,
    position integer NOT NULL CHECK (position > 0),
    status varchar(20) NOT NULL,
    planned_start date,
    planned_finish date,
    actual_start date,
    actual_finish date,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT construction_stages_name_present CHECK (char_length(btrim(name)) > 0 AND name = btrim(name)),
    CONSTRAINT construction_stages_work_type_known CHECK (work_type IN ('FOUNDATION', 'WALLS', 'ROOF', 'UTILITIES', 'FINISHING', 'OTHER')),
    CONSTRAINT construction_stages_plan_dates CHECK (planned_start IS NULL OR planned_finish IS NULL OR planned_finish >= planned_start),
    CONSTRAINT construction_stages_actual_dates CHECK (actual_finish IS NULL OR actual_finish >= actual_start),
    CONSTRAINT construction_stages_dates_range CHECK (
        (planned_start IS NULL OR planned_start BETWEEN DATE '0001-01-01' AND DATE '9999-12-31') AND
        (planned_finish IS NULL OR planned_finish BETWEEN DATE '0001-01-01' AND DATE '9999-12-31') AND
        (actual_start IS NULL OR actual_start BETWEEN DATE '0001-01-01' AND DATE '9999-12-31') AND
        (actual_finish IS NULL OR actual_finish BETWEEN DATE '0001-01-01' AND DATE '9999-12-31')
    ),
    CONSTRAINT construction_stages_status_dates CHECK (
        (status = 'PLANNED' AND actual_start IS NULL AND actual_finish IS NULL) OR
        (status = 'IN_PROGRESS' AND actual_start IS NOT NULL AND actual_finish IS NULL) OR
        (status = 'WORK_COMPLETED' AND actual_start IS NOT NULL AND actual_finish IS NOT NULL)
    ),
    -- Перестановка 1↔2 допустима в одной транзакции; дубли запрещены на commit.
    CONSTRAINT construction_stages_project_position_key UNIQUE (project_id, position) DEFERRABLE INITIALLY DEFERRED
);
