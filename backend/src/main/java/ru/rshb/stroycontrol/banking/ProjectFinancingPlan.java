package ru.rshb.stroycontrol.banking;

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

/** Указатели на последние личные результаты; записи сериализуются блокировкой карточки. */
@Entity
@Table(name = "project_financing_plans")
@Getter
@Setter(AccessLevel.PACKAGE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
class ProjectFinancingPlan {
    @Id
    private UUID projectId;
    private long version;
    private UUID latestPlotCheckId;
    private UUID latestCalculationId;
}
