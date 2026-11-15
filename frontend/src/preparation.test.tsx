import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Preparation } from "./Preparation";
import { request, api } from "./api/client";
vi.mock("./api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("./api/client")>();
  return {
    ...original,
    request: vi.fn(),
    api: { ...original.api, get: vi.fn() },
  };
});
const owner = {
  id: "owner",
  email: "owner@example.test",
  role: "CUSTOMER" as const,
};
const project = {
  id: "house",
  name: "Дом",
  address: "Участок",
  customerId: "owner",
  contractorId: "builder",
  createdAt: "2026-11-13T12:00:00Z",
  processMode: "PROPOSAL_REQUIRED" as const,
  requestSubmitted: false,
  approvedProposalId: null,
  approvedContractId: null,
};
const data = {
  id: "request",
  projectId: "house",
  state: "DRAFT" as const,
  version: 0,
  location: "Участок",
  technologyRecommendation: false,
  materialsRecommendation: false,
  financingSource: "OWN_FUNDS" as const,
};
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});
beforeEach(() => {
  vi.mocked(request).mockImplementation(async (path) =>
    path.endsWith("/request") ? data : [],
  );
  vi.mocked(api.get).mockResolvedValue(project);
});
describe("Подготовка строительства", () => {
  it("Сохраняет неполный черновик и не теряет текст при сетевой ошибке", async () => {
    const user = userEvent.setup();
    render(
      <Preparation project={project} account={owner} onChanged={vi.fn()} />,
    );
    const field = await screen.findByLabelText("Какой дом вы хотите");
    await user.type(field, "Дом для семьи");
    vi.mocked(request).mockRejectedValueOnce(new Error("Нет связи"));
    await user.click(
      screen.getByRole("button", { name: "Сохранить черновик" }),
    );
    expect((field as HTMLTextAreaElement).value).toBe("Дом для семьи");
    expect((await screen.findByRole("alert")).textContent).toContain(
      "Нет связи",
    );
  });
  it("Для подрядчика показывает только отправленную заявку, без кредитных подробностей", async () => {
    vi.mocked(request).mockImplementation(async (path) =>
      path.endsWith("/request")
        ? {
            ...data,
            state: "SUBMITTED",
            financingSource: "RSHB_RURAL_MORTGAGE",
          }
        : [],
    );
    render(
      <Preparation
        project={{ ...project, requestSubmitted: true }}
        account={{
          id: "builder",
          email: "builder@example.test",
          role: "CONTRACTOR",
        }}
        onChanged={vi.fn()}
      />,
    );
    await screen.findByText("Сельская ипотека РСХБ");
    expect(
      screen.queryByRole("heading", { name: "Сведения об ипотеке" }),
    ).toBeNull();
    expect(
      screen.queryByRole("button", { name: "Отправить заявку подрядчику" }),
    ).toBeNull();
    expect(
      screen.getByRole("button", { name: "Подготовить предложение" }),
    ).toBeTruthy();
  });
  it("Текст сметы выводится буквально, пользовательский HTML не исполняется", async () => {
    const proposal = {
      id: "doc",
      projectId: "house",
      revisionNo: 1,
      state: "SUBMITTED",
      version: 1,
      estimateText: "<img src=x onerror=alert(1)>",
      termsText: null,
      totalPrice: 100,
      images: [],
      stages: [],
      decision: null,
    };
    vi.mocked(request).mockImplementation(async (path) =>
      path.endsWith("/request")
        ? { ...data, state: "SUBMITTED" }
        : path.endsWith("/proposals")
          ? [proposal]
          : [],
    );
    const { container } = render(
      <Preparation
        project={{ ...project, requestSubmitted: true }}
        account={owner}
        onChanged={vi.fn()}
      />,
    );
    await screen.findByText("<img src=x onerror=alert(1)>");
    expect(container.querySelector("img")).toBeNull();
  });
});

