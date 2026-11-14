import { test, expect } from "@playwright/test";
import { register, login, csrf, schema, password, accessible } from "./support";

test("Отказ от сметы создаёт отдельную заявку другому подрядчику без чужой истории", async ({
  page,
  request,
}, info) => {
  const suffix = `${info.project.name}-${Date.now()}`;
  const owner = `reissue-owner-${suffix}@example.test`,
    first = `reissue-first-${suffix}@example.test`,
    second = `reissue-second-${suffix}@example.test`;
  for (const [email, role] of [
    [owner, "CUSTOMER"],
    [first, "CONTRACTOR"],
    [second, "CONTRACTOR"],
  ])
    await register(request, email, role);
  const api = page.request;
  async function signIn(email: string) {
    await schema(
      await api.post("/api/v1/auth/login", {
        data: { email, password },
        headers: { "X-CSRF-TOKEN": await csrf(api) },
      }),
      "Account",
      200,
    );
  }
  async function command(path: string, data: unknown, method = "POST") {
    const response = await api.fetch("/api/v1" + path, {
      method,
      data,
      headers: { "X-CSRF-TOKEN": await csrf(api) },
    });
    expect(response.ok(), await response.text()).toBe(true);
    return response.json();
  }
  await signIn(owner);
  const project = await command("/projects", {
    name: "Дом для нового предложения",
    address: "с. Озёрное, участок 12",
  });
  const root = `/projects/${project.id}`;
  await command(root + "/contractor", { email: first }, "PUT");
  const wishes = await command(
    root + "/request",
    {
      version: 0,
      description: "Одноэтажный каркасный дом для семьи",
      technology: "Деревянный каркас",
      materials: "Сухая древесина",
      areaM2: 80,
      floors: 1,
      completionScope: "Тёплый контур",
      desiredBudget: 3000000,
      desiredStart: "2026-12-01",
      desiredFinish: "2027-03-01",
      financingSource: "OWN_FUNDS",
    },
    "PATCH",
  );
  await command(root + "/request/submit", { version: wishes.version });
  await signIn(first);
  let proposal = await command(root + "/proposals", {});
  proposal = await command(
    root + "/proposals/" + proposal.id,
    {
      version: proposal.version,
      estimateText: "Работы и материалы — 3 400 000 ₽",
      termsText: null,
      totalPrice: 3400000,
      images: [],
      stages: [
        {
          id: crypto.randomUUID(),
          name: "Фундамент",
          workType: "FOUNDATION",
          expectedResult: "Готовое основание",
          plannedStart: "2026-12-01",
          plannedFinish: "2026-12-10",
        },
      ],
    },
    "PUT",
  );
  await command(root + "/proposals/" + proposal.id + "/submit", {
    version: proposal.version,
  });
  await signIn(owner);
  await page.goto("/#/projects/" + project.id);
  await page
    .getByRole("button", { name: "Отклонить предложение", exact: true })
    .click();
  await page
    .getByLabel("Причина отказа", { exact: true })
    .fill("Стоимость выше согласуемого бюджета");
  await page
    .getByRole("button", { name: "Подтвердить отказ", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Другой подрядчик", exact: true }),
  ).toBeVisible();
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/proposal-rejected-${info.project.name}.png`,
    fullPage: true,
  });
  await page.getByLabel("Email другого подрядчика").fill(second);
  await page
    .getByRole("button", { name: "Выбрать другого подрядчика", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Создать копию заявки", exact: true })
    .click();
  await expect(page.getByLabel("Какой дом вы хотите")).toHaveValue(
    "Одноэтажный каркасный дом для семьи",
  );
  const target = page.url().split("/projects/")[1].split("?")[0];
  expect(target).not.toBe(project.id);
  const copied = await schema(
    await api.get(`/api/v1/projects/${target}/request`),
    "ConstructionRequest",
    200,
  );
  expect(copied.state).toBe("DRAFT");
  expect(copied.version).toBe(0);
  expect(
    await (await api.get(`/api/v1/projects/${target}/proposals`)).json(),
  ).toEqual([]);
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/request-reissue-${info.project.name}.png`,
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Отправить заявку подрядчику", exact: true })
    .click();
  await expect(
    page.getByText("Передана подрядчику", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, second);
  await page
    .getByRole("link")
    .filter({ hasText: "Дом для нового предложения" })
    .click();
  await expect(
    page.getByRole("button", { name: "Подготовить предложение", exact: true }),
  ).toBeVisible();
  await schema(await api.get("/api/v1" + root), "Problem", 404);
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, first);
  await schema(await api.get(`/api/v1/projects/${target}`), "Problem", 404);
});
