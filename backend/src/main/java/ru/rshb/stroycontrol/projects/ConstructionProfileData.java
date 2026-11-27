package ru.rshb.stroycontrol.projects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Общие технические сведения дома; площадь и срок остаются у существующих владельцев. */
@Entity
@Table(name = "project_construction_profiles")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class ConstructionProfileData {
    @Id
    private UUID projectId;
    private String wallMaterial;
    private String foundationType;
    private String roofType;
}
