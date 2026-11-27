import { describe, it, expect } from "vitest";
import {
  addMonths,
  addDays,
  daysLeft,
  generateObligations,
  makeEstimate,
  totals,
  overrun,
  insurancePrice,
  payment,
  mergeGuest,
  blankData,
  applyPolicy,
  estimateCSV,
  validateFile,
  validCadastre,
  maskCadastre,
  type Policy,
} from "../src/domain";
describe("Календарные сроки", () => {
  it("Сохраняет последний допустимый день месяца, включая високосный год", () => {
    expect(addMonths("2024-08-31", 6)).toBe("2025-02-28");
    expect(addMonths("2023-08-31", 6)).toBe("2024-02-29");
    expect(addMonths("2024-02-29", 24)).toBe("2026-02-28");
  });
  it("Считает 180 дней и остаток без UTC-сдвигов", () => {
    expect(addDays("2026-01-01", 180)).toBe("2026-06-30");
    expect(daysLeft("2026-11-25", "2026-11-25")).toBe(0);
    expect(daysLeft("2026-11-24", "2026-11-25")).toBe(-1);
  });
  it("Создаёт 10 подтверждений занятости, 5 страховых сроков и оба срока для ИЖС", () => {
    const o = generateObligations({
      signedAt: "2026-01-31",
      objectType: "construction",
      isVeteran: false,
    });
    expect(o).toHaveLength(17);
    expect(o.filter((x) => x.type === "employment")).toHaveLength(10);
    expect(o.filter((x) => x.type === "insurance")).toHaveLength(5);
    expect(o.find((x) => x.type === "construction_end")?.dueDate).toBe(
      "2028-01-31",
    );
  });
  it("Исключает занятость для СВО и срок строительства для готового дома", () => {
    const o = generateObligations({
      signedAt: "2026-01-01",
      objectType: "ready",
      isVeteran: true,
    });
    expect(o).toHaveLength(6);
    expect(
      o.some((x) => x.type === "employment" || x.type === "construction_end"),
    ).toBe(false);
  });
});
describe("Деньги и файлы", () => {
  it.each([4500000, 6200000, 8500000])(
    "Шаблон сохраняет полный бюджет %i",
    (budget) => {
      expect(totals(makeEstimate(budget))).toEqual({
        planned: budget,
        actual: 0,
      });
    },
  );
  it("Перерасход срабатывает точно на 10%, в том числе для отдельного этапа", () => {
    const s = makeEstimate(0);
    s[0].items[0].plannedAmount = 100;
    s[0].items[0].actualAmount = 109;
    expect(overrun(s)).toBeNull();
    s[0].items[0].actualAmount = 110;
    expect(overrun(s)?.amount).toBe(10);
    s[1].items[0].plannedAmount = 1000;
    expect(overrun(s)?.title).toContain("Фундамент");
  });
  it("Применяет коэффициент материала и независимое покрытие жизни", () => {
    expect(insurancePrice(4000000, "stone", true, "mortgage").total).toBe(
      22000,
    );
    expect(insurancePrice(4000000, "wood", true, "mortgage").total).toBe(26000);
    expect(insurancePrice(4000000, "wood", true, "construction").total).toBe(
      14000,
    );
  });
  it("Считает аннуитет и нулевую ставку", () => {
    expect(payment(4000000, 3, 20)).toBeCloseTo(22183.9, 0);
    expect(payment(120000, 0, 1)).toBe(10000);
  });
  it("Экранирует формулы и кавычки в CSV", () => {
    const s = makeEstimate(0);
    s[0].items[0].name = '=HYPERLINK("x")';
    expect(estimateCSV(s)).toContain('"\'=HYPERLINK(""x"")"');
  });
  it("Отклоняет неподдерживаемые, пустые и слишком большие файлы", () => {
    expect(
      validateFile(new File(["a"], "test.pdf", { type: "application/pdf" })),
    ).toBe("");
    expect(
      validateFile(
        new File(["x"], "test.exe", { type: "application/octet-stream" }),
      ),
    ).toContain("PDF");
    expect(
      validateFile(new File([], "test.pdf", { type: "application/pdf" })),
    ).toContain("пустой");
    expect(
      validateFile(
        new File([new Uint8Array(1600000)], "big.png", { type: "image/png" }),
      ),
    ).toContain("большой");
  });
});
describe("Миграция и интеграция", () => {
  it("Сохраняет старую смету в архиве и переносит гостевой черновик дат", () => {
    const u = blankData(),
      g = blankData();
    u.estimate = makeEstimate(8500000);
    u.estimateName = "Старый проект";
    g.estimate = makeEstimate(4500000);
    g.estimateName = "Новый проект";
    g.mortgageDraft.signedAt = "2025-01-15";
    g.mortgageDraftTouched = true;
    const r = mergeGuest(u, g);
    expect(r.estimateName).toBe("Новый проект");
    expect(r.estimateArchives[0].name).toBe("Старый проект");
    expect(r.mortgageDraft.signedAt).toBe("2025-01-15");
  });
  it("Покупка закрывает только выбранный страховой срок, а недострой не закрывает ипотечную страховку", () => {
    const d = blankData();
    d.obligations = generateObligations({
      signedAt: "2026-01-01",
      objectType: "construction",
      isVeteran: false,
    });
    const target = d.obligations.filter((x) => x.type === "insurance")[1];
    const p: Policy = {
      id: "p1",
      kind: "mortgage",
      premium: 22000,
      insuredAmount: 4000000,
      createdAt: "2026-11-25",
      expiresAt: "2027-11-25",
      obligationId: target.id,
    };
    const r = applyPolicy(d, p);
    expect(r.obligations.filter((x) => x.status === "approved")).toHaveLength(
      1,
    );
    expect(r.obligations.find((x) => x.id === target.id)?.status).toBe(
      "approved",
    );
    expect(
      applyPolicy(d, { ...p, kind: "construction" }).obligations.some(
        (x) => x.status === "approved",
      ),
    ).toBe(false);
  });
  it("Поддерживает номера с кварталом из 6 и 7 цифр и посимвольную маску", () => {
    expect(validCadastre("16:24:070801:412")).toBe(true);
    expect(validCadastre("50:21:0040112:105")).toBe(true);
    let s = "";
    for (const c of "50210040112105") s = maskCadastre(s + c);
    expect(s).toBe("50:21:0040112:105");
  });
});
