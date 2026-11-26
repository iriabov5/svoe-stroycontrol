package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Хранит указатели плана; общую блокировку обеспечивает projects. */
interface ProjectFinancingPlanRepository extends JpaRepository<ProjectFinancingPlan, UUID> {}
