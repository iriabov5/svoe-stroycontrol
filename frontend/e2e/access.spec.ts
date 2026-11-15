import { test, expect } from "@playwright/test";
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

test("Заказчик создаёт дом и назначает подрядчика; постороннему карточка недоступна", async ({
  page,
  request,
}, info) => {
  const suffix = `${info.project.name}-${Date.now()}`;
  const owner = `owner-${suffix}@example.test`,
    builder = `builder-${suffix}@example.test`,
    other = `other-${suffix}@example.test`;
  await register(request, builder, "CONTRACTOR");
  await register(request, other, "CUSTOMER");
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await accessible(page);
  await expect(
    page.getByRole("img", { name: "РСХБ", exact: true }),
  ).toHaveCount(1);
  await expect(page.locator("footer.site-footer")).toHaveCount(0);
  if (info.project.name === "desktop") {
    await page.setViewportSize({ width: 1366, height: 768 });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollHeight <= window.innerHeight + 1,
      ),
    ).toBe(true);
    await page.screenshot({
      path: "../docs/screenshots/account-login.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 1440, height: 1000 });
  }
  await page.getByRole("button", { name: "Зарегистрироваться" }).click();
  await page.getByLabel("Ваше имя", { exact: true }).fill("Иван");
  await page.getByLabel("Email", { exact: true }).fill(owner);
  await page.getByLabel("Пароль").fill(password);
  await page.getByRole("radio", { name: /Заказчик/ }).check();
  await accessible(page);
  await page
    .getByRole("button", { name: "Создать аккаунт", exact: true })
    .click();
  await expect(page.getByRole("status")).toContainText("Аккаунт создан");
  await login(page, owner);
  await expect(
    page.getByRole("heading", { name: "У каждого дома есть начало" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Создать первую карточку" }).click();
  await page.getByLabel("Название дома").fill("Дом в Берёзовке");
  await page.getByLabel("Адрес дома").fill("с. Берёзовка, ул. Центральная, 14");
  await accessible(page);
  if (info.project.name === "desktop")
    await page.screenshot({
      path: "../docs/screenshots/project-create.png",
      fullPage: true,
    });
  await page
    .getByRole("button", { name: "Создать карточку", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Дом в Берёзовке" }),
  ).toBeVisible();
  const url = page.url().replace("?created", "");
  await page
    .getByLabel("Email подрядчика")
    .fill(`missing-${suffix}@example.test`);
  await page.getByRole("button", { name: "Назначить подрядчика" }).click();
  const fixtureId = /projects\/([^?]+)/.exec(page.url().split("#/")[1])?.[1];
  if (fixtureId) await legacyFixture(fixtureId);
  await expect(page.getByRole("alert")).toContainText("не зарегистрирован");
  await expect(page.getByLabel("Email подрядчика")).toHaveValue(
    `missing-${suffix}@example.test`,
  );
  await page.getByLabel("Email подрядчика").fill(builder);
  await page.getByRole("button", { name: "Назначить подрядчика" }).click();
  await expect(
    page.getByRole("status").filter({ hasText: "Открыть доступ" }),
  ).toContainText(builder);
  if (info.project.name === "desktop")
    await page.screenshot({
      path: "../docs/screenshots/contractor-confirm.png",
      fullPage: true,
    });
  await page.getByRole("button", { name: "Подтвердить назначение" }).click();
  await expect(
    page.getByText("Подрядчик назначен", { exact: true }),
  ).toBeVisible();
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/project-${info.project.name}.png`,
    fullPage: true,
  });
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Дом в Берёзовке" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти" }).click();
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await page.goto("/");
  await login(page, builder);
  await page.getByRole("link").filter({ hasText: "Дом в Берёзовке" }).click();
  await expect(
    page.getByText("ООО «Тёплый дом»", { exact: true }).first(),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /Назначить/ })).toHaveCount(0);
  await accessible(page);
  await page.getByRole("button", { name: "Выйти" }).click();
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await page.goto("/");
  await login(page, other);
  await expect(
    page.getByRole("link").filter({ hasText: "Дом в Берёзовке" }),
  ).toHaveCount(0);
  await page.goto(url);
  await expect(page.getByRole("alert")).toContainText(
    "не найдена или недоступна",
  );
});

test("Подрядчик понимает пустой список; клавиатура и истёкшая сессия сохраняют маршрут", async ({
  page,
  request,
}) => {
  const email = `empty-${Date.now()}@example.test`;
  await register(request, email, "CONTRACTOR");
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
  await page.getByLabel("Email", { exact: true }).focus();
  await page.keyboard.type(email);
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Пароль")).toBeFocused();
  await page.keyboard.type(password);
  await page.keyboard.press("Tab");
  await page.keyboard.press("Enter");
  await expect(
    page.getByRole("heading", { name: "Вас пока не назначили на объект" }),
  ).toBeVisible();
  await expect(page.getByText(/Попросите заказчика/)).toContainText(email);
  await accessible(page);
  const token = await csrf(page.request);
  expect(
    (
      await page.request.post("/api/v1/auth/logout", {
        headers: { "X-CSRF-TOKEN": token },
      })
    ).status(),
  ).toBe(204);
  await page.goto("/#/projects/00000000-0000-0000-0000-000000000001");
  await expect(
    page.getByRole("heading", { name: "С возвращением" }),
  ).toBeVisible();
});

test("Ответы реального API соответствуют схемам OpenAPI", async ({
  request,
}) => {
  const email = `contract-${Date.now()}@example.test`;
  await register(request, email, "CUSTOMER");
  await schema(
    await request.post("/api/v1/auth/login", {
      data: { email, password },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Account",
    200,
  );
  await schema(await request.get("/api/v1/auth/me"), "Account", 200);
  const project = await schema(
    await request.post("/api/v1/projects", {
      data: { name: "Контрактный дом", address: "Тестовый адрес" },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Project",
    201,
  );
  await schema(
    await request.get(`/api/v1/projects/${project.id}`),
    "Project",
    200,
  );
  const list = await request.get("/api/v1/projects");
  expect(list.status()).toBe(200);
  const validate = ajv.compile({
    type: "array",
    items: { $ref: "contract#/components/schemas/Project" },
  });
  expect(validate(await list.json())).toBe(true);
  await schema(
    await request.put(`/api/v1/projects/${project.id}/contractor`, {
      data: { email: "missing@example.test" },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Problem",
    422,
  );
  await schema(
    await request.post("/api/v1/projects", {
      data: { name: " ", address: "Адрес" },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Problem",
    400,
  );
});

test("Прежний аккаунт заполняет имя без потери формы и возвращается к созданию дома", async ({
  page,
  request,
}, info) => {
  const email = `legacy-profile-${info.project.name}-${Date.now()}@example.test`;
  await schema(
    await request.post("/api/v1/auth/register", {
      data: { email, password, role: "CUSTOMER" },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Account",
    201,
  );
  await page.goto("/");
  await login(page, email);
  await page
    .getByRole("link", { name: "+ Создать карточку", exact: true })
    .click();
  await page
    .getByLabel("Название дома", { exact: true })
    .fill("Дом с сохранённой формой");
  await page
    .getByLabel("Адрес дома", { exact: true })
    .fill("с. Озёрное, участок 21");
  await page
    .getByRole("button", { name: "Создать карточку", exact: true })
    .click();
  const dialog = page.getByRole("dialog", {
    name: "Заполните имя",
    exact: true,
  });
  await expect(dialog.getByLabel("Ваше имя", { exact: true })).toBeFocused();
  await page.keyboard.press("Escape");
  await expect(dialog).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Создать карточку", exact: true }),
  ).toBeFocused();
  await page
    .getByRole("button", { name: "Создать карточку", exact: true })
    .click();
  await dialog.getByLabel("Ваше имя", { exact: true }).fill("Иван");
  await dialog
    .getByRole("button", { name: "Сохранить имя", exact: true })
    .click();
  await expect(
    dialog.getByText("Имя сохранено", { exact: true }),
  ).toBeVisible();
  await accessible(page);
  await dialog
    .getByRole("button", { name: "Вернуться к работе", exact: true })
    .click();
  await expect(page.getByLabel("Название дома", { exact: true })).toHaveValue(
    "Дом с сохранённой формой",
  );
  await expect(page.getByLabel("Адрес дома", { exact: true })).toHaveValue(
    "с. Озёрное, участок 21",
  );
  await page
    .getByRole("button", { name: "Создать карточку", exact: true })
    .click();
  await expect(
    page.getByRole("heading", {
      name: "Дом с сохранённой формой",
      exact: true,
    }),
  ).toBeVisible();
});
