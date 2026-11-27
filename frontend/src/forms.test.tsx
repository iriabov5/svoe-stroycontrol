import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Auth } from "./Auth";
import { CreateProject, ProjectDetail, ProjectList } from "./Projects";
import { api, ApiError, request } from "./api/client";

vi.mock("./api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("./api/client")>();
  return {
    ...original,
    request: vi.fn(),
    api: {
      register: vi.fn(),
      login: vi.fn(),
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      assign: vi.fn(),
      listStages: vi.fn(),
    },
  };
});
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
beforeEach(() => vi.mocked(api.listStages).mockResolvedValue([]));
const owner = {
  id: "owner",
  email: "owner@example.test",
  role: "CUSTOMER" as const,
};
const project = {
  id: "project",
  name: "Дом",
  address: "Адрес",
  customerId: owner.id,
  contractorId: null,
  createdAt: "2026-11-13T10:00:00Z",
};

describe("Формы и состояния первой итерации", () => {
  it("при ошибке входа сохраняет email и очищает пароль", async () => {
    vi.mocked(api.login).mockRejectedValue(
      new ApiError(401, "INVALID_CREDENTIALS", "Неверный email или пароль"),
    );
    const user = userEvent.setup();
    render(<Auth onLogin={vi.fn()} expired={false} />);
    await user.type(screen.getByLabelText("Email"), owner.email);
    await user.type(screen.getByLabelText("Пароль"), "wrong-password-123");
    await user.click(screen.getByRole("button", { name: "Войти" }));
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Неверный email",
    );
    expect((screen.getByLabelText("Email") as HTMLInputElement).value).toBe(
      owner.email,
    );
    expect((screen.getByLabelText("Пароль") as HTMLInputElement).value).toBe(
      "",
    );
  });
  it("регистрирует выбранную роль и предлагает отдельный вход", async () => {
    vi.mocked(api.register).mockResolvedValue({ ...owner, role: "CONTRACTOR" });
    const login = vi.fn();
    const user = userEvent.setup();
    render(<Auth onLogin={login} expired={false} />);
    await user.click(
      screen.getByRole("button", { name: "Зарегистрироваться" }),
    );
    await user.type(screen.getByLabelText("Email"), owner.email);
    await user.type(screen.getByLabelText("Пароль"), "example-password-123");
    await user.click(screen.getByRole("radio", { name: /Подрядчик/ }));
    await user.type(screen.getByLabelText("Имя и фамилия"), "Сергей Иванов");
    await user.click(screen.getByRole("button", { name: "Создать аккаунт" }));
    expect((await screen.findByRole("status")).textContent).toContain(
      "Аккаунт создан",
    );
    expect(api.register).toHaveBeenCalledWith({
      email: owner.email,
      password: "example-password-123",
      role: "CONTRACTOR",
      displayName: "Сергей Иванов",
    });
    expect(login).not.toHaveBeenCalled();
  });
  it("отклоняет пробельное имя и связывает ошибку с полем", async () => {
    const user = userEvent.setup();
    render(<CreateProject />);
    await user.type(screen.getByLabelText("Название дома"), "   ");
    await user.type(screen.getByLabelText("Адрес дома"), "Адрес");
    await user.click(screen.getByRole("button", { name: "Создать карточку" }));
    expect(
      screen.getByLabelText("Название дома").getAttribute("aria-invalid"),
    ).toBe("true");
    expect(api.create).not.toHaveBeenCalled();
  });
  it("показывает email перед назначением и сохраняет его при ошибке", async () => {
    vi.mocked(api.get).mockResolvedValue(project);
    vi.mocked(request).mockImplementation(async (path) => {
      if (path.endsWith("/control-gates"))
        return {
          projectId: project.id,
          computedAt: "2026-11-27T00:00:00Z",
          completedAt: null,
          groups: [],
          primaryPointId: null,
          actionablePointCount: 0,
        };
      if (path.endsWith("/construction-profile"))
        return {
          projectId: project.id,
          areaM2: null,
          floors: null,
          wallMaterial: null,
          foundationType: null,
          roofType: null,
          plannedFinish: null,
          desiredFinish: null,
          sources: {},
        };
      return {
        organization: {
          id: "org",
          displayName: "Тёплый дом",
          inn: "9000000014",
        },
        primaryContact: {
          accountId: "builder",
          displayName: "Сергей Иванов",
          globalRole: "CONTRACTOR",
          organizationId: "org",
          organizationName: "Тёплый дом",
          position: "Руководитель",
          capturedAt: null,
          legacy: false,
        },
      };
    });
    vi.mocked(api.assign).mockRejectedValue(
      new ApiError(
        422,
        "CONTRACTOR_NOT_FOUND",
        "Подрядчик с таким email не зарегистрирован",
      ),
    );
    const user = userEvent.setup();
    render(<ProjectDetail id={project.id} account={owner} created={false} />);
    await user.type(
      await screen.findByLabelText("Email подрядчика"),
      "missing@example.test",
    );
    await user.click(
      screen.getByRole("button", { name: "Назначить подрядчика" }),
    );
    expect(api.assign).not.toHaveBeenCalled();
    await user.click(
      screen.getByRole("button", { name: "Подтвердить назначение" }),
    );
    await screen.findByRole("alert");
    expect(
      (screen.getByLabelText("Email подрядчика") as HTMLInputElement).value,
    ).toBe("missing@example.test");
  });
  it("объясняет подрядчику пустой список и позволяет повторить загрузку после ошибки", async () => {
    vi.mocked(api.list)
      .mockRejectedValueOnce(new Error("Нет соединения"))
      .mockResolvedValueOnce([]);
    const user = userEvent.setup();
    render(<ProjectList account={{ ...owner, role: "CONTRACTOR" }} />);
    await screen.findByRole("alert");
    await user.click(
      screen.getByRole("button", { name: "Повторить загрузку" }),
    );
    await waitFor(() =>
      expect(screen.getByText(/Попросите заказчика/).textContent).toContain(
        owner.email,
      ),
    );
    expect(screen.queryByRole("link", { name: /Создать/ })).toBeNull();
  });
});