const submittedProposal = {
  id: "proposal",
  projectId: "house",
  revisionNo: 2,
  version: 3,
  state: "SUBMITTED",
  estimateText: "Работы и материалы",
  termsText: "Без отделки",
  totalPrice: 150,
  images: [],
  stages: [
    {
      id: "foundation",
      name: "Фундамент",
      workType: "FOUNDATION",
      expectedResult: "Основание готово",
      plannedStart: "2026-11-01",
      plannedFinish: "2027-04-01",
    },
  ],
  decision: null,
};
const builder = {
  id: "builder",
  email: "builder@example.test",
  role: "CONTRACTOR" as const,
};
function documentData(
  proposal: Record<string, unknown>,
  contract?: Record<string, unknown>,
) {
  vi.mocked(request).mockImplementation(async (path) => {
    if (path.endsWith("/request"))
      return {
        ...data,
        state: "SUBMITTED",
        desiredBudget: 100,
        desiredStart: "2026-12-01",
        desiredFinish: "2027-03-01",
      };
    if (path.endsWith("/proposals")) return [proposal];
    if (path.endsWith("/contracts")) return contract ? [contract] : [];
    return {};
  });
}
it("показывает превышение бюджета и выход строительного плана за желаемые сроки", async () => {
  documentData(submittedProposal);
  render(
    <Preparation
      project={{ ...project, requestSubmitted: true }}
      account={owner}
      onChanged={vi.fn()}
    />,
  );
  expect(await screen.findByText(/Выше желаемого бюджета/)).toBeTruthy();
  expect(screen.getByText("Выходит за желаемые сроки")).toBeTruthy();
  expect(screen.getByText("Без отделки")).toBeTruthy();
  expect(screen.getByText("Основание готово")).toBeTruthy();
});
it("перед согласованием сметы показывает цену и номер версии в подтверждении", async () => {
  const user = userEvent.setup();
  documentData(submittedProposal);
  render(
    <Preparation
      project={{ ...project, requestSubmitted: true }}
      account={owner}
      onChanged={vi.fn()}
    />,
  );
  await user.click(
    await screen.findByRole("button", { name: "Согласовать предложение" }),
  );
  expect(screen.getByText(/Согласовать версию 2 на/)).toBeTruthy();
  await user.click(
    screen.getByRole("button", { name: "Подтвердить согласование" }),
  );
  expect(request).toHaveBeenCalledWith(
    "/projects/house/proposals/proposal/approve",
    "POST",
    { version: 3 },
  );
});
it("после замечаний подрядчик готовит новую версию, не меняя старую", async () => {
  documentData({
    ...submittedProposal,
    state: "CHANGES_REQUESTED",
    decision: {
      decision: "CHANGES_REQUESTED",
      comment: "Уточните состав материалов",
      decidedAt: "2026-11-14T12:00:00Z",
    },
  });
  render(
    <Preparation
      project={{ ...project, requestSubmitted: true }}
      account={builder}
      onChanged={vi.fn()}
    />,
  );
  expect(
    await screen.findByRole("button", { name: "Подготовить новую версию" }),
  ).toBeTruthy();
  expect(screen.getByText("Уточните состав материалов")).toBeTruthy();
});
it("согласование договора показывает отдельное подтверждение и текст договора", async () => {
  const user = userEvent.setup();
  documentData(
    { ...submittedProposal, state: "APPROVED" },
    {
      id: "contract",
      projectId: "house",
      revisionNo: 1,
      version: 2,
      state: "SUBMITTED",
      contractText: "Договор на строительство дома",
      decision: null,
    },
  );
  render(
    <Preparation
      project={{
        ...project,
        requestSubmitted: true,
        approvedProposalId: "proposal",
      }}
      account={owner}
      onChanged={vi.fn()}
    />,
  );
  await user.click(
    await screen.findByRole("button", { name: "Согласовать договор" }),
  );
  expect(screen.getByText("Договор на строительство дома")).toBeTruthy();
  expect(
    screen.getByText(/Подтвердить согласование этой версии договора/),
  ).toBeTruthy();
});
it("сохранённая картинка сметы передаётся по ID без URL и локального ключа", async () => {
  const user = userEvent.setup();
  documentData({
    ...submittedProposal,
    state: "DRAFT",
    stages: [],
    images: [
      {
        attachmentId: "image-id",
        url: "/api/v1/projects/house/attachments/image-id/content",
        caption: "План дома",
      },
    ],
  });
  render(
    <Preparation
      project={{ ...project, requestSubmitted: true }}
      account={builder}
      onChanged={vi.fn()}
    />,
  );
  const caption = await screen.findByLabelText("Подпись изображения");
  await user.clear(caption);
  await user.type(caption, "План первого этажа");
  await user.click(
    screen.getByRole("button", { name: "Сохранить предложение" }),
  );
  const saved = vi
    .mocked(request)
    .mock.calls.find(([, method]) => method === "PUT");
  expect(saved?.[2]).toEqual(
    expect.objectContaining({
      images: [{ attachmentId: "image-id", caption: "План первого этажа" }],
    }),
  );
});

it.each([
  ["FOUND", "Одобрено · демоданные"],
  ["EXPIRED", "Срок решения истёк"],
  ["NOT_FOUND", "Одобренный кредит не найден. Можно продолжить без подгрузки."],
])(
  "демо-одобрение %s показывает соответствующее состояние владельцу",
  async (status, message) => {
    vi.mocked(request).mockImplementation(async (path) => {
      if (path.endsWith("/request"))
        return { ...data, financingSource: "RSHB_RURAL_MORTGAGE" };
      if (path.endsWith("/mortgage-approval"))
        return {
          status,
          snapshot:
            status === "NOT_FOUND"
              ? null
              : {
                  amount: 3000000,
                  rate: 3,
                  termMonths: 240,
                  decisionDate: "2026-11-01",
                  validUntil: "2027-02-01",
                  fetchedAt: "2026-11-14T12:00:00Z",
                },
        };
      return [];
    });
    render(
      <Preparation project={project} account={owner} onChanged={vi.fn()} />,
    );
    expect(await screen.findByText(message)).toBeTruthy();
  },
);
it("сводка отправленной заявки сохраняет рекомендации и размеры будущего дома", async () => {
  vi.mocked(request).mockImplementation(async (path) =>
    path.endsWith("/request")
      ? {
          ...data,
          state: "SUBMITTED",
          technologyRecommendation: true,
          materialsRecommendation: true,
          lengthM: 8,
          widthM: 10,
          areaM2: 80,
        }
      : [],
  );
  render(
    <Preparation
      project={{ ...project, requestSubmitted: true }}
      account={builder}
      onChanged={vi.fn()}
    />,
  );
  expect(
    await screen.findByText(/Технология: нужна рекомендация/),
  ).toBeTruthy();
  expect(screen.getByText(/Материалы: нужна рекомендация/)).toBeTruthy();
  expect(screen.getByText(/8 × 10 м/)).toBeTruthy();
});
