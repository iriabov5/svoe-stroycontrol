import { test, expect } from "@playwright/test";
import type { APIRequestContext } from "@playwright/test";
import {
  legacyFixture,
  schema,
  csrf,
  register,
  login,
  accessible,
  ajv,
  password,
} from "./support";

async function seed(request: APIRequestContext, suffix: string) {
  const owner = `stage-owner-${suffix}@example.test`,
    builder = `stage-builder-${suffix}@example.test`,
    outsider = `stage-other-${suffix}@example.test`;
  await register(request, owner, "CUSTOMER");
  await register(request, builder, "CONTRACTOR");
  await register(request, outsider, "CUSTOMER");
  await schema(
    await request.post("/api/v1/auth/login", {
      data: { email: owner, password },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Account",
    200,
  );
  const home = await schema(
    await request.post("/api/v1/projects", {
      data: {
        name: "Дом в Берёзовке",
        address: "с. Берёзовка, ул. Центральная, 14",
      },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Project",
    201,
  );
  await legacyFixture(home.id);
  await schema(
    await request.put(`/api/v1/projects/${home.id}/contractor`, {
      data: { email: builder },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Project",
    200,
  );
  return { owner, builder, outsider, home };
}

test("План этапов, порядок и даты доступны обеим ролям; подрядчик фиксирует ход работ", async ({
  page,
  request,
}, info) => {
  const { owner, builder, outsider } = await seed(
    request,
    `${info.project.name}-${Date.now()}`,
  );
  const today = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Europe/Moscow",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date());
  const before = (days: number) =>
    new Date(Date.parse(`${today}T12:00:00Z`) - days * 86400000)
      .toISOString()
      .slice(0, 10);
  await page.goto("/");
  await login(page, owner);
  await page.getByRole("link").filter({ hasText: "Дом в Берёзовке" }).click();
  await expect(
    page.getByRole("heading", { name: "План работ пока не составлен" }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Добавить этап", exact: true })
    .click();
  await expect(page.getByLabel("Название этапа")).toBeFocused();
  await page.getByLabel("Название этапа").fill("Фундамент");
  await page.getByLabel("Плановое начало").fill(before(2));
  await page.getByLabel("Плановое завершение").fill(before(1));
  await accessible(page);
  await page
    .getByRole("button", { name: "Добавить этап", exact: true })
    .click();
  await expect(
    page.getByRole("link", { name: "Фундамент", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Завершение просрочено на 1 дн.")).toBeVisible();
  await page
    .getByRole("button", { name: "Добавить этап", exact: true })
    .click();
  await page.getByLabel("Название этапа").fill("Стены");
  await page.getByLabel("Вид работ").selectOption("WALLS");
  await page
    .getByRole("button", { name: "Добавить этап", exact: true })
    .click();
  await page.getByRole("button", { name: "Поднять этап Стены" }).click();
  const list = page
    .getByRole("region", { name: "Этапы строительства" })
    .getByRole("listitem");
  await expect(list.first()).toContainText("Стены");
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/stages-${info.project.name}.png`,
    fullPage: true,
  });
  await page.getByRole("link", { name: "Фундамент", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Фундамент", exact: true }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Изменить план" }).click();
  await page.getByLabel("Плановое завершение").fill(before(3));
  await page.getByRole("button", { name: "Сохранить план" }).click();
  await expect(page.getByLabel("Плановое завершение")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await page.getByLabel("Плановое завершение").fill(before(1));
  await page.getByRole("button", { name: "Сохранить план" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "План этапа сохранён" }),
  ).toContainText("План этапа сохранён");
  await page.getByRole("button", { name: "Выйти" }).click();
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await login(page, builder);
  await page.getByRole("link").filter({ hasText: "Дом в Берёзовке" }).click();
  await expect(page.getByRole("button", { name: "Добавить этап" })).toHaveCount(
    0,
  );
  await page.getByRole("link", { name: "Фундамент", exact: true }).click();
  await page.getByRole("button", { name: "Начать работы" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Начало работ зафиксировано" }),
  ).toContainText("Начало работ зафиксировано");
  await page.reload();
  await expect(page.getByText("В работе", { exact: true })).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Фундамент", exact: true }),
  ).toBeFocused();
  await page.getByRole("button", { name: "Завершить работы" }).click();
  await expect(
    page.getByText("Работы завершены", { exact: true }),
  ).toBeVisible();
  await expect(page.getByText("Завершён позже плана на 1 дн.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Изменить план" })).toHaveCount(
    0,
  );
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/stage-detail-${info.project.name}.png`,
    fullPage: true,
  });
  const stageUrl = page.url();
  await page.getByRole("button", { name: "Выйти" }).click();
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await login(page, outsider);
  await page.goto(stageUrl);
  await expect(page.getByRole("alert")).toContainText(
    "не найдена или недоступна",
  );
});

test("Операции этапов соответствуют OpenAPI и сохраняют контракт версии", async ({
  request,
}, info) => {
  const { builder, home } = await seed(
    request,
    `contract-${info.project.name}-${Date.now()}`,
  );
  const base = `/api/v1/projects/${home.id}/stages`;
  const first = await schema(
    await request.post(base, {
      data: {
        name: "Фундамент",
        workType: "FOUNDATION",
        plannedStart: null,
        plannedFinish: null,
      },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Stage",
    201,
  );
  const second = await schema(
    await request.post(base, {
      data: { name: "Стены", workType: "WALLS" },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Stage",
    201,
  );
  await schema(await request.get(`${base}/${first.id}`), "Stage", 200);
  const order = await request.put(`${base}/order`, {
    data: {
      stages: [
        { id: second.id, version: second.version },
        { id: first.id, version: first.version },
      ],
    },
    headers: { "X-CSRF-TOKEN": await csrf(request) },
  });
  expect(order.status()).toBe(200);
  const validate = ajv.compile({
    type: "array",
    items: { $ref: "contract#/components/schemas/Stage" },
  });
  const reordered = await order.json();
  expect(validate(reordered), JSON.stringify(validate.errors)).toBe(true);
  const current = reordered.find(
    (stage: { id: string }) => stage.id === first.id,
  );
  const body = {
    name: "Фундамент дома",
    workType: "FOUNDATION",
    version: current.version,
    plannedStart: null,
    plannedFinish: null,
  };
  const updated = await schema(
    await request.put(`${base}/${first.id}`, {
      data: body,
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Stage",
    200,
  );
  await schema(
    await request.put(`${base}/${first.id}`, {
      data: body,
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Problem",
    409,
  );
  await schema(
    await request.post("/api/v1/auth/login", {
      data: { email: builder, password },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Account",
    200,
  );
  const started = await schema(
    await request.post(`${base}/${first.id}/start`, {
      data: { version: updated.version },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Stage",
    200,
  );
  const completed = await schema(
    await request.post(`${base}/${first.id}/complete`, {
      data: { version: started.version },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Stage",
    200,
  );
  const repeated = await schema(
    await request.post(`${base}/${first.id}/complete`, {
      data: { version: started.version },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Stage",
    200,
  );
  expect(repeated.actualFinish).toBe(completed.actualFinish);
  expect(repeated.version).toBe(completed.version);
  expect(validate(await (await request.get(base)).json())).toBe(true);
});
