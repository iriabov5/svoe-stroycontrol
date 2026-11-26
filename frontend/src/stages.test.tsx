import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { StageDetail, StageForm, StageList, StageTiming } from "./Stages";
import { api, ApiError, type Stage } from "./api/client";

vi.mock("./api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/client")>()),
  api: {
    listStages: vi.fn(),
    listControlGates: vi.fn(),
    getStage: vi.fn(),
    get: vi.fn(),
    createStage: vi.fn(),
    updateStage: vi.fn(),
    startStage: vi.fn(),
    completeStage: vi.fn(),
    reorderStages: vi.fn(),
  },
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const owner = {
  id: "owner",
  email: "owner@example.test",
  role: "CUSTOMER" as const,
};
const project = {
  id: "project",
  name: "Дом",
  address: "Адрес",
  customerId: "owner",
  contractorId: "builder",
  createdAt: "2026-11-13T00:00:00Z",
};
const stage: Stage = {
  id: "stage",
  projectId: "project",
  version: 2,
  position: 1,
  name: "Фундамент",
  workType: "FOUNDATION",
  status: "PLANNED",
  plannedStart: null,
  plannedFinish: null,
  actualStart: null,
  actualFinish: null,
  startOverdueDays: 0,
  finishOverdueDays: 0,
  startLateDays: 0,
  finishLateDays: 0,
};

it("форма связывает ошибку диапазона с полем завершения и не отправляет запрос", async () => {
  const user = userEvent.setup();
  render(
    <StageForm projectId="project" onSaved={vi.fn()} onCancel={vi.fn()} />,
  );
  await user.type(screen.getByLabelText("Название этапа"), "Фундамент");
  await user.type(screen.getByLabelText("Плановое начало"), "2026-11-14");
  await user.type(screen.getByLabelText("Плановое завершение"), "2026-11-13");
  await user.click(screen.getByRole("button", { name: "Добавить этап" }));
  expect(
    screen.getByLabelText("Плановое завершение").getAttribute("aria-invalid"),
  ).toBe("true");
  expect(api.createStage).not.toHaveBeenCalled();
});

it("конфликт сохраняет введённый план и предлагает явную отмену правок при обновлении", async () => {
  vi.mocked(api.updateStage).mockRejectedValue(
    new ApiError(409, "STALE_STAGE_VERSION", "Этапы уже изменились"),
  );
  const reload = vi.fn();
  const user = userEvent.setup();
  render(
    <StageForm
      projectId="project"
      stage={stage}
      onSaved={vi.fn()}
      onCancel={vi.fn()}
      onReload={reload}
    />,
  );
  expect(document.activeElement).toBe(screen.getByLabelText("Название этапа"));
  await user.clear(screen.getByLabelText("Название этапа"));
  await user.type(screen.getByLabelText("Название этапа"), "Мои правки");
  await user.click(screen.getByRole("button", { name: "Сохранить план" }));
  await screen.findByRole("alert");
  expect(
    (screen.getByLabelText("Название этапа") as HTMLInputElement).value,
  ).toBe("Мои правки");
  expect(
    (
      screen.getByRole("button", {
        name: "Сохранить план",
      }) as HTMLButtonElement
    ).disabled,
  ).toBe(true);
  await user.click(
    screen.getByRole("button", { name: "Обновить этап, отменив правки" }),
  );
  expect(reload).toHaveBeenCalledOnce();
});

it("перестановка передаёт полный список этапов с версиями", async () => {
  const second = { ...stage, id: "second", name: "Стены", position: 2 };
  vi.mocked(api.listStages).mockResolvedValue([stage, second]);
  vi.mocked(api.listControlGates).mockResolvedValue([]);
  vi.mocked(api.reorderStages).mockResolvedValue([
    { ...second, position: 1 },
    { ...stage, position: 2 },
  ]);
  const user = userEvent.setup();
  render(<StageList project={project} account={owner} />);
  await user.click(
    await screen.findByRole("button", { name: "Поднять этап Стены" }),
  );
  expect(api.reorderStages).toHaveBeenCalledWith("project", [
    { id: "second", version: 2 },
    { id: "stage", version: 2 },
  ]);
  await screen.findByText("Порядок этапов сохранён");
});

it("выделяет этап, когда текущей роли нужно действие", async () => {
  vi.mocked(api.listStages).mockResolvedValue([stage]);
  vi.mocked(api.listControlGates).mockResolvedValue([
    {
      key: "FOUNDATION",
      status: "WAITING_CUSTOMER",
      responsibleRole: "CUSTOMER",
      title: "Перед заливкой фундамента",
      action: "Проверить завершённый этап",
      detail: "Проверьте фото.",
      stageId: stage.id,
    },
  ]);

  render(<StageList project={project} account={owner} />);

  expect(
    await screen.findByRole("link", {
      name: "Требуется действие: Проверить завершённый этап",
    }),
  ).toBeTruthy();
});

it("подрядчик видит только доступный переход и результат начала работ", async () => {
  vi.mocked(api.get).mockResolvedValue(project);
  vi.mocked(api.getStage).mockResolvedValue(stage);
  vi.mocked(api.startStage).mockResolvedValue({
    ...stage,
    status: "IN_PROGRESS",
    actualStart: "2026-11-13",
    version: 3,
  });
  const user = userEvent.setup();
  render(
    <StageDetail
      projectId="project"
      stageId="stage"
      account={{
        id: "builder",
        email: "builder@example.test",
        role: "CONTRACTOR",
      }}
    />,
  );
  await user.click(
    await screen.findByRole("button", { name: "Начать работы" }),
  );
  expect(api.startStage).toHaveBeenCalledWith("project", "stage", 2);
  await screen.findByText("Начало работ зафиксировано");
  expect(document.activeElement).toBe(
    screen.getByRole("heading", { name: "Фундамент", level: 1 }),
  );
  expect(screen.getByRole("button", { name: "Завершить работы" })).toBeTruthy();
  expect(screen.queryByRole("button", { name: "Изменить план" })).toBeNull();
});

it("просрочка и фактическое опоздание обозначаются текстом", () => {
  render(
    <StageTiming
      stage={{ ...stage, finishOverdueDays: 2, startLateDays: 1 }}
    />,
  );
  expect(screen.getByText("Завершение просрочено на 2 дн.")).toBeTruthy();
  expect(screen.getByText("Начат позже плана на 1 дн.")).toBeTruthy();
});
