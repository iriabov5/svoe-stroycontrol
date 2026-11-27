package ru.rshb.stroycontrol.projects;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Хранит только дополнительные технические характеристики в модуле проектов. */
interface ConstructionProfileRepository extends JpaRepository<ConstructionProfileData, UUID> {}
