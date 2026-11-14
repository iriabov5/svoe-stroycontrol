import { afterEach, expect, it, vi } from "vitest";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Profile } from "./Profile";
import { ContractorCard } from "./ContractorCard";
import { api, request, type Account, type Project } from "./api/client";
import type { components } from "./api/schema";

vi.mock("./api/client", async (original) => ({
  ...(await original<typeof import("./api/client")>()),
  request: vi.fn(),
  api: { me: vi.fn(), get: vi.fn() },
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
const director: Account = {
  id: "director",
  email: "director@example.test",
  role: "CONTRACTOR",
  displayName: "Сергей Иванов",
  profileVersion: 0,
};
const organization: components["schemas"]["Organization"] = {
  id: "org",
  ownerAccountId: "director",
  type: "LEGAL_ENTITY",
  displayName: "Тёплый дом",
  legalName: "ООО Тёплый дом",
  inn: "7707083893",
  address: "Строительная, 1",
  phone: null,
  version: 0,
};
const membership = {
  id: "membership",
  organizationId: "org",
  organizationName: organization.displayName,
  position: "Руководитель",
  role: "OWNER",
};
const project: Project = {
  id: "house",
  name: "Дом",
  address: "Участок",
  customerId: "owner",
  contractorId: "director",
  contractorOrganizationId: "org",
  contractorOrganization: organization,
  createdAt: "2026-11-14T12:00:00Z",
  permissions: {
    canManageTeam: true,
    canPrepareDocuments: true,
    canPublishJournal: true,
    canRecordWork: true,
    canAnswerJournal: false,
    canDecideDocuments: false,
  },
};
function organizationData(role = "OWNER") {
  vi.mocked(request).mockImplementation(async (path) =>
    path === "/organizations/me"
      ? { organization, membership: { ...membership, role } }
      : path.endsWith("/contractor-team")
        ? { version: 0, members: [] }
        : [],
  );
  vi.mocked(api.me).mockResolvedValue(director);
  vi.mocked(api.get).mockResolvedValue(project);
}
it("новая организация показывает подходящую длину ИНН для юрлица и ИП", async () => {
  const user = userEvent.setup();
  vi.mocked(request).mockImplementation(async (path) =>
    path === "/organizations/me"
      ? { organization: null, membership: null }
      : [],
  );
  render(
    <Profile account={director} onAccount={vi.fn()} onContinue={vi.fn()} />,
  );
  await screen.findByRole("heading", { name: "Представьте свою организацию" });
  expect(screen.getByText("10 цифр, проверим контрольную цифру.")).toBeTruthy();
  await user.selectOptions(
    screen.getByLabelText("Вид подрядчика"),
    "SOLE_TRADER",
  );
  expect(screen.getByText("12 цифр, проверим контрольные цифры.")).toBeTruthy();
  expect(
    screen.getByRole("button", { name: "Создать организацию" }),
  ).toBeTruthy();
});
it("руководитель меняет реквизиты, сохраняя неизменным ИНН", async () => {
  const user = userEvent.setup();
  organizationData();
  render(
    <Profile account={director} onAccount={vi.fn()} onContinue={vi.fn()} />,
  );
  await user.click(await screen.findByText("Изменить реквизиты"));
  expect((screen.getByLabelText("ИНН") as HTMLInputElement).readOnly).toBe(
    true,
  );
  await user.click(screen.getByRole("button", { name: "Сохранить реквизиты" }));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/organizations/org",
      "PATCH",
      expect.objectContaining({
        version: 0,
        displayName: organization.displayName,
      }),
    ),
  );
});
it("сотрудник читает реквизиты без формы изменения организации", async () => {
  organizationData("MEMBER");
  render(
    <Profile account={director} onAccount={vi.fn()} onContinue={vi.fn()} />,
  );
  expect(await screen.findByText(organization.legalName)).toBeTruthy();
  expect(screen.queryByText("Изменить реквизиты")).toBeNull();
});
it("ошибка загрузки организации оставляет видимую причину и повтор", async () => {
  vi.mocked(request).mockRejectedValue(new Error("Нет связи"));
  render(
    <Profile account={director} onAccount={vi.fn()} onContinue={vi.fn()} />,
  );
  expect((await screen.findByRole("alert")).textContent).toContain("Нет связи");
});
it("руководитель открывает настройки команды и сохраняет пустое назначение", async () => {
  const user = userEvent.setup();
  organizationData();
  render(
    <ContractorCard project={project} account={director} onChanged={vi.fn()} />,
  );
  await user.click(
    await screen.findByRole("button", { name: "Настроить команду стройки" }),
  );
  expect(screen.getByText(/Сначала пригласите коллег/)).toBeTruthy();
  await user.click(screen.getByRole("button", { name: "Сохранить команду" }));
  await waitFor(() =>
    expect(request).toHaveBeenCalledWith(
      "/projects/house/contractor-team",
      "PUT",
      { version: 0, members: [] },
    ),
  );
  expect(await screen.findByText("Команда стройки сохранена")).toBeTruthy();
});
it("отмена настройки команды возвращает список без записи", async () => {
  const user = userEvent.setup();
  organizationData();
  render(
    <ContractorCard project={project} account={director} onChanged={vi.fn()} />,
  );
  await user.click(
    await screen.findByRole("button", { name: "Настроить команду стройки" }),
  );
  await user.click(screen.getByRole("button", { name: "Отмена" }));
  expect(
    screen.getByRole("button", { name: "Настроить команду стройки" }),
  ).toBeTruthy();
  expect(
    vi.mocked(request).mock.calls.some(([, method]) => method === "PUT"),
  ).toBe(false);
});
it("участник видит персональные назначения, но не управление командой", async () => {
  vi.mocked(request).mockResolvedValue({
    version: 1,
    members: [
      {
        id: "lawyer",
        displayName: "Мария Соколова",
        position: "Юрист",
        documents: true,
        works: false,
        email: "lawyer@example.test",
      },
    ],
  });
  render(
    <ContractorCard
      project={{
        ...project,
        permissions: { ...project.permissions!, canManageTeam: false },
      }}
      account={{ ...director, id: "owner", role: "CUSTOMER" }}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByText("Мария Соколова")).toBeTruthy();
  expect(screen.getByText("Документы", { exact: true })).toBeTruthy();
  expect(
    screen.queryByRole("button", { name: "Настроить команду стройки" }),
  ).toBeNull();
});
