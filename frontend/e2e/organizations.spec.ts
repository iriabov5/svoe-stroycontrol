import { test, expect } from "@playwright/test";
import { register, login, csrf, schema, password, accessible } from "./support";

test("Организация, юрист, прораб и Иван работают под своими именами и с разными доступами", async ({
  page,
  request,
}, info) => {
  test.setTimeout(120000);
  const suffix = `${info.project.name}-${Date.now()}`;
  const owner = `team-owner-${suffix}@example.test`,
    director = `team-director-${suffix}@example.test`,
    lawyer = `team-lawyer-${suffix}@example.test`,
    foreman = `team-foreman-${suffix}@example.test`;
  await register(request, owner, "CUSTOMER", { displayName: "Иван" });
  await register(request, director, "CONTRACTOR", {
    displayName: "Сергей Иванов",
    organizationName: "ООО «Тёплый дом»",
  });
  await register(request, lawyer, "CONTRACTOR", {
    displayName: "Мария Соколова",
    organization: false,
  });
  await register(request, foreman, "CONTRACTOR", {
    displayName: "Алексей Орлов",
    organization: false,
  });
  await page.goto("/");
  await login(page, director);
  await page.getByRole("link", { name: /Сергей Иванов · профиль/ }).click();
  await expect(
    page.getByRole("heading", { name: "Мой профиль", exact: true }),
  ).toBeVisible();
  await page.getByText("Пригласить сотрудника", { exact: true }).click();
  for (const [email, position] of [
    [lawyer, "Юрист"],
    [foreman, "Прораб"],
  ]) {
    await page.getByLabel("Email сотрудника", { exact: true }).fill(email);
    await page.getByLabel("Должность", { exact: true }).fill(position);
    await page
      .getByRole("button", { name: "Пригласить в организацию", exact: true })
      .click();
    await expect(
      page.getByLabel("Email сотрудника", { exact: true }),
    ).toHaveValue("");
  }
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/organization-profile-${info.project.name}.png`,
    fullPage: true,
  });
  for (const [email, name] of [
    [lawyer, "Мария Соколова"],
    [foreman, "Алексей Орлов"],
  ]) {
    await page.getByRole("button", { name: "Выйти", exact: true }).click();
    await login(page, email);
    await page
      .getByRole("link", { name: new RegExp(name + " · профиль") })
      .click();
    await page
      .getByRole("button", { name: "Принять приглашение", exact: true })
      .click();
    await expect(
      page.getByText("Приглашение принято", { exact: true }),
    ).toBeVisible();
  }
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
    name: "Дом с командой подрядчика",
    address: "с. Озёрное, участок 14",
  });
  const root = `/projects/${project.id}`;
  await command(root + "/contractor", { email: director }, "PUT");
  const wishes = await command(
    root + "/request",
    {
      version: 0,
      description: "Дом для семьи",
      technology: "Каркас",
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
  await signIn(director);
  let proposal = await command(root + "/proposals", {});
  proposal = await command(
    root + "/proposals/" + proposal.id,
    {
      version: proposal.version,
      estimateText: "Работы и материалы — 2 800 000 ₽",
      termsText: null,
      totalPrice: 2800000,
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
  proposal = await command(root + "/proposals/" + proposal.id + "/submit", {
    version: proposal.version,
  });
  await signIn(owner);
  await command(root + "/proposals/" + proposal.id + "/approve", {
    version: proposal.version,
  });
  await signIn(director);
  const contract = await command(root + "/contracts", {});
  await page.goto("/#" + root);
  await page.reload();
  await page.getByText("Подробнее об организации", { exact: true }).click();
  await page
    .getByRole("button", { name: "Настроить команду стройки", exact: true })
    .click();
  await page
    .getByRole("group", { name: "Мария Соколова · Юрист", exact: true })
    .getByLabel("Документы", { exact: true })
    .check();
  await page
    .getByRole("group", { name: "Алексей Орлов · Прораб", exact: true })
    .getByLabel("Работы", { exact: true })
    .check();
  await page
    .getByRole("button", { name: "Сохранить команду", exact: true })
    .click();
  await expect(
    page.getByText("Команда стройки сохранена", { exact: true }),
  ).toBeVisible();
  await accessible(page);
  await page.locator(".contractor-card").screenshot({
    path: `../docs/screenshots/project-team-${info.project.name}.png`,
  });
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, lawyer);
  await page
    .getByRole("link")
    .filter({ hasText: "Дом с командой подрядчика" })
    .click();
  await page
    .getByLabel("Текст договора подряда", { exact: true })
    .fill(
      "Договор подряда на строительство по согласованной смете. Цена 2 800 000 ₽.",
    );
  await page
    .getByRole("button", { name: "Сохранить договор", exact: true })
    .click();
  await page
    .getByLabel("Условия проверены, отправить заказчику", { exact: true })
    .check();
  await page
    .getByRole("button", {
      name: "Отправить договор на согласование",
      exact: true,
    })
    .click();
  const panel = page.locator(".document-panel").filter({
    has: page.getByRole("heading", { name: "Договор подряда", exact: true }),
  });
  await expect(
    panel.getByText("Мария Соколова · Юрист", { exact: true }).first(),
  ).toBeVisible();
  const sent = await schema(
    await api.get("/api/v1" + root + "/contracts/" + contract.id),
    "ConstructionContract",
    200,
  );
  expect(sent.createdBy).not.toBe(sent.submittedById);
  expect(sent.submittedBySnapshot.displayName).toBe("Мария Соколова");
  await accessible(page);
  await panel.screenshot({
    path: `../docs/screenshots/contract-author-${info.project.name}.png`,
  });
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page
    .getByRole("link")
    .filter({ hasText: "Дом с командой подрядчика" })
    .click();
  await page
    .getByRole("button", { name: "Согласовать договор", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Подтвердить согласование", exact: true })
    .click();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, foreman);
  await page
    .getByRole("link")
    .filter({ hasText: "Дом с командой подрядчика" })
    .click();
  await expect(
    page.getByRole("button", { name: "Подготовить договор", exact: true }),
  ).toHaveCount(0);
  const forbidden = await api.post(
    "/api/v1" + root + "/contracts/" + contract.id + "/submit",
    {
      data: { version: sent.version, contractorConfirmed: true },
      headers: { "X-CSRF-TOKEN": await csrf(api) },
    },
  );
  await schema(forbidden, "Problem", 403);
  await page.getByRole("link", { name: "Фундамент", exact: true }).click();
  await page
    .getByRole("button", { name: "Начать работы", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Добавить запись", exact: true })
    .click();
  await page.getByLabel("Тип записи", { exact: true }).selectOption("QUESTION");
  await page
    .getByLabel("Вопрос заказчику", { exact: true })
    .fill("С какой стороны оставить проход к дому?");
  await page
    .getByRole("button", { name: "Отправить вопрос", exact: true })
    .click();
  const article = page.getByRole("article", { name: "Вопрос", exact: true });
  await expect(
    article.getByText("Алексей Орлов · Прораб", { exact: true }),
  ).toBeVisible();
  const stageUrl = page.url();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page.goto(stageUrl);
  await page
    .getByLabel("Ваш ответ", { exact: true })
    .fill("Оставьте проход со стороны дороги.");
  await page.getByRole("button", { name: "Ответить", exact: true }).click();
  await expect(
    page
      .getByRole("article", { name: "Вопрос", exact: true })
      .getByText("Иван · Заказчик", { exact: true }),
  ).toBeVisible();
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/team-journal-${info.project.name}.png`,
    fullPage: true,
  });
  const personal = await api.get("/api/v1" + root + "/contractor-team");
  const team = await personal.json();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, director);
  await page.goto("/#" + root);
  await page.getByText("Подробнее об организации", { exact: true }).click();
  await page
    .getByRole("button", { name: "Настроить команду стройки", exact: true })
    .click();
  await page
    .getByRole("group", { name: "Алексей Орлов · Прораб", exact: true })
    .getByLabel("Работы", { exact: true })
    .uncheck();
  await page
    .getByRole("button", { name: "Сохранить команду", exact: true })
    .click();
  await expect(
    page.getByText("Команда стройки сохранена", { exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, foreman);
  await schema(await api.get("/api/v1" + root), "Problem", 404);
  expect(
    team.members.some(
      (m: { displayName: string }) => m.displayName === "Алексей Орлов",
    ),
  ).toBe(true);
});
