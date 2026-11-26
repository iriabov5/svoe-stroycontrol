import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { ControlGates } from "./ControlGates";
import { api } from "./api/client";

vi.mock("./api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/client")>()),
  api: { listControlGates: vi.fn() },
}));

afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it("карточка объясняет заказчику следующее действие и ведёт к этапу", async () => {
  vi.mocked(api.listControlGates).mockResolvedValue([
    {
      key: "FOUNDATION",
      status: "WAITING_CUSTOMER",
      responsibleRole: "CUSTOMER",
      title: "Перед заливкой фундамента",
      action: "Проверить завершённый этап",
      detail: "Проверьте фото армирования.",
      stageId: "stage-id",
    },
  ]);

  render(
    <ControlGates
      projectId="project-id"
      account={{
        id: "customer",
        email: "customer@example.test",
        role: "CUSTOMER",
      }}
    />,
  );

  expect(await screen.findByText("Перед заливкой фундамента")).toBeTruthy();
  expect(screen.getByText("Требуется действие")).toBeTruthy();
  expect(
    screen.getByText("Следующий шаг").parentElement?.textContent,
  ).toContain("Проверить завершённый этап");
  expect(
    screen.getByRole("link", { name: "Открыть этап" }).getAttribute("href"),
  ).toBe("#/projects/project-id/stages/stage-id");
});

it("не ломает карточку проекта, если контрольные точки ещё не созданы", async () => {
  vi.mocked(api.listControlGates).mockResolvedValue([]);

  render(
    <ControlGates
      projectId="project-id"
      account={{
        id: "customer",
        email: "customer@example.test",
        role: "CUSTOMER",
      }}
    />,
  );

  expect(
    await screen.findByText(
      "Контрольные точки появятся, когда в плане строительства будут этапы.",
    ),
  ).toBeTruthy();
});
