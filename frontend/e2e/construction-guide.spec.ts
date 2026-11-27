import { test, expect } from "@playwright/test";
import { readFile, writeFile } from "node:fs/promises";
import {
  createGuideDemo,
  guideClient,
  signInGuide,
  publishGuidePhoto,
  submitGuideStage,
} from "../scripts/seed-construction-guide.mjs";
import { login, accessible, schema } from "./support";

test.use({ actionTimeout: 10000 });

test("Контрольные точки: действия ролей, закрытые фото, возврат, приёмка и завершение", async ({
  page,
}, info) => {
  test.setTimeout(180000);
  const demo = await createGuideDemo({
    baseURL: process.env.BASE_URL ?? "http://localhost:8088",
  });
  await writeFile(
    `../output/construction-guide-${info.project.name}.json`,
    JSON.stringify(demo, null, 2),
  );
  const a = demo.projects[0],
    b = demo.projects[1],
    f = a.stages[0],
    second = a.stages[1],
    u = b.stages[0];
  const owner = await guideClient(demo.baseURL),
    builder = await guideClient(demo.baseURL);
  await signInGuide(owner, demo.ownerEmail);
  await signInGuide(builder, demo.builderEmail);
  const desktop = info.project.name === "desktop";
  async function capture(state: string, both = false) {
    await expect(
      page.getByText("Обновляем действия…", { exact: true }),
    ).not.toBeVisible();
    if (!desktop && !both) return;
    await page.screenshot({
      path: `../docs/screenshots/control-gates-${state}-${info.project.name}.png`,
      fullPage: true,
    });
  }
  async function as(email: string) {
    if (
      await page.getByRole("button", { name: "Выйти", exact: true }).isVisible()
    ) {
      await page.getByRole("button", { name: "Выйти", exact: true }).click();
      await expect(page.getByLabel("Email", { exact: true })).toBeVisible();
    } else await page.goto("/");
    await login(page, email);
  }
  async function card(id: string) {
    if (page.url().endsWith(`/#/projects/${id}`)) await page.reload();
    else await page.goto(`/#/projects/${id}`);
    const panel = page.getByRole("region", {
      name: "Контрольные точки",
      exact: true,
    });
    await expect(panel).toHaveAttribute("aria-busy", "false");
    return panel;
  }
  try {
    await page.goto("/");
    await login(page, demo.ownerEmail);
    // Проверяющий начинает со списка: индикатор сам ведёт к нужной приёмке.
    await page.getByRole("link", { name: /Требуется действие:/ }).click();
    await expect(
      page.getByRole("heading", { name: "Приёмка этапа", exact: true }),
    ).toBeFocused();
    await capture("acceptance", true);
    await schema(
      await page.request.get(`/api/v1/projects/${a.id}/control-gates`),
      "ControlGatesView",
      200,
    );
    await schema(
      await page.request.get(`/api/v1/projects/${a.id}/construction-profile`),
      "ConstructionProfile",
      200,
    );
    await schema(
      await page.request.get("/api/v1/construction-actions"),
      "ConstructionActionsView",
      200,
    );
    let panel = await card(a.id);
    await expect(
      page.getByRole("heading", { name: "Паспорт дома", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByText("128 м²", { exact: false }).first(),
    ).toBeVisible();
    await capture("project", true);
    await accessible(page);
    await panel
      .getByRole("link", { name: "Проверить сдачу этапа", exact: true })
      .click();
    const acceptance = page.getByRole("region", {
      name: "Приёмка этапа",
      exact: true,
    });
    await acceptance
      .getByRole("button", { name: "Вернуть с замечаниями", exact: true })
      .click();
    await page
      .getByLabel("Замечания к этапу", { exact: true })
      .fill("Демонстрация: уточните подпись подтверждения.");
    await page
      .getByRole("button", { name: "Отправить замечания", exact: true })
      .click();
    await expect(
      acceptance.getByText("Нужны исправления", { exact: true }).first(),
    ).toBeVisible();
    await expect(
      page.getByText("Обновляем действия…", { exact: true }),
    ).not.toBeVisible();
    await expect(
      page.getByRole("link", { name: /Требуется действие:/ }),
    ).toHaveCount(0);
    await as(demo.builderEmail);
    panel = await card(a.id);
    await expect(
      panel.getByRole("link", {
        name: "Исправить замечания и сдать повторно",
        exact: true,
      }),
    ).toBeVisible();
    await capture("returned");
    await panel
      .getByRole("link", {
        name: "Исправить замечания и сдать повторно",
        exact: true,
      })
      .click();
    await page
      .getByRole("button", { name: "Сдать повторно", exact: true })
      .click();
    await page
      .getByLabel("Итог выполненного этапа", { exact: true })
      .fill(
        "Синтетический пример: пояснение уточнено, исправления подготовлены.",
      );
    await page
      .getByRole("checkbox", {
        name: /Синтетический пример: подтверждения подготовлены/,
      })
      .check();
    await page
      .getByRole("button", { name: "Отправить на приёмку", exact: true })
      .click();
    await expect(
      page
        .getByRole("region", { name: "Приёмка этапа", exact: true })
        .getByText("На приёмке", { exact: true })
        .first(),
    ).toBeVisible();
    // Реальная публикация с загрузкой через UI снимает действие без повторного входа.
    panel = await card(b.id);
    await capture("contractor");
    await panel
      .getByRole("link", { name: "Добавить подтверждения", exact: true })
      .click();
    await expect(
      page.getByRole("heading", { name: "Ход работ и вопросы", exact: true }),
    ).toBeFocused();
    await page
      .getByRole("button", { name: "Добавить запись", exact: true })
      .click();
    const art = await page.context().newPage();
    await art.setContent(
      await readFile("e2e/fixtures/construction-utilities.svg", "utf8"),
    );
    const image = await art.locator("svg").screenshot();
    await art.close();
    await page
      .getByLabel("Что сделано", { exact: true })
      .fill(
        "Синтетический пример: инженерные сети для демонстрации публикации.",
      );
    await page
      .getByLabel("Добавить фото или PDF", { exact: true })
      .setInputFiles({
        name: "utilities.png",
        mimeType: "image/png",
        buffer: image,
      });
    await page
      .getByLabel("Подпись загруженной фотографии", { exact: true })
      .fill("Синтетическая иллюстрация, не подтверждение качества.");
    await page
      .getByRole("button", { name: "Опубликовать", exact: true })
      .click();
    await expect(
      page.getByText("Запись опубликована", { exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("link", { name: /Требуется действие:/ }),
    ).toContainText("1 объект");
    panel = await card(b.id);
    await panel.getByText("Все контрольные точки", { exact: true }).click();
    await expect(
      panel.getByText(/Подтверждения добавлены/).first(),
    ).toBeVisible();
    await capture("evidence");
    // Второй фундамент того же вида остаётся отдельной точкой и проходит штатный цикл.
    await builder.send(`/projects/${a.id}/stages/${second.id}/start`, "POST", {
      version: second.version,
    });
    const entry = await publishGuidePhoto(builder, a.id, second.id, image);
    const current = await builder.send(`/projects/${a.id}/stages/${second.id}`);
    await builder.send(
      `/projects/${a.id}/stages/${second.id}/complete`,
      "POST",
      { version: current.version },
    );
    panel = await card(a.id);
    await capture("submission");
    await submitGuideStage(builder, a.id, second.id, entry);
    await as(demo.ownerEmail);
    await page.getByRole("link", { name: /Требуется действие:/ }).click();
    await page
      .getByRole("button", { name: "Принять этап", exact: true })
      .click();
    await expect(
      page
        .getByRole("region", { name: "Приёмка этапа", exact: true })
        .getByText("Принят заказчиком", { exact: true })
        .first(),
    ).toBeVisible();
    panel = await card(a.id);
    await panel.getByText("Все контрольные точки", { exact: true }).click();
    await capture("accepted");
    await panel
      .getByRole("link", { name: "Проверить сдачу этапа", exact: true })
      .first()
      .click();
    await page
      .getByRole("button", { name: "Принять этап", exact: true })
      .click();
    await expect(
      page
        .getByRole("region", { name: "Приёмка этапа", exact: true })
        .getByText("Принят заказчиком", { exact: true })
        .first(),
    ).toBeVisible();
    panel = await card(a.id);
    await capture("handover");
    await panel
      .getByRole("link", { name: "Подтвердить завершение дома", exact: true })
      .click();
    await expect(
      page.getByRole("heading", {
        name: "Готовность строительства",
        exact: true,
      }),
    ).toBeFocused();
    await page
      .getByRole("button", {
        name: "Подтвердить завершение строительства",
        exact: true,
      })
      .click();
    await page
      .getByRole("button", { name: "Подтверждаю завершение", exact: true })
      .click();
    await expect(
      page.getByText("Строительство завершено", { exact: true }).first(),
    ).toBeVisible();
    panel = await card(a.id);
    await capture("completed");
    await accessible(page);
    await card(demo.emptyProject.id);
    await capture("empty");
    await page.route("**/api/v1/projects/*/control-gates", (route) =>
      route.fulfill({
        status: 500,
        contentType: "application/problem+json",
        body: JSON.stringify({
          type: "about:blank",
          title: "Синтетический временный сбой",
          status: 500,
          code: "GUIDANCE_READ_FAILED",
        }),
      }),
    );
    await page.goto(`/#/projects/${a.id}`);
    await expect(page.getByText(/Не удалось обновить сведения/)).toBeVisible();
    await capture("error");
    await page.unroute("**/api/v1/projects/*/control-gates");
    await page
      .getByRole("button", { name: "Повторить обновление" })
      .first()
      .click();
    await expect(page.getByText(/Синтетический временный сбой/)).toHaveCount(0);
    await as(demo.lawyerEmail);
    panel = await card(b.id);
    await expect(
      panel.getByRole("link", { name: "Добавить подтверждения", exact: true }),
    ).toHaveCount(0);
    await capture("readonly");
    const hidden = await page.request.get(
      `/api/v1/projects/${demo.emptyProject.id}/control-gates`,
    );
    await schema(hidden, "Problem", 404);
  } finally {
    await owner.context.dispose();
    await builder.context.dispose();
  }
});
