import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AcceptancePanel } from "./Acceptance";
import { CompletionPanel } from "./Completion";
import {
  api,
  request,
  ApiError,
  type Account,
  type Project,
  type Stage,
} from "./api/client";

import type { components } from "./api/schema";

vi.mock("./api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/client")>()),
  request: vi.fn(),
  api: { get: vi.fn() },
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const account: Account = {
  id: "owner",
  email: "owner@example.test",
  role: "CUSTOMER",
  displayName: "Иван",
};
const project: Project = {
  id: "project",
  name: "Дом",
  address: "Участок",
  customerId: "owner",
  contractorId: "builder",
  createdAt: "2026-11-14T00:00:00Z",
};
const stage: Stage = {
  id: "stage",
  projectId: "project",
  version: 2,
  position: 1,
  name: "Фундамент",
  workType: "FOUNDATION",
  status: "WORK_COMPLETED",
  plannedStart: null,
  plannedFinish: null,
  actualStart: "2026-11-13",
  actualFinish: "2026-11-14",
  startOverdueDays: 0,
  finishOverdueDays: 0,
  startLateDays: 0,
  finishLateDays: 0,
};
const submission: components["schemas"]["AcceptanceSubmission"] = {
  id: "submission",
  projectId: "project",
  stageId: "stage",
  cycleNumber: 1,
  summary: "Армирование проверено",
  state: "OPEN",
  version: 0,
  evidence: [],
  authorSnapshot: {
    accountId: "builder",
    displayName: "Алексей",
    globalRole: "CONTRACTOR",
    organizationId: null,
    organizationName: null,
    position: null,
    capturedAt: "2026-11-14T08:00:00Z",
    legacy: false,
  },
  createdAt: "2026-11-14T08:00:00Z",
  decision: null,
  comment: null,
  decidedBy: null,
  decidedAt: null,
};

it("возврат связывает ошибку с замечанием и сохраняет введённый текст", async () => {
  const user = userEvent.setup();
  vi.mocked(request).mockImplementation(async (_path, method) => {
    if (method === "POST")
      throw new ApiError(400, "VALIDATION_ERROR", "Уточните замечание", {
        comment: "Опишите, что исправить",
      });
    return {
      state: "OPEN",
      submissions: [submission],
      bankStatus: null,
      mortgage: false,
    };
  });
  render(<AcceptancePanel project={project} stage={stage} account={account} />);
  await user.click(
    await screen.findByRole("button", { name: "Вернуть с замечаниями" }),
  );
  await user.type(
    screen.getByLabelText("Замечания к этапу"),
    "Нужно уточнить армирование",
  );
  await user.click(screen.getByRole("button", { name: "Отправить замечания" }));
  await screen.findByText("Опишите, что исправить");
  expect(
    screen.getByLabelText("Замечания к этапу").getAttribute("aria-invalid"),
  ).toBe("true");
  expect(
    (screen.getByLabelText("Замечания к этапу") as HTMLTextAreaElement).value,
  ).toBe("Нужно уточнить армирование");
});

it("собственные средства скрывают банковский блок после приёмки", async () => {
  vi.mocked(request).mockResolvedValue({
    state: "ACCEPTED",
    submissions: [
      {
        ...submission,
        state: "ACCEPTED",
        decision: "ACCEPT",
        decidedAt: "2026-11-14T09:00:00Z",
      },
    ],
    bankStatus: null,
    mortgage: false,
  });
  render(<AcceptancePanel project={project} stage={stage} account={account} />);
  await screen.findAllByText("Принят заказчиком");
  expect(
    screen.queryByRole("heading", { name: "Подтверждение для банка" }),
  ).toBeNull();
  expect(
    vi
      .mocked(request)
      .mock.calls.every(([path]) => !path.includes("bank-confirmation")),
  ).toBe(true);
});

it("завершение требует отдельного подтверждения и обновляет карточку после успеха", async () => {
  const user = userEvent.setup(),
    changed = vi.fn();
  const ready = {
    projectId: "project",
    total: 2,
    accepted: 2,
    canComplete: true,
    reason: null,
    version: 4,
    completedAt: null,
    completedBy: null,
  };
  vi.mocked(request).mockImplementation(async (_path, method) =>
    method === "POST"
      ? { ...ready, canComplete: false, completedAt: "2026-11-14T09:00:00Z" }
      : ready,
  );
  vi.mocked(api.get).mockResolvedValue({
    ...project,
    completedAt: "2026-11-14T09:00:00Z",
  });
  render(
    <CompletionPanel project={project} account={account} onChanged={changed} />,
  );
  await user.click(
    await screen.findByRole("button", {
      name: "Подтвердить завершение строительства",
    }),
  );
  expect(
    vi.mocked(request).mock.calls.some(([, method]) => method === "POST"),
  ).toBe(false);
  await user.click(
    screen.getByRole("button", { name: "Подтверждаю завершение" }),
  );
  await screen.findByRole("heading", { name: "Строительство завершено" });
  await waitFor(() =>
    expect(changed).toHaveBeenCalledWith(
      expect.objectContaining({ completedAt: "2026-11-14T09:00:00Z" }),
    ),
  );
});
