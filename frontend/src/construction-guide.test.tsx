import { afterEach, expect, it, vi } from "vitest";
import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import {
  ConstructionActionsIndicator,
  ConstructionProfile,
  ControlGatesPanel,
} from "./ConstructionGuide";
import { request } from "./api/client";
import {
  gateLink,
  useGuidance,
  type ControlGates,
  type GuidanceState,
} from "./guidance";
vi.mock("./api/client", async (original) => ({
  ...(await original<typeof import("./api/client")>()),
  request: vi.fn(),
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const target = {
  projectId: "house",
  stageId: "networks",
  section: "JOURNAL" as const,
  submissionId: null,
};
const guide: ControlGates = {
  projectId: "house",
  computedAt: "2026-11-27T00:00:00Z",
  completedAt: null,
  primaryPointId: "UTILITIES:networks",
  actionablePointCount: 1,
  groups: [
    {
      key: "FOUNDATION",
      title: "Фундамент",
      points: [
        {
          id: "FOUNDATION:f",
          stageId: "f",
          title: "Проверка фундамента",
          status: "WAITING_CUSTOMER",
          reasonCode: "ACCEPTANCE_OPEN",
          detail: "Ждём заказчика",
          responsibleRole: "CUSTOMER",
          canAct: false,
          action: null,
          target: { ...target, stageId: "f", section: "ACCEPTANCE" },
          acceptedStageCount: null,
          totalStageCount: null,
        },
      ],
    },
    {
      key: "UTILITIES",
      title: "Сети",
      points: [
        {
          id: "UTILITIES:networks",
          stageId: "networks",
          title: "Подтверждение сетей",
          status: "WAITING_CONTRACTOR",
          reasonCode: "EVIDENCE_REQUIRED",
          detail: "Добавьте фотографию",
          responsibleRole: "CONTRACTOR",
          canAct: true,
          action: { code: "OPEN_JOURNAL", label: "Добавить подтверждения" },
          target,
          acceptedStageCount: null,
          totalStageCount: null,
        },
      ],
    },
    { key: "HANDOVER", title: "Передача", points: [] },
  ],
};
const state: GuidanceState<ControlGates> = {
  data: guide,
  error: "",
  refreshing: false,
  reload: vi.fn(),
};
it("главная точка следует персональному выбору сервера, а ожидание другой роли остаётся доступным", () => {
  const { container } = render(<ControlGatesPanel state={state} />);
  expect(
    container.querySelector(".control-point-primary h3")?.textContent,
  ).toBe("Подтверждение сетей");
  expect(
    screen
      .getAllByRole("link", { name: "Добавить подтверждения" })[0]
      .getAttribute("href"),
  ).toBe("#/projects/house/stages/networks?section=journal");
});
it("при обновлении или ошибке не предлагает выполнить действие по прежним данным", () => {
  render(<ControlGatesPanel state={{ ...state, error: "Нет соединения" }} />);
  expect(
    screen.queryByRole("link", { name: "Добавить подтверждения" }),
  ).toBeNull();
  expect(screen.getByRole("alert").textContent).toContain(
    "Показаны прежние данные",
  );
  fireEvent.click(screen.getByRole("button", { name: "Повторить обновление" }));
  expect(state.reload).toHaveBeenCalledOnce();
});
it("завершённый проект сохраняет историю и дату без новых строительных действий", () => {
  const done = {
    ...guide,
    completedAt: "2026-11-27T00:00:00Z",
    actionablePointCount: 0,
    groups: guide.groups.map((g) => ({
      ...g,
      points: g.points.map((p) => ({
        ...p,
        status: "COMPLETED" as const,
        canAct: false,
        action: null,
      })),
    })),
  };
  render(<ControlGatesPanel state={{ ...state, data: done }} />);
  expect(screen.getByText(/Строительство завершено/)).toBeTruthy();
  expect(
    screen.queryByRole("link", { name: "Добавить подтверждения" }),
  ).toBeNull();
});
it("индикатор снимается после публикации в той же сессии и повторно появляется после навигации", async () => {
  const summary = {
    computedAt: guide.computedAt,
    projectCount: 1,
    projects: [
      {
        projectId: "house",
        projectName: "Дом",
        primaryPointId: guide.primaryPointId!,
        target,
      },
    ],
  };
  vi.mocked(request).mockResolvedValue(summary);
  render(<ConstructionActionsIndicator />);
  await screen.findByRole("link", {
    name: "Требуется действие: 1 объект. Открыть Дом",
  });
  vi.mocked(request).mockResolvedValue({
    ...summary,
    projectCount: 0,
    projects: [],
  });
  await act(async () =>
    window.dispatchEvent(new Event("construction-changed")),
  );
  await waitFor(() =>
    expect(
      screen.queryByRole("link", { name: /Требуется действие/ }),
    ).toBeNull(),
  );
  vi.mocked(request).mockResolvedValue(summary);
  await act(async () =>
    window.dispatchEvent(new HashChangeEvent("hashchange")),
  );
  await screen.findByRole("link", {
    name: "Требуется действие: 1 объект. Открыть Дом",
  });
});
it("ошибка сводки видна и повтор восстанавливает индикатор, не подменяя ошибку нулём", async () => {
  vi.mocked(request)
    .mockRejectedValueOnce(new Error("Сбой источника"))
    .mockResolvedValueOnce({
      computedAt: guide.computedAt,
      projectCount: 0,
      projects: [],
    });
  render(<ConstructionActionsIndicator />);
  await screen.findByText(/Не удалось обновить действия/);
  fireEvent.click(screen.getByRole("button", { name: "Обновить действия" }));
  await waitFor(() =>
    expect(screen.queryByText(/Не удалось обновить действия/)).toBeNull(),
  );
});
it("паспорт показывает частичные сведения и отделяет пожелание от планового срока", async () => {
  vi.mocked(request).mockResolvedValue({
    projectId: "house",
    areaM2: 128.5,
    floors: 2,
    wallMaterial: null,
    foundationType: "УШП",
    roofType: null,
    plannedFinish: null,
    desiredFinish: "2027-10-01",
    sources: {
      areaM2: "REQUEST_DRAFT",
      floors: "REQUEST_DRAFT",
      foundationType: "PROFILE",
      desiredFinish: "REQUEST_DRAFT",
      plannedFinish: null,
      wallMaterial: null,
      roofType: null,
    },
  });
  render(<ConstructionProfile projectId="house" />);
  await screen.findByText("128.5 м²");
  expect(screen.getByText("УШП")).toBeTruthy();
  expect(screen.getByText("Желаемое завершение")).toBeTruthy();
  expect(screen.getAllByText("Не указано").length).toBeGreaterThan(1);
});
it("поздний ответ предыдущей карточки не заменяет текущую", async () => {
  let old!: (value: { name: string }) => void;
  vi.mocked(request)
    .mockImplementationOnce(
      () =>
        new Promise((r) => {
          old = r;
        }),
    )
    .mockResolvedValueOnce({ name: "Новая карточка" });
  function View({ path }: { path: string }) {
    const state = useGuidance<{ name: string }>(path);
    return <p>{state.data?.name}</p>;
  }
  const { rerender } = render(<View path="/old" />);
  rerender(<View path="/new" />);
  await screen.findByText("Новая карточка");
  await act(async () => old({ name: "Прежняя карточка" }));
  expect(screen.queryByText("Прежняя карточка")).toBeNull();
});
it("маршрут завершения ведёт в карточку, выбранный цикл передаётся отдельным параметром", () => {
  expect(gateLink({ ...target, stageId: null, section: "COMPLETION" })).toBe(
    "#/projects/house?section=completion",
  );
  expect(
    gateLink({ ...target, section: "ACCEPTANCE", submissionId: "cycle" }),
  ).toContain("section=acceptance&submissionId=cycle");
});
