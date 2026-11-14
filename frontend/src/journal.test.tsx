import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Journal } from "./Journal";
import { ReissuePanel } from "./Reissue";
import { request, type Stage } from "./api/client";
import type { components } from "./api/schema";

vi.mock("./api/client", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api/client")>()),
  request: vi.fn(),
}));
const owner = {
    id: "owner",
    email: "owner@example.test",
    role: "CUSTOMER" as const,
  },
  builder = {
    id: "builder",
    email: "builder@example.test",
    role: "CONTRACTOR" as const,
  };
const project = {
  id: "project",
  name: "Дом",
  address: "Участок",
  customerId: "owner",
  contractorId: "builder",
  createdAt: "2026-11-13T00:00:00Z",
};
const stage: Stage = {
  id: "stage",
  projectId: "project",
  version: 1,
  position: 1,
  name: "Фундамент",
  workType: "FOUNDATION",
  status: "IN_PROGRESS",
  plannedStart: null,
  plannedFinish: null,
  actualStart: "2026-11-13",
  actualFinish: null,
  startOverdueDays: 0,
  finishOverdueDays: 0,
  startLateDays: 0,
  finishLateDays: 0,
};
const entry: components["schemas"]["JournalEntry"] = {
  id: "entry",
  projectId: "project",
  stageId: "stage",
  version: 0,
  kind: "NOTE",
  status: "PUBLISHED",
  text: "Опалубка готова",
  photos: [],
  sourceEntryId: null,
  author: builder,
  createdAt: "2026-11-13T12:00:00Z",
  response: null,
};
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
beforeEach(() =>
  vi
    .mocked(request)
    .mockResolvedValue({ items: [], nextCursor: null, awaitingCount: 0 }),
);
describe("Лента этапа", () => {
  it("До начала этапа объясняет ограничение и не показывает публикацию", async () => {
    render(
      <Journal
        project={project}
        stage={{ ...stage, status: "PLANNED" }}
        account={builder}
      />,
    );
    await screen.findByText("Здесь будет история этапа");
    expect(
      screen.queryByRole("button", { name: "Добавить запись" }),
    ).toBeNull();
    expect(screen.getByText(/После начала этапа/)).toBeTruthy();
  });
  it("После сетевого сбоя сохраняет текст и повторяет публикацию с прежним ключом", async () => {
    const user = userEvent.setup();
    const bodies: unknown[] = [];
    let failed = true;
    vi.mocked(request).mockImplementation(async (_path, method, body) => {
      if (method === "POST") {
        bodies.push(body);
        if (failed) {
          failed = false;
          throw new Error("Нет связи");
        }
        return entry;
      }
      return {
        items: bodies.length > 1 ? [entry] : [],
        nextCursor: null,
        awaitingCount: 0,
      };
    });
    render(<Journal project={project} stage={stage} account={builder} />);
    await screen.findByText("Здесь будет история этапа");
    await user.click(screen.getByRole("button", { name: "Добавить запись" }));
    await user.type(screen.getByLabelText("Что сделано"), "Опалубка готова");
    await user.click(screen.getByRole("button", { name: "Опубликовать" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Нет связи",
    );
    expect(
      (screen.getByLabelText("Что сделано") as HTMLTextAreaElement).value,
    ).toBe("Опалубка готова");
    await user.click(screen.getByRole("button", { name: "Опубликовать" }));
    await screen.findByText("Запись опубликована");
    expect(bodies).toHaveLength(2);
    expect(bodies[0]).toEqual(bodies[1]);
  });
  it("Отрицательное решение требует пояснение рядом с полем", async () => {
    const user = userEvent.setup();
    const pending = {
      ...entry,
      kind: "APPROVAL" as const,
      status: "PENDING" as const,
    };
    vi.mocked(request).mockResolvedValue({
      items: [pending],
      nextCursor: null,
      awaitingCount: 1,
    });
    render(<Journal project={project} stage={stage} account={owner} />);
    await screen.findByText("Ждёт вашего ответа");
    await user.click(screen.getByRole("button", { name: "Не согласовать" }));
    const text = screen.getByLabelText("Комментарий к решению");
    expect(text.getAttribute("aria-invalid")).toBe("true");
    expect(text.getAttribute("aria-describedby")).toContain("-error");
    expect(
      vi.mocked(request).mock.calls.filter((c) => c[1] === "POST"),
    ).toHaveLength(0);
  });
  it("После ответа показывает результат рядом с вопросом и уменьшает число ожиданий", async () => {
    const user = userEvent.setup();
    const question = {
      ...entry,
      kind: "QUESTION" as const,
      status: "PENDING" as const,
      text: "Какой цвет?",
    };
    vi.mocked(request).mockImplementation(async (_path, method) =>
      method === "POST"
        ? {
            ...question,
            status: "ANSWERED",
            version: 1,
            response: {
              actorId: "owner",
              text: "Белый",
              decision: null,
              createdAt: "2026-11-13T13:00:00Z",
            },
          }
        : { items: [question], nextCursor: null, awaitingCount: 1 },
    );
    render(<Journal project={project} stage={stage} account={owner} />);
    await screen.findByText("Какой цвет?");
    await user.type(screen.getByLabelText("Ваш ответ"), "Белый");
    await user.click(screen.getByRole("button", { name: "Ответить" }));
    await screen.findByText("Ответ заказчика");
    expect(screen.getByText("Белый")).toBeTruthy();
    expect(screen.queryByText("Ждёт вашего ответа")).toBeNull();
  });
  it("Недоступная фотография не скрывает подпись и текст, HTML остаётся текстом", async () => {
    vi.mocked(request).mockResolvedValue({
      items: [
        {
          ...entry,
          text: "<script>alert(1)</script>",
          photos: [
            {
              id: "photo",
              url: "https://example.test/a.png",
              caption: "Армирование",
            },
          ],
        },
      ],
      nextCursor: null,
      awaitingCount: 0,
    });
    const { container } = render(
      <Journal project={project} stage={stage} account={builder} />,
    );
    await screen.findByText("<script>alert(1)</script>");
    fireEvent.error(screen.getByRole("img", { name: "Армирование" }));
    expect(container.querySelector("figure > p")?.textContent).toBe(
      "Изображение недоступно",
    );
    expect(container.querySelector("figcaption")?.textContent).toBe(
      "Армирование",
    );
    expect(container.querySelector("script")).toBeNull();
  });
});
describe("Копия заявки", () => {
  it("Предлагает открыть существующую копию, не создавать ещё одну", async () => {
    vi.mocked(request).mockResolvedValue({ targetProjectId: "next" });
    render(<ReissuePanel project={project} />);
    const link = await screen.findByRole("link", {
      name: "Открыть копию заявки",
    });
    expect(link.getAttribute("href")).toBe("#/projects/next");
    expect(
      screen.queryByRole("button", { name: "Выбрать другого подрядчика" }),
    ).toBeNull();
  });
  it("Перед копированием показывает адресата и сохраняет email после сбоя", async () => {
    const user = userEvent.setup();
    vi.mocked(request).mockImplementation(async (_path, method) => {
      if (method === "POST") throw new Error("Нет связи");
      return { targetProjectId: null };
    });
    render(<ReissuePanel project={project} />);
    await screen.findByRole("button", { name: "Выбрать другого подрядчика" });
    await user.type(
      screen.getByLabelText("Email другого подрядчика"),
      "new@example.test",
    );
    await user.click(
      screen.getByRole("button", { name: "Выбрать другого подрядчика" }),
    );
    expect(screen.getByText(/перед отправкой проверьте бюджет/)).toBeTruthy();
    await user.click(
      screen.getByRole("button", { name: "Создать копию заявки" }),
    );
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Нет связи",
    );
    expect(
      (screen.getByLabelText("Email другого подрядчика") as HTMLInputElement)
        .value,
    ).toBe("new@example.test");
  });
});
