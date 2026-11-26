import { readFile } from "node:fs/promises";
import { test, expect } from "@playwright/test";
import { register, login, accessible, schema, csrf } from "./support";

test("Заявка, демо-ипотека, смета и договор открывают выполнение работ", async ({
  page,
  request,
}, info) => {
  test.setTimeout(120000);
  await page.route("https://demo-images.example.test/house-plan.svg", (route) =>
    route.fulfill({
      path: "e2e/fixtures/house-plan.svg",
      contentType: "image/svg+xml",
    }),
  );
  const suffix = `${info.project.name}-${Date.now()}`;
  const owner = `preparation-owner-${suffix}@example.test`,
    builder = `preparation-builder-${suffix}@example.test`;
  await register(request, owner, "CUSTOMER");
  await register(request, builder, "CONTRACTOR");
  await page.goto("/");
  await login(page, owner);
  await page
    .getByRole("link", { name: "+ Создать карточку", exact: true })
    .click();
  await page.getByLabel("Название дома").fill("Каркасный дом у озера");
  await page.getByLabel("Адрес дома").fill("с. Озёрное, участок 8");
  await page
    .getByRole("button", { name: "Создать карточку", exact: true })
    .click();
  await page.getByLabel("Email подрядчика").fill(builder);
  await page
    .getByRole("button", { name: "Назначить подрядчика", exact: true })
    .click();
  await page.getByRole("button", { name: "Подтвердить назначение" }).click();
  await page
    .getByLabel("Какой дом вы хотите")
    .fill("Одноэтажный каркасный дом для семьи, две спальни и кухня-гостиная.");
  await page
    .getByLabel("Технология строительства", { exact: true })
    .fill("Деревянный каркас");
  await page
    .getByLabel("Желаемые материалы", { exact: true })
    .fill("Камерная сушка, утеплитель 200 мм");
  await page.getByLabel("Площадь, м²", { exact: true }).fill("80");
  await page.getByLabel("Этажность", { exact: true }).fill("1");
  await page.getByLabel("Комплектация", { exact: true }).fill("Тёплый контур");
  await page.getByLabel("Желаемый бюджет, ₽", { exact: true }).fill("3000000");
  await page
    .getByLabel("Кадастровый номер участка", { exact: true })
    .fill("50:12:1234567:89");
  await page.getByLabel("Желаемое начало строительства").fill("2026-12-01");
  await page.getByLabel("Желаемое завершение строительства").fill("2027-03-01");
  await page.getByRole("radio", { name: /Сельская ипотека/ }).check();
  await page
    .getByRole("button", { name: "Сохранить черновик", exact: true })
    .click();
  await expect(
    page.getByText("Черновик заявки сохранён", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Подключить Интернет-банк РСХБ", exact: true })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(
    page.getByRole("button", {
      name: "Подключить Интернет-банк РСХБ",
      exact: true,
    }),
  ).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.getByRole("dialog")).toBeVisible();
  await accessible(page);
  await page.getByRole("dialog").screenshot({
    path: `../docs/screenshots/bank-dialog-${info.project.name}.png`,
  });
  await page.getByLabel("Показать демонстрационные сведения о кредите").check();
  await page.getByRole("button", { name: "Продолжить в деморежиме" }).click();
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(
    page.getByText("Одобрено · демоданные", { exact: true }),
  ).toBeVisible();
  await page.screenshot({
    path: `../docs/screenshots/request-${info.project.name}.png`,
    fullPage: true,
  });
  await accessible(page);
  await page
    .getByRole("button", { name: "Отправить заявку подрядчику", exact: true })
    .click();
  await expect(
    page.getByText("Передана подрядчику", { exact: true }),
  ).toBeVisible();
  const homeUrl = page.url();
  const projectId = homeUrl.split("/projects/")[1].split("?")[0];
  await schema(
    await page.request.get(`/api/v1/projects/${projectId}/request`),
    "ConstructionRequest",
    200,
  );
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  await expect(
    page.getByRole("heading", { name: "Сведения об ипотеке", exact: true }),
  ).toHaveCount(0);
  await page
    .getByRole("button", { name: "Подготовить предложение", exact: true })
    .click();
  await page
    .getByLabel("Смета: работы, материалы и цены", { exact: true })
    .fill(
      "Фундамент: работы — 150 000 ₽, материалы — 250 000 ₽.\nКаркас, кровля, утепление, окна и двери — 2 400 000 ₽.",
    );
  await page.getByLabel("Общая стоимость, ₽", { exact: true }).fill("2800000");
  await page
    .getByLabel("Условия и исключения", { exact: true })
    .fill("Инженерные сети и внутренняя отделка не входят.");
  const svg = await readFile("e2e/fixtures/house-plan.svg", "utf8");
  const pngBase64 = await page.evaluate(async (source) => {
    const url = URL.createObjectURL(
      new Blob([source], { type: "image/svg+xml" }),
    );
    try {
      const image = new Image();
      image.src = url;
      await image.decode();
      const canvas = document.createElement("canvas");
      canvas.width = image.naturalWidth;
      canvas.height = image.naturalHeight;
      canvas.getContext("2d")!.drawImage(image, 0, 0);
      return canvas.toDataURL("image/png").split(",")[1];
    } finally {
      URL.revokeObjectURL(url);
    }
  }, svg);
  const planPng = Buffer.from(pngBase64, "base64");
  await page.getByLabel("Добавить изображения", { exact: true }).setInputFiles({
    name: "house-plan.png",
    mimeType: "image/png",
    buffer: planPng,
  });
  await page
    .getByLabel("Подпись изображения", { exact: true })
    .fill("Демонстрационная планировка 8 × 10 м");
  await page
    .getByRole("button", {
      name: "Открыть изображение: Демонстрационная планировка 8 × 10 м",
      exact: true,
    })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("dialog").screenshot({
    path: `../docs/screenshots/proposal-preview-${info.project.name}.png`,
  });
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page
    .locator("form")
    .filter({
      has: page.getByLabel("Смета: работы, материалы и цены", { exact: true }),
    })
    .screenshot({
      path: `../docs/screenshots/proposal-upload-${info.project.name}.png`,
    });
  await page
    .getByRole("button", { name: "Добавить этап в предложение", exact: true })
    .click();
  await page.getByLabel("Название этапа", { exact: true }).fill("Фундамент");
  await page
    .getByLabel("Ожидаемый результат этапа", { exact: true })
    .fill("Основание готово к монтажу каркаса");
  await page.getByLabel("Плановое начало", { exact: true }).fill("2026-12-01");
  await page
    .getByLabel("Плановое завершение", { exact: true })
    .fill("2026-12-10");
  await page
    .getByRole("button", { name: "Сохранить предложение", exact: true })
    .click();
  await page
    .getByRole("button", {
      name: "Отправить предложение на согласование",
      exact: true,
    })
    .click();
  await expect(
    page.getByText("Работы, материалы и цены", { exact: true }),
  ).toBeVisible();
  const planImage = page.getByRole("img", {
    name: "Демонстрационная планировка 8 × 10 м",
    exact: true,
  });
  await planImage.scrollIntoViewIfNeeded();
  await expect
    .poll(() =>
      planImage.evaluate(
        (image) =>
          (image as HTMLImageElement).complete &&
          (image as HTMLImageElement).naturalWidth > 0,
      ),
    )
    .toBe(true);
  const uploadedPlan = await planImage.screenshot();
  await page.screenshot({
    path: `../docs/screenshots/proposal-${info.project.name}.png`,
    fullPage: true,
  });
  await accessible(page);
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  await page
    .getByRole("button", {
      name: "Открыть изображение: Демонстрационная планировка 8 × 10 м",
      exact: true,
    })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await page
    .getByRole("button", { name: "Согласовать предложение", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Подтвердить согласование", exact: true })
    .click();
  await expect(
    page.getByText(
      "План согласован. Начать работы можно после согласования договора подряда.",
    ),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  await page
    .getByRole("button", { name: "Подготовить договор", exact: true })
    .click();
  await page
    .getByLabel("Текст договора подряда", { exact: true })
    .fill(
      "Демонстрационный договор подряда.\nЗаказчик: Анна. Подрядчик: Иван.\nОбъект: каркасный дом у озера, участок 8.\nПредмет: строительство тёплого контура по согласованной смете.\nЦена: 2 800 000 ₽. Срок: 01.12.2026 — 01.03.2027.\nПорядок оплаты и сдачи работ согласуется сторонами.",
    );
  await page
    .getByRole("button", { name: "Сохранить договор", exact: true })
    .click();
  await page.getByLabel("Условия проверены, отправить заказчику").check();
  await page
    .getByRole("button", {
      name: "Отправить договор на согласование",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  const contractPanel = page
    .getByRole("heading", { name: "Договор подряда", exact: true })
    .locator("..")
    .locator("..")
    .locator("..");
  await contractPanel
    .getByLabel("Замечания для подрядчика")
    .fill("Уточнить порядок оплаты по этапам");
  await contractPanel
    .getByRole("button", { name: "Вернуть на уточнение" })
    .click();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  await page
    .getByRole("button", { name: "Подготовить новую версию", exact: true })
    .click();
  await page
    .getByLabel("Текст договора подряда", { exact: true })
    .fill(
      "Демонстрационный договор. Цена и график — по согласованному предложению. Оплата по завершении этапов. Условия уточнены по замечанию заказчика.",
    );
  await page
    .getByRole("button", { name: "Сохранить договор", exact: true })
    .click();
  await page.getByLabel("Условия проверены, отправить заказчику").check();
  await page
    .getByRole("button", {
      name: "Отправить договор на согласование",
      exact: true,
    })
    .click();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  await page
    .getByRole("button", { name: "Согласовать договор", exact: true })
    .click();
  await page
    .getByRole("button", { name: "Подтвердить согласование", exact: true })
    .click();
  await page.screenshot({
    path: `../docs/screenshots/contract-${info.project.name}.png`,
    fullPage: true,
  });
  await accessible(page);
  const { ajv } = await import("./support");
  for (const [collection, name] of [
    ["proposals", "ConstructionProposal"],
    ["contracts", "ConstructionContract"],
  ]) {
    const response = await page.request.get(
      `/api/v1/projects/${projectId}/${collection}`,
    );
    expect(response.status()).toBe(200);
    const validate = ajv.compile({
      $ref: "contract#/components/schemas/" + name,
    });
    for (const d of await response.json())
      expect(validate(d), JSON.stringify(validate.errors)).toBe(true);
  }
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page
    .getByRole("link")
    .filter({ hasText: "Каркасный дом у озера" })
    .click();
  await page.getByRole("link", { name: "Фундамент", exact: true }).click();
  await page
    .getByRole("button", { name: "Начать работы", exact: true })
    .click();
  await expect(page.getByText("В работе", { exact: true })).toBeVisible();
  await page
    .getByRole("button", { name: "Добавить запись", exact: true })
    .click();
  await page
    .getByLabel("Что сделано", { exact: true })
    .fill("Смонтирована опалубка и проверено армирование.");
  await page.getByRole("button", { name: "Добавить фото по ссылке" }).click();
  await page
    .getByLabel("Ссылка на фото", { exact: true })
    .fill("https://demo-images.example.test/house-plan.svg");
  await page
    .getByLabel("Что на фотографии")
    .fill("План основания — синтетическая иллюстрация");
  await page
    .getByLabel("Добавить фото или PDF", { exact: true })
    .setInputFiles([
      { name: "foundation.png", mimeType: "image/png", buffer: uploadedPlan },
      {
        name: "passport.pdf",
        mimeType: "application/pdf",
        buffer: await readFile("e2e/fixtures/material-passport.pdf"),
      },
    ]);
  await page
    .getByLabel("Подпись загруженной фотографии", { exact: true })
    .fill("Схема основания — закрытое вложение");
  await page
    .getByLabel("Пояснение к документу", { exact: true })
    .fill("Демонстрационный паспорт материала");
  await page.getByRole("button", { name: "Опубликовать", exact: true }).click();
  await expect(
    page.getByText("Запись опубликована", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: /passport.pdf/ })).toBeVisible();
  await page
    .getByRole("button", {
      name: "Открыть фото: План основания — синтетическая иллюстрация",
    })
    .click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await page
    .getByRole("button", { name: "Добавить запись", exact: true })
    .click();
  await page.getByLabel("Тип записи").selectOption("QUESTION");
  await page
    .getByLabel("Вопрос заказчику")
    .fill("С какой стороны оставить проход к дому?");
  await page
    .getByRole("button", { name: "Отправить вопрос", exact: true })
    .click();
  await expect(
    page.getByText("Запись опубликована", { exact: true }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Добавить запись", exact: true })
    .click();
  await page.getByLabel("Тип записи").selectOption("APPROVAL");
  await page
    .getByLabel("Что нужно согласовать")
    .fill("Согласуйте размещение временного ограждения.");
  await page
    .getByRole("button", { name: "Запросить согласование", exact: true })
    .click();
  await expect(
    page.getByText("Запись опубликована", { exact: true }),
  ).toBeVisible();
  await schema(
    await page.request.get(
      `/api/v1/projects/${projectId}/stages/${page.url().split("/stages/")[1]}/journal`,
    ),
    "JournalPage",
    200,
  );
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/stage-journal-${info.project.name}.png`,
    fullPage: true,
  });
  await page
    .getByRole("button", { name: "Завершить работы", exact: true })
    .click();
  await expect(
    page.getByText("Работы завершены", { exact: true }),
  ).toBeVisible();
  const stageUrl = page.url();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page.goto(stageUrl);
  const question = page.getByRole("article", { name: "Вопрос", exact: true });
  await question
    .getByLabel("Ваш ответ")
    .fill("Оставьте проход со стороны дороги.");
  await question.getByRole("button", { name: "Ответить", exact: true }).click();
  await expect(
    question.getByText("Оставьте проход со стороны дороги."),
  ).toBeVisible();
  const approval = page.getByRole("article", {
    name: "На согласование",
    exact: true,
  });
  await approval
    .getByRole("button", { name: "Не согласовать", exact: true })
    .click();
  await expect(approval.getByLabel("Комментарий к решению")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
  await approval
    .getByLabel("Комментарий к решению")
    .fill("Перенесите ограждение ближе к дороге.");
  await approval
    .getByRole("button", { name: "Не согласовать", exact: true })
    .click();
  await expect(
    approval.getByText("Перенесите ограждение ближе к дороге."),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Требуют ответа", exact: true })
    .click();
  await expect(page.getByText("Всё обсуждено", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Все", exact: true }).click();
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/stage-answers-${info.project.name}.png`,
    fullPage: true,
  });
  await page.reload();
  await expect(
    page.getByText("Оставьте проход со стороны дороги."),
  ).toBeVisible();
  // Сдача и исправление проходят тем же маршрутом, что и первоначальный журнал.
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page.goto(stageUrl);
  const acceptance = page.getByRole("region", {
    name: "Приёмка этапа",
    exact: true,
  });
  await acceptance
    .getByRole("button", { name: "Передать на приёмку", exact: true })
    .click();
  await page
    .getByLabel("Итог выполненного этапа", { exact: true })
    .fill("Фундамент выполнен. Армирование и геометрия проверены.");
  await page
    .getByRole("checkbox", {
      name: /Смонтирована опалубка и проверено армирование/,
    })
    .check();
  await page
    .getByRole("button", { name: "Отправить на приёмку", exact: true })
    .click();
  await expect(
    acceptance.getByText("На приёмке", { exact: true }).first(),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page.goto(stageUrl);
  await page
    .getByRole("button", { name: "Вернуть с замечаниями", exact: true })
    .click();
  await page
    .getByLabel("Замечания к этапу", { exact: true })
    .fill("Добавьте крупный план усиления углов фундамента.");
  await page
    .getByRole("button", { name: "Отправить замечания", exact: true })
    .click();
  await expect(
    page.getByText("Что нужно исправить", { exact: true }),
  ).toBeVisible();
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/acceptance-returned-${info.project.name}.png`,
    fullPage: true,
  });
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, builder);
  await page.goto(stageUrl);
  await page
    .getByRole("button", { name: "Добавить запись", exact: true })
    .click();
  await page
    .getByLabel("Что сделано", { exact: true })
    .fill(
      "Усиление углов проверено. Добавлен крупный план по замечанию заказчика.",
    );
  await page
    .getByLabel("Добавить фото или PDF", { exact: true })
    .setInputFiles({
      name: "foundation-fixed.png",
      mimeType: "image/png",
      buffer: uploadedPlan,
    });
  await page
    .getByLabel("Подпись загруженной фотографии", { exact: true })
    .fill("Подтверждение исправления — демоиллюстрация");
  await page.getByRole("button", { name: "Опубликовать", exact: true }).click();
  await expect(
    page
      .getByRole("region", { name: "Ход работ и вопросы", exact: true })
      .getByRole("article", { name: "Ход работ", exact: true })
      .filter({
        hasText:
          "Усиление углов проверено. Добавлен крупный план по замечанию заказчика.",
      }),
  ).toBeVisible();
  await page
    .getByRole("button", { name: "Сдать повторно", exact: true })
    .click();
  await page
    .getByLabel("Итог выполненного этапа", { exact: true })
    .fill("Замечания устранены. Фундамент готов к монтажу каркаса.");
  await page
    .getByRole("checkbox", { name: /Усиление углов проверено/ })
    .check();
  await page
    .getByRole("button", { name: "Отправить на приёмку", exact: true })
    .click();
  await expect(
    acceptance.getByText("На приёмке", { exact: true }).first(),
  ).toBeVisible();
  await page.getByRole("button", { name: "Выйти", exact: true }).click();
  await login(page, owner);
  await page.goto(stageUrl);
  await page.getByRole("button", { name: "Принять этап", exact: true }).click();
  await expect(
    page.getByText("Принят заказчиком", { exact: true }).first(),
  ).toBeVisible();
  const bankPanel = page.getByRole("region", {
    name: "Подтверждение для банка",
    exact: true,
  });
  await expect(
    bankPanel.getByRole("button", {
      name: "Проверить и отправить",
      exact: true,
    }),
  ).toBeVisible();
  await acceptance.screenshot({
    path: `../docs/screenshots/acceptance-${info.project.name}.png`,
  });
  await bankPanel
    .getByRole("button", { name: "Проверить и отправить", exact: true })
    .click();
  await expect(
    bankPanel.getByText("Состав подтверждения", { exact: true }),
  ).toBeVisible();
  await accessible(page);
  await bankPanel.screenshot({
    path: `../docs/screenshots/bank-package-${info.project.name}.png`,
  });
  await bankPanel
    .getByRole("button", { name: "Отправить в демо-банк", exact: true })
    .click();
  await expect(
    bankPanel.getByText("Нужно дополнить", { exact: true }).first(),
  ).toBeVisible({ timeout: 20000 });
  await accessible(page);
  await bankPanel.screenshot({
    path: `../docs/screenshots/bank-needs-information-${info.project.name}.png`,
  });
  // В этом демонстрационном проекте один этап: подтверждаем итог независимо от банка.
  await page.goto(homeUrl);
  await page
    .getByRole("button", {
      name: "Подтвердить завершение строительства",
      exact: true,
    })
    .click();
  await expect(
    page.getByRole("button", { name: "Подтверждаю завершение", exact: true }),
  ).toBeFocused();
  await page
    .getByRole("button", { name: "Подтверждаю завершение", exact: true })
    .click();
  await expect(
    page.getByRole("heading", { name: "Строительство завершено", exact: true }),
  ).toBeVisible();
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/construction-completed-${info.project.name}.png`,
    fullPage: true,
  });
  await page.goto(stageUrl);
  await expect(
    page.getByRole("button", { name: "Добавить запись", exact: true }),
  ).toHaveCount(0);
  await bankPanel
    .getByRole("button", { name: "Проверить и отправить", exact: true })
    .click();
  await page
    .getByLabel("Пояснение для демо-банка", { exact: true })
    .fill(
      "Усиление углов проверено. Прикладываем уточнённый паспорт материала.",
    );
  await bankPanel
    .getByLabel("Добавить фото или PDF", { exact: true })
    .setInputFiles({
      name: "clarification.pdf",
      mimeType: "application/pdf",
      buffer: await readFile("e2e/fixtures/material-passport.pdf"),
    });
  await bankPanel
    .getByLabel("Пояснение к документу", { exact: true })
    .fill("Уточнённые сведения по этапу");
  await bankPanel
    .getByRole("button", { name: "Отправить в демо-банк", exact: true })
    .click();
  await expect(
    bankPanel.getByText("Принято демо-банком", { exact: true }).first(),
  ).toBeVisible({ timeout: 20000 });
  await accessible(page);
  await page.screenshot({
    path: `../docs/screenshots/acceptance-bank-complete-${info.project.name}.png`,
    fullPage: true,
  });
});
