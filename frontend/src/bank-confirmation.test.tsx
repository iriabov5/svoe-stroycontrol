import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { BankConfirmationPanel } from "./BankConfirmation";
import { request, ApiError, type Project, type Stage } from "./api/client";
import type { components } from "./api/schema";
vi.mock("./api/client", async (original) => ({
  ...(await original<typeof import("./api/client")>()),
  request: vi.fn(),
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
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
const preview = {
  project: { name: project.name },
  stage,
  documents: {
    estimateText: "Согласованная смета",
    contractText: "Согласованный договор",
  },
};
const confirmation = {
  mortgage: true,
  connected: true,
  canSend: true,
  version: 0,
  preview,
  submissions: [],
};
function panel() {
  return render(
    <BankConfirmationPanel
      project={project}
      stage={stage}
      acceptance={{ ...submission, state: "ACCEPTED" }}
    />,
  );
}
it("при собственных средствах не показывает банковские действия", async () => {
  vi.mocked(request).mockResolvedValue({ ...confirmation, mortgage: false });
  panel();
  await waitFor(() =>
    expect(
      screen.queryByRole("heading", { name: "Подтверждение для банка" }),
    ).toBeNull(),
  );
});
it("подключает дело только после явного согласия владельца", async () => {
  const user = userEvent.setup();
  let connected = false;
  vi.mocked(request).mockImplementation(async (_path, method) => {
    if (method === "POST") {
      connected = true;
      return { id: "case", isDemo: true };
    }
    return { ...confirmation, connected, canSend: connected };
  });
  panel();
  await user.click(
    await screen.findByText("Подключить демо-дело", { selector: "summary" }),
  );
  const button = screen.getByRole("button", { name: "Подключить демо-дело" });
  expect((button as HTMLButtonElement).disabled).toBe(true);
  await user.click(
    screen.getByLabelText("Разрешаю подключить синтетическое кредитное дело"),
  );
  await user.click(button);
  expect(
    await screen.findByRole("button", { name: "Проверить и отправить" }),
  ).toBeTruthy();
  expect(request).toHaveBeenCalledWith(
    "/projects/project/bank-case",
    "POST",
    expect.objectContaining({
      consent: true,
      idempotencyKey: expect.any(String),
    }),
  );
});
it("предпросмотр показывает согласованные документы до явной отправки", async () => {
  const user = userEvent.setup();
  vi.mocked(request).mockResolvedValue(confirmation);
  panel();
  await user.click(
    await screen.findByRole("button", { name: "Проверить и отправить" }),
  );
  expect(screen.getByText("Согласованная смета")).toBeTruthy();
  expect(screen.getByText("Согласованный договор")).toBeTruthy();
  expect(
    vi.mocked(request).mock.calls.some(([, method]) => method === "POST"),
  ).toBe(false);
  await user.click(screen.getByRole("button", { name: "Вернуться к этапу" }));
  expect(screen.queryByText("Состав подтверждения")).toBeNull();
});
it("ошибка отправки сохраняет пояснение и тот же ключ для безопасного повтора", async () => {
  const user = userEvent.setup();
  vi.mocked(request).mockImplementation(async (_path, method) => {
    if (method === "POST")
      throw new ApiError(503, "BANK_DEMO_UNAVAILABLE", "Нет связи с банком");
    return confirmation;
  });
  panel();
  await user.click(
    await screen.findByRole("button", { name: "Проверить и отправить" }),
  );
  const note = screen.getByLabelText("Комментарий к подтверждению");
  await user.type(note, "Армирование проверено");
  const send = screen.getByRole("button", { name: "Отправить в демо-банк" });
  await user.click(send);
  expect((await screen.findByRole("alert")).textContent).toContain(
    "Нет связи с банком",
  );
  expect((note as HTMLTextAreaElement).value).toBe("Армирование проверено");
  await user.click(send);
  const attempts = vi
    .mocked(request)
    .mock.calls.filter(([, method]) => method === "POST");
  expect(attempts).toHaveLength(2);
  expect(attempts[0][2]).toEqual(attempts[1][2]);
});
it("показывает решение и пояснения в сохранённой истории отправок", async () => {
  const user = userEvent.setup();
  vi.mocked(request).mockResolvedValue({
    ...confirmation,
    canSend: false,
    submissions: [
      {
        id: "sent",
        version: 1,
        revision: 1,
        state: "ACCEPTED",
        createdAt: "2026-11-14T12:00:00Z",
        bankReference: "demo-reference",
        reason: null,
        packet: {
          clarifications: ["Паспорт уточнён", "Фото дополнены"],
          additionalFiles: [],
        },
      },
    ],
  });
  panel();
  await user.click(await screen.findByText("История отправок · 1"));
  expect(screen.getByText("Принято демо-банком", { exact: true })).toBeTruthy();
  expect(screen.getByText(/Паспорт уточнён/)).toBeTruthy();
  expect(
    screen.queryByRole("button", { name: "Проверить и отправить" }),
  ).toBeNull();
});
