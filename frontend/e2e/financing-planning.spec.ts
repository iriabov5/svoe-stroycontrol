import { test, expect } from "@playwright/test";
import { register, login, accessible, schema, csrf } from "./support";

test("Участок и ипотека встроены в карточку: проверка, расчёт, сохранение и документация", async ({
  page,
  request,
}, info) => {
  test.setTimeout(120000);
  const suffix = `${info.project.name}-${Date.now()}`;
  const owner = `plot-owner-${suffix}@example.test`;
  const builder = `plot-builder-${suffix}@example.test`;
  await register(request, owner, "CUSTOMER");
  await register(request, builder, "CONTRACTOR");
  await page.goto("/");
  await login(page, owner);
  await page.getByRole("link", { name: "Создать первую карточку" }).click();
  await page.getByLabel("Название дома").fill("Дом в Сокурах");
  await page.getByLabel("Адрес дома").fill("Тестовый участок в селе Сокуры");
  await page
    .getByRole("button", { name: "Создать карточку", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Участок и ипотека", exact: true }),
  ).toBeVisible();
  const panel = page.getByRole("region", {
    name: "Участок и ипотека",
    exact: true,
  });
  const id = /\/projects\/([^?]+)/.exec(new URL(page.url()).hash)![1];
  const api = page.context().request;
  const root = `/api/v1/projects/${id}`;
  const desktop = info.project.name === "desktop";
  async function capture(name: string, target = panel) {
    await target.scrollIntoViewIfNeeded();
    await page.screenshot({
      path: `../docs/screenshots/${name}.png`,
      fullPage: true,
    });
  }
  if (desktop) await capture("plot-mortgage-entry-desktop");
  await page
    .getByLabel("Кадастровый номер участка", { exact: true })
    .fill("16:24:070801:412");
  await expect(
    panel.getByRole("button", { name: "Проверить участок", exact: true }),
  ).toBeDisabled();
  await panel
    .getByRole("button", { name: "Сохранить черновик для проверки" })
    .click();
  await expect(
    panel.getByRole("button", { name: "Проверить участок", exact: true }),
  ).toBeEnabled();
  const checkResponse = page.waitForResponse(
    (r) =>
      r.url().endsWith("/financing-planning/plot-checks") &&
      r.request().method() === "POST",
  );
  await panel
    .getByRole("button", { name: "Проверить участок", exact: true })
    .click();
  const response = await checkResponse;
  expect(response.status(), await response.text()).toBe(200);
  await expect(
    panel.getByRole("heading", { name: "Подходит по демопроверке" }),
  ).toBeVisible();
  await schema(
    await api.get(root + "/financing-planning"),
    "FinancingPlanningView",
    200,
  );
  await accessible(page);
  if (desktop)
    await capture(
      "plot-check-eligible-desktop",
      panel.getByRole("region", { name: "Результат проверки участка" }),
    );
  else
    await capture(
      "plot-check-mobile",
      panel.getByRole("region", { name: "Результат проверки участка" }),
    );
  const calculator = panel.getByRole("region", { name: "Рассчитайте платёж" });
  await calculator
    .getByLabel("Срок кредита — ползунок", { exact: true })
    .focus();
  await page.keyboard.press("ArrowRight");
  await expect(
    calculator.getByLabel("Срок кредита, лет", { exact: true }),
  ).toHaveValue("21");
  await page.keyboard.press("ArrowLeft");
  await expect(
    calculator.getByLabel("Срок кредита, лет", { exact: true }),
  ).toHaveValue("20");
  await expect(calculator.getByText(/22\s*183,90/)).toBeVisible();
  if (desktop) await capture("mortgage-calculation-desktop", calculator);
  else await capture("mortgage-calculation-mobile", calculator);
  const calculationResponse = page.waitForResponse(
    (r) =>
      r.url().endsWith("/financing-planning/calculations") &&
      r.request().method() === "POST",
  );
  await calculator.getByRole("button", { name: "Сохранить расчёт" }).click();
  expect((await calculationResponse).status()).toBe(200);
  await expect(
    panel.getByText("Предварительный расчёт сохранён", { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(
    panel.getByRole("heading", { name: "Сохранённый предварительный расчёт" }),
  ).toBeVisible();
  await expect(
    panel
      .getByRole("region", { name: "Сохранённый предварительный расчёт" })
      .getByText(/22\s*183,90/),
  ).toBeVisible();
  if (desktop)
    await capture(
      "mortgage-calculation-saved-desktop",
      panel.getByRole("region", { name: "Сохранённый предварительный расчёт" }),
    );
  await calculator
    .getByLabel("Стоимость дома и участка, ₽", { exact: true })
    .fill("10000000");
  await expect(
    calculator.getByText(/превышает демонстрационный лимит на/),
  ).toBeVisible();
  if (desktop) await capture("mortgage-calculation-limit-desktop", calculator);
  await calculator
    .getByRole("checkbox", { name: /Совместное оформление/ })
    .check();
  await expect(
    calculator.getByText(/превышает демонстрационный лимит на/),
  ).toHaveCount(0);
  await accessible(page);

  async function number(value: string) {
    await page
      .getByLabel("Кадастровый номер участка", { exact: true })
      .fill(value);
    await panel
      .getByRole("button", { name: "Сохранить черновик для проверки" })
      .click();
    await expect(
      panel.getByRole("button", { name: "Проверить участок", exact: true }),
    ).toBeEnabled();
  }
  await number("50:21:0040112:105");
  await expect(
    panel.getByText(/Этот результат относится к прежнему номеру/),
  ).toBeVisible();
  if (desktop) await capture("plot-mortgage-stale-desktop");
  await panel
    .getByRole("button", { name: "Проверить участок", exact: true })
    .click();
  await expect(
    panel.getByRole("heading", { name: "Не подходит по демопроверке" }),
  ).toBeVisible();
  if (desktop)
    await capture(
      "plot-check-ineligible-desktop",
      panel.getByRole("region", { name: "Результат проверки участка" }),
    );
  await number("16:24:070801:999");
  await panel
    .getByRole("button", { name: "Проверить участок", exact: true })
    .click();
  await expect(
    panel.getByRole("heading", { name: "Участок отсутствует в демобазе" }),
  ).toBeVisible();
  if (desktop)
    await capture(
      "plot-check-not-found-desktop",
      panel.getByRole("region", { name: "Результат проверки участка" }),
    );
  await number("1:2:3:4");
  await panel
    .getByRole("button", { name: "Проверить участок", exact: true })
    .click();
  await expect(
    page.getByLabel("Кадастровый номер участка", { exact: true }),
  ).toHaveAttribute("aria-invalid", "true");
  if (desktop)
    await page.screenshot({
      path: "../docs/screenshots/plot-check-validation-desktop.png",
      fullPage: true,
    });
  await number("16:24:070801:412");
  // Управляемый сбой транспорта: тот же Problem, что возвращает ERROR-сценарий реального Mock API.
  await page.route("**/financing-planning/plot-checks", (route) =>
    route.fulfill({
      status: 503,
      contentType: "application/problem+json",
      body: JSON.stringify({
        type: "about:blank",
        status: 503,
        code: "BANK_DEMO_UNAVAILABLE",
        title:
          "Демонстрационный источник временно недоступен. Повторите проверку",
      }),
    }),
  );
  await panel
    .getByRole("button", { name: "Проверить участок", exact: true })
    .click();
  await expect(panel.getByRole("alert")).toContainText("временно недоступен");
  if (desktop) await capture("plot-check-unavailable-desktop");
  await page.unroute("**/financing-planning/plot-checks");
  await panel.getByRole("button", { name: "Повторить действие" }).click();
  await expect(
    panel.getByRole("heading", { name: "Подходит по демопроверке" }),
  ).toBeVisible();

  await page
    .getByLabel("Какой дом вы хотите", { exact: true })
    .fill("Тестовый дом для семьи");
  await page
    .getByLabel("Технология строительства", { exact: true })
    .fill("Каркас");
  await page.getByLabel("Желаемые материалы", { exact: true }).fill("Дерево");
  await page.getByLabel("Площадь, м²", { exact: true }).fill("80");
  await page.getByLabel("Этажность", { exact: true }).fill("1");
  await page.getByLabel("Комплектация", { exact: true }).fill("Тёплый контур");
  await page.getByLabel("Желаемый бюджет, ₽", { exact: true }).fill("3000000");
  await page.getByLabel("Желаемое начало строительства").fill("2026-12-01");
  await page.getByLabel("Желаемое завершение строительства").fill("2027-03-01");
  await page.getByRole("radio", { name: /Собственные средства/ }).check();
  const savedDraft = page.waitForResponse(
    (r) => r.url().endsWith("/request") && r.request().method() === "PATCH",
  );
  await page
    .getByRole("button", { name: "Сохранить черновик", exact: true })
    .click();
  const draftResponse = await savedDraft;
  expect(draftResponse.status(), await draftResponse.text()).toBe(200);
  await schema(
    await api.put(root + "/contractor", {
      data: { email: builder },
      headers: { "X-CSRF-TOKEN": await csrf(api) },
    }),
    "Project",
    200,
  );
  await page.reload();
  await page
    .getByRole("button", { name: "Отправить заявку подрядчику", exact: true })
    .click();
  await expect(
    panel.getByRole("button", { name: "Проверить участок", exact: true }),
  ).toHaveCount(0);
  await expect(
    panel.getByText(/Планирование доступно только для чтения/),
  ).toBeVisible();
  if (desktop) await capture("plot-mortgage-readonly-desktop");
  await accessible(page);

  // Новый проект не получает результаты другой карточки даже у того же владельца.
  const another = await schema(
    await api.post("/api/v1/projects", {
      data: { name: "Другой тестовый дом", address: "Другой участок" },
      headers: { "X-CSRF-TOKEN": await csrf(api) },
    }),
    "Project",
    201,
  );
  const plan = await schema(
    await api.get(`/api/v1/projects/${another.id}/financing-planning`),
    "FinancingPlanningView",
    200,
  );
  expect(plan.plotCheck).toBeNull();
  expect(plan.calculation).toBeNull();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page.goto(`/#/projects/${id}`);
  await expect(
    page.getByRole("heading", { name: "Каркасный дом", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("heading", { name: "Дом в Сокурах", exact: true }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Участок и ипотека", exact: true }),
  ).toHaveCount(0);
  await schema(
    await page.context().request.get(root + "/financing-planning"),
    "Problem",
    404,
  );
});
