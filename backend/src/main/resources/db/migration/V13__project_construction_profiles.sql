-- Дополнительные технические сведения без второй копии заявки и срока плана.
CREATE TABLE project_construction_profiles (
 project_id uuid PRIMARY KEY REFERENCES house_projects(id),
 wall_material varchar(120),
 foundation_type varchar(120),
 roof_type varchar(120),
 CONSTRAINT construction_profile_nonempty CHECK (wall_material IS NOT NULL OR foundation_type IS NOT NULL OR roof_type IS NOT NULL),
 CONSTRAINT construction_profile_wall_valid CHECK (wall_material IS NULL OR length(trim(wall_material))>0),
 CONSTRAINT construction_profile_foundation_valid CHECK (foundation_type IS NULL OR length(trim(foundation_type))>0),
 CONSTRAINT construction_profile_roof_valid CHECK (roof_type IS NULL OR length(trim(roof_type))>0)
);
