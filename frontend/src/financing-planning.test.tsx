import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { FinancingPlanning, previewPayment } from "./FinancingPlanning";
import { ApiError, request } from "./api/client";
import type { components } from "./api/schema";
vi.mock("./api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/client")>()),
  request: vi.fn(),
}));
type Plan = components["schemas"]["FinancingPlanningView"];
const empty: Plan = {
  version: 0,
  editable: true,
  plotCheck: null,
  calculation: null,
  plotCheckCurrent: false,
  calculationCurrent: false,
};
const checked: Plan = {
  ...empty,
  version: 1,
  plotCheckCurrent: true,
  plotCheck: {
    id: "check",
    cadastralNumber: "16:24:070801:412",
    status: "ELIGIBLE",
    address: "Тестовое село Сокуры",
    region: "Татарстан",
    landCategory: "Земли населённых пунктов",
    permittedUse: "ИЖС",
    reason: null,
    rate: 3,
    termsVersion: "pr1-demo-v1",
    checkedAt: "2026-11-26T12:00:00Z",
    isDemo: true,
  },
};
const saved: Plan = {
  ...checked,
  version: 2,
  calculationCurrent: true,
  calculation: {
    id: "calculation",
    plotCheckId: "check",
    cost: 5000000,
    depositPercent: 20,
    depositAmount: 1000000,
    years: 20,
    joint: false,
    principal: 4000000,
    rate: 3,
    creditLimit: 6000000,
    monthlyPayment: 22183.9,
    withinLimit: true,
    termsVersion: "pr1-demo-v1",
    calculatedAt: "2026-11-26T12:01:00Z",
    isDemo: true,
  },
};
const props = () => ({
  projectId: "house",
  requestVersion: 1,
  cadastralNumber: "16:24:070801:412",
  dirty: false,
  editable: true,
  onExample: vi.fn(),
  onSaveDraft: vi.fn().mockResolvedValue(true),
  onFieldErrors: vi.fn(),
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
beforeEach(() => vi.mocked(request).mockResolvedValue(empty));

describe("Участок и предварительная ипотека", () => {
  it("проверяет сохранённую заявку и показывает серверный результат", async () => {
    const user = userEvent.setup();
    vi.mocked(request)
      .mockResolvedValueOnce(empty)
      .mockResolvedValueOnce(checked);
    render(<FinancingPlanning {...props()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Проверить участок",
      }),
    );
    expect(
      await screen.findByRole("heading", { name: "Подходит по демопроверке" }),
    ).toBeTruthy();
    expect(request).toHaveBeenLastCalledWith(
      "/projects/house/financing-planning/plot-checks",
      "POST",
      {
        requestVersion: 1,
        planningVersion: 0,
        idempotencyKey: expect.any(String),
      },
    );
    expect(
      screen.getByRole("heading", { name: "Рассчитайте платёж" }),
    ).toBeTruthy();
  });
  it("при несохранённой форме предлагает явно сохранить черновик", async () => {
    const user = userEvent.setup(),
      p = props();
    render(<FinancingPlanning {...p} dirty />);
    const button = await screen.findByRole("button", {
      name: "Проверить участок",
    });
    expect((button as HTMLButtonElement).disabled).toBe(true);
    await user.click(
      screen.getByRole("button", { name: "Сохранить черновик для проверки" }),
    );
    expect(p.onSaveDraft).toHaveBeenCalledOnce();
    expect(request).toHaveBeenCalledTimes(1);
    await user.click(
      screen.getByRole("button", { name: "Приграничье · 0,1%" }),
    );
    expect(p.onExample).toHaveBeenCalledWith("31:15:1203001:88");
  });
  it("ошибка формата связана с существующим полем заявки и не вызывает POST", async () => {
    const user = userEvent.setup(),
      p = props();
    render(<FinancingPlanning {...p} cadastralNumber="bad" />);
    await user.click(
      await screen.findByRole("button", {
        name: "Проверить участок",
      }),
    );
    expect(p.onFieldErrors).toHaveBeenCalledWith({
      cadastralNumber: expect.stringContaining("Введите номер"),
    });
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("после технического сбоя повторяет сохранение расчёта с тем же ключом и параметрами", async () => {
    const user = userEvent.setup();
    vi.mocked(request)
      .mockResolvedValueOnce(checked)
      .mockRejectedValueOnce(
        new ApiError(503, "BANK_DEMO_UNAVAILABLE", "Нет связи"),
      )
      .mockResolvedValueOnce(saved);
    render(<FinancingPlanning {...props()} />);
    await user.click(
      await screen.findByRole("button", { name: "Сохранить расчёт" }),
    );
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Нет связи",
    );
    await user.click(
      screen.getByRole("button", { name: "Повторить действие" }),
    );
    expect(
      await screen.findByText("Предварительный расчёт сохранён"),
    ).toBeTruthy();
    const calls = vi.mocked(request).mock.calls;
    expect(calls[2]).toEqual(calls[1]);
    expect(calls[2][0]).toContain("/calculations");
  });
  it("неизвестный и отрицательный участки не открывают калькулятор", async () => {
    vi.mocked(request).mockResolvedValue({
      ...checked,
      plotCheck: {
        ...checked.plotCheck!,
        status: "NOT_FOUND",
        address: null,
        landCategory: null,
        permittedUse: null,
        rate: null,
        reason: "Участок отсутствует в демобазе",
      },
    });
    render(<FinancingPlanning {...props()} />);
    await screen.findByRole("heading", {
      name: "Участок отсутствует в демобазе",
    });
    expect(
      screen.queryByRole("button", { name: "Сохранить расчёт" }),
    ).toBeNull();
  });
  it("показывает сохранённый расчёт и предупреждает об устаревшем основании", async () => {
    vi.mocked(request).mockResolvedValue({
      ...saved,
      plotCheckCurrent: false,
      calculationCurrent: false,
    });
    render(
      <FinancingPlanning {...props()} cadastralNumber="31:15:1203001:88" />,
    );
    await screen.findByRole("heading", {
      name: "Сохранённый предварительный расчёт",
    });
    expect(screen.getByText(/Расчёт сохранён ранее/)).toBeTruthy();
    expect(
      screen.queryByRole("button", { name: "Сохранить расчёт" }),
    ).toBeNull();
  });
  it("после отправки заявки сохраняет сведения только для чтения", async () => {
    vi.mocked(request).mockResolvedValue({ ...saved, editable: false });
    render(<FinancingPlanning {...props()} editable={false} />);
    await screen.findByRole("heading", {
      name: "Сохранённый предварительный расчёт",
    });
    expect(
      screen.queryByRole("button", { name: "Проверить участок" }),
    ).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Сохранить расчёт" }),
    ).toBeNull();
  });
  it("поздний ответ прежней карточки не заменяет новую карточку", async () => {
    let resolve!: (value: Plan) => void;
    vi.mocked(request)
      .mockImplementationOnce(
        () =>
          new Promise<Plan>((r) => {
            resolve = r;
          }),
      )
      .mockResolvedValueOnce(empty);
    const p = props();
    const { rerender } = render(<FinancingPlanning key="house" {...p} />);
    rerender(<FinancingPlanning key="other" {...p} projectId="other" />);
    await screen.findByRole("button", {
      name: "Проверить участок",
    });
    resolve(saved);
    await waitFor(() =>
      expect(
        screen.queryByRole("heading", {
          name: "Сохранённый предварительный расчёт",
        }),
      ).toBeNull(),
    );
  });
  it("предварительный просмотр совпадает с контрольным серверным примером", () => {
    expect(previewPayment(4000000, 3, 20)).toBeCloseTo(22183.903914, 5);
    expect(previewPayment(1200, 0, 1)).toBe(100);
  });
  it("после ошибки загрузки повторно получает сохранённый план", async () => {
    const user = userEvent.setup();
    vi.mocked(request)
      .mockRejectedValueOnce(new Error("Нет соединения"))
      .mockResolvedValueOnce(saved);
    render(<FinancingPlanning {...props()} />);
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Нет соединения",
    );
    await user.click(
      screen.getByRole("button", { name: "Повторить действие" }),
    );
    await screen.findByRole("heading", {
      name: "Сохранённый предварительный расчёт",
    });
    expect(screen.queryByRole("alert")).toBeNull();
  });
  it("после конфликта обновляет версию и повторяет расчёт с новым ключом", async () => {
    const user = userEvent.setup();
    vi.mocked(request)
      .mockResolvedValueOnce(checked)
      .mockRejectedValueOnce(
        new ApiError(409, "STALE_FINANCING_PLAN", "План изменился"),
      )
      .mockResolvedValueOnce({ ...checked, version: 3 })
      .mockResolvedValueOnce({ ...saved, version: 4 });
    render(<FinancingPlanning {...props()} />);
    await user.click(
      await screen.findByRole("button", { name: "Сохранить расчёт" }),
    );
    await screen.findByText("План изменился");
    await waitFor(() => expect(request).toHaveBeenCalledTimes(3));
    await user.click(screen.getByRole("button", { name: "Сохранить расчёт" }));
    await screen.findByText("Предварительный расчёт сохранён");
    const calls = vi.mocked(request).mock.calls;
    expect(calls[3][2]).toMatchObject({ planningVersion: 3 });
    expect((calls[3][2] as { idempotencyKey: string }).idempotencyKey).not.toBe(
      (calls[1][2] as { idempotencyKey: string }).idempotencyKey,
    );
  });
  it("показывает ошибку поля от сервера и сохраняет введённую стоимость", async () => {
    const user = userEvent.setup(),
      p = props();
    vi.mocked(request)
      .mockResolvedValueOnce(checked)
      .mockRejectedValueOnce(
        new ApiError(400, "VALIDATION_ERROR", "Проверьте стоимость", {
          cost: "Уточните стоимость",
        }),
      );
    render(<FinancingPlanning {...p} />);
    const cost = await screen.findByLabelText("Стоимость дома и участка, ₽");
    fireEvent.change(cost, { target: { value: "6000000" } });
    await user.click(screen.getByRole("button", { name: "Сохранить расчёт" }));
    await screen.findByText("Уточните стоимость");
    expect(cost.getAttribute("aria-invalid")).toBe("true");
    expect((cost as HTMLInputElement).value).toBe("6000000");
    expect(p.onFieldErrors).toHaveBeenCalledWith({
      cost: "Уточните стоимость",
    });
  });
  it("показывает ошибки диапазонов до запроса и управляет лимитом совместного оформления", async () => {
    vi.mocked(request).mockResolvedValue(checked);
    render(<FinancingPlanning {...props()} />);
    const cost = await screen.findByLabelText("Стоимость дома и участка, ₽");
    const deposit = screen.getByLabelText("Первоначальный взнос, %");
    const years = screen.getByLabelText("Срок кредита, лет");
    fireEvent.change(cost, { target: { value: "500000" } });
    fireEvent.change(deposit, { target: { value: "10" } });
    fireEvent.change(years, { target: { value: "0" } });
    for (const field of [cost, deposit, years])
      expect(field.getAttribute("aria-invalid")).toBe("true");
    expect(
      (
        screen.getByRole("button", {
          name: "Сохранить расчёт",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    fireEvent.change(cost, { target: { value: "10000000" } });
    fireEvent.change(deposit, { target: { value: "20" } });
    fireEvent.change(years, { target: { value: "20" } });
    await screen.findByText(/превышает демонстрационный лимит на/);
    fireEvent.click(
      screen.getByRole("checkbox", { name: /Совместное оформление/ }),
    );
    expect(
      screen.queryByText(/превышает демонстрационный лимит на/),
    ).toBeNull();
    expect(request).toHaveBeenCalledTimes(1);
  });
  it("отрицательный результат объясняет причину и не открывает калькулятор", async () => {
    vi.mocked(request).mockResolvedValue({
      ...checked,
      plotCheck: {
        ...checked.plotCheck!,
        status: "INELIGIBLE",
        rate: null,
        reason: "Синтетический регион-исключение",
      },
    });
    render(<FinancingPlanning {...props()} />);
    await screen.findByRole("heading", { name: "Не подходит по демопроверке" });
    expect(screen.getByText(/Синтетический регион-исключение/)).toBeTruthy();
    expect(
      screen.queryByRole("button", { name: "Сохранить расчёт" }),
    ).toBeNull();
  });
  it("ползунки меняют поля и сохраняют выбранные параметры расчёта", async () => {
    const user = userEvent.setup();
    vi.mocked(request)
      .mockResolvedValueOnce(checked)
      .mockResolvedValueOnce(saved);
    render(<FinancingPlanning {...props()} />);
    await screen.findByRole("heading", { name: "Рассчитайте платёж" });
    fireEvent.change(
      screen.getByLabelText("Стоимость дома и участка — ползунок"),
      { target: { value: "6000000" } },
    );
    fireEvent.change(screen.getByLabelText("Первоначальный взнос — ползунок"), {
      target: { value: "30" },
    });
    fireEvent.change(screen.getByLabelText("Срок кредита — ползунок"), {
      target: { value: "15" },
    });
    expect(
      (screen.getByLabelText("Стоимость дома и участка, ₽") as HTMLInputElement)
        .value,
    ).toBe("6000000");
    expect(
      (screen.getByLabelText("Первоначальный взнос, %") as HTMLInputElement)
        .value,
    ).toBe("30");
    expect(
      (screen.getByLabelText("Срок кредита, лет") as HTMLInputElement).value,
    ).toBe("15");
    await user.click(screen.getByRole("button", { name: "Сохранить расчёт" }));
    await screen.findByText("Предварительный расчёт сохранён");
    expect(vi.mocked(request).mock.calls[1][2]).toMatchObject({
      cost: 6000000,
      depositPercent: 30,
      years: 15,
    });
  });
  it("дробные параметры и превышение верхних границ не допускают сохранение", async () => {
    vi.mocked(request).mockResolvedValue(checked);
    render(<FinancingPlanning {...props()} />);
    const cost = await screen.findByLabelText("Стоимость дома и участка, ₽");
    const deposit = screen.getByLabelText("Первоначальный взнос, %");
    const years = screen.getByLabelText("Срок кредита, лет");
    for (const [field, value] of [
      [cost, "15100000"],
      [deposit, "80.5"],
      [years, "26"],
    ] as const) {
      fireEvent.change(field, { target: { value } });
      expect(field.getAttribute("aria-invalid")).toBe("true");
    }
    expect(
      (
        screen.getByRole("button", {
          name: "Сохранить расчёт",
        }) as HTMLButtonElement
      ).disabled,
    ).toBe(true);
    expect(request).toHaveBeenCalledTimes(1);
  });
});
