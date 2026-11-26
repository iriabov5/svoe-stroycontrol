import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { App } from "./App";
import { api, ApiError } from "./api/client";

vi.mock("./api/client", async (original) => ({
  ...(await original<typeof import("./api/client")>()),
  api: {
    me: vi.fn(),
    logout: vi.fn(),
    list: vi.fn(),
    listControlGates: vi.fn(),
  },
}));
vi.mock("./Projects", () => ({
  ProjectList: () => <h1>Список домов</h1>,
  CreateProject: () => <h1>Новый дом</h1>,
  ProjectDetail: () => <h1>Карточка дома</h1>,
}));
vi.mock("./Stages", () => ({ StageDetail: () => <h1>Рабочий этап</h1> }));
vi.mock("./Auth", () => ({ Auth: () => <h1>Вход в сервис</h1> }));
vi.mock("./Profile", () => ({
  Profile: ({ onContinue }: Readonly<{ onContinue: () => void }>) => (
    <div>
      <h1>Личный профиль</h1>
      <input id="profile-name" aria-label="Ваше имя" />
      <button type="button" onClick={onContinue}>
        Вернуться к работе
      </button>
    </div>
  ),
}));
const owner = {
  id: "owner",
  email: "owner@example.test",
  role: "CUSTOMER" as const,
  displayName: "Иван",
};
beforeEach(() => {
  window.history.replaceState(null, "", "/#/projects");
  vi.mocked(api.me).mockResolvedValue(owner);
  vi.mocked(api.logout).mockResolvedValue(undefined);
  vi.mocked(api.list).mockResolvedValue([]);
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it.each([
  ["/projects", "Список домов"],
  ["/projects/new", "Новый дом"],
  ["/projects/house", "Карточка дома"],
  ["/projects/house/stages/foundation", "Рабочий этап"],
  ["/profile", "Личный профиль"],
])(
  "открывает сохранённый маршрут %s после проверки сессии",
  async (route, heading) => {
    window.history.replaceState(null, "", "/#" + route);
    render(<App />);
    expect(await screen.findByRole("heading", { name: heading })).toBeTruthy();
  },
);
it("подрядчик не получает форму создания дома по прямому маршруту", async () => {
  vi.mocked(api.me).mockResolvedValue({ ...owner, role: "CONTRACTOR" });
  window.history.replaceState(null, "", "/#/projects/new");
  render(<App />);
  expect(
    await screen.findByRole("heading", { name: "Список домов" }),
  ).toBeTruthy();
  expect(screen.queryByRole("heading", { name: "Новый дом" })).toBeNull();
});
it("показывает в шапке доступную ссылку на объект с действием", async () => {
  vi.mocked(api.list).mockResolvedValue([
    {
      id: "house",
      name: "Дом семьи Орловых",
      address: "Берёзки",
      customerId: owner.id,
      contractorId: "builder",
      createdAt: "2027-04-10T00:00:00Z",
    },
  ]);
  vi.mocked(api.listControlGates).mockResolvedValue([
    {
      key: "FOUNDATION",
      status: "WAITING_CUSTOMER",
      responsibleRole: "CUSTOMER",
      title: "Перед заливкой фундамента",
      action: "Проверить завершённый этап",
      detail: "Проверьте фото.",
      stageId: "foundation",
    },
  ]);

  render(<App />);

  const alert = await screen.findByRole("link", {
    name: "Требуется действие: 1. Открыть Дом семьи Орловых",
  });
  expect(alert.getAttribute("href")).toBe("#/projects/house");
});
it("неавторизованному аккаунту показывает вход, сохраняя ссылку на этап", async () => {
  vi.mocked(api.me).mockRejectedValue(
    new ApiError(401, "AUTH_REQUIRED", "Войдите"),
  );
  window.history.replaceState(null, "", "/#/projects/house/stages/foundation");
  render(<App />);
  expect(
    await screen.findByRole("heading", { name: "Вход в сервис" }),
  ).toBeTruthy();
  expect(window.location.hash).toContain("/stages/foundation");
});
it("позволяет повторить проверку сессии после сетевой ошибки", async () => {
  const user = userEvent.setup();
  vi.mocked(api.me).mockRejectedValueOnce(new Error("Нет связи"));
  render(<App />);
  await user.click(
    await screen.findByRole("button", { name: "Повторить подключение" }),
  );
  expect(
    await screen.findByRole("heading", { name: "Список домов" }),
  ).toBeTruthy();
});
it("диалог имени удерживает фокус и возвращает его при Escape", async () => {
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole("heading", { name: "Список домов" });
  const trigger = screen.getByRole("button", { name: "Выйти" });
  trigger.focus();
  act(() => window.dispatchEvent(new Event("profile-required")));
  expect(screen.getByRole("dialog", { name: "Заполните имя" })).toBeTruthy();
  expect(document.activeElement).toBe(screen.getByLabelText("Ваше имя"));
  await user.keyboard("{Escape}");
  expect(screen.queryByRole("dialog")).toBeNull();
});
it("выход закрывает авторизованный экран", async () => {
  const user = userEvent.setup();
  render(<App />);
  await screen.findByRole("heading", { name: "Список домов" });
  await user.click(screen.getByRole("button", { name: "Выйти" }));
  expect(
    await screen.findByRole("heading", { name: "Вход в сервис" }),
  ).toBeTruthy();
  expect(api.logout).toHaveBeenCalledOnce();
});
