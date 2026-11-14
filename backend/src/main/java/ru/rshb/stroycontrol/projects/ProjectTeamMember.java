package ru.rshb.stroycontrol.projects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Назначает конкретное действующее членство в команду одной стройки с независимыми доступами. */
@Entity
@Table(name = "project_contractor_members")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProjectTeamMember {
    @Id
    @Column(name = "id")
    @Builder.Default
    private UUID id = UUID.randomUUID();

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "membership_id")
    private UUID membershipId;

    @Column(name = "documents")
    private boolean documents;

    @Column(name = "works")
    private boolean works;
}
