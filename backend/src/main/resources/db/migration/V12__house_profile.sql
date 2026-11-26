-- Необязательный паспорт дома. Старые и обычные карточки остаются без изменений.
ALTER TABLE house_projects
    ADD COLUMN area_sqm integer,
    ADD COLUMN floors integer,
    ADD COLUMN wall_material varchar(120),
    ADD COLUMN foundation_type varchar(120),
    ADD COLUMN roof_type varchar(120),
    ADD COLUMN planned_completion varchar(60),
    ADD CONSTRAINT house_projects_area_sqm_valid CHECK (
        area_sqm IS NULL OR area_sqm BETWEEN 20 AND 2000
    ),
    ADD CONSTRAINT house_projects_floors_valid CHECK (
        floors IS NULL OR floors BETWEEN 1 AND 5
    );
