import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
import {
  register,
  login,
  csrf,
  password,
  legacyFixture,
  accessible,
  schema,
} from "./support";

for (const financing of ["OWN_FUNDS", "RSHB_RURAL_MORTGAGE"] as const) {
  test(
    financing === "OWN_FUNDS"
      ? "Собственные средства: приёмка завершает дом без банковского блока"
      : "Демо-дело подключается после приёмки без изменения исходной заявки",
    async ({ page, request }, info) => {
      test.setTimeout(90000);
      const suffix = `${info.project.name}-${financing}-${Date.now()}`;
      const owner = `accept-owner-${suffix}@example.test`,
        builder = `accept-builder-${suffix}@example.test`;
      await register(request, owner, "CUSTOMER", { displayName: "Иван" });
      await register(request, builder, "CONTRACTOR", {
        displayName: "Алексей Орлов",
      });
      async function signIn(email: string) {
        const response = await request.post("/api/v1/auth/login", {
          data: { email, password },
          headers: { "X-CSRF-TOKEN": await csrf(request) },
        });
        expect(response.ok(), await response.text()).toBe(true);
      }
      async function command(path: string, data: unknown, method = "POST") {
        const response = await request.fetch("/api/v1" + path, {
          method,
          data,
          headers: { "X-CSRF-TOKEN": await csrf(request) },
        });
        expect(response.ok(), await response.text()).toBe(true);
        return response.json();
      }
      await signIn(owner);
      const project = await command("/projects", {
        name:
          financing === "OWN_FUNDS"
            ? "Дом на собственные средства"
            : "Дом с поздним демо-делом",
        address: "с. Озёрное, участок 25",
      });
      await legacyFixture(project.id);
      const root = `/projects/${project.id}`;
      await command(root + "/contractor", { email: builder }, "PUT");
      await command(
        root + "/request",
        {
          version: 0,
          description: "Одноэтажный дом для семьи",
          technology: "Деревянный каркас",
          materials: "Сухая древесина",
          areaM2: 80,
          floors: 1,
          completionScope: "Тёплый контур",
          desiredBudget: 3000000,
          desiredStart: "2026-12-01",
          desiredFinish: "2027-03-01",
          financingSource: financing,
        },
        "PATCH",
      );
      let stage = await command(root + "/stages", {
        name: "Фундамент",
        workType: "FOUNDATION",
      });
      await signIn(builder);
      stage = await command(root + "/stages/" + stage.id + "/start", {
        version: stage.version,
      });
      const illustration = await page.context().newPage();
      await illustration.setContent(
        readFileSync("e2e/fixtures/house-plan.svg", "utf8"),
      );
      const image = await illustration.locator("svg").screenshot();
      await illustration.close();
      const uploaded = await request.post("/api/v1" + root + "/attachments", {
        multipart: {
          file: {
            name: "foundation.png",
            mimeType: "image/png",
            buffer: image,
          },
          purpose: "JOURNAL",
          stageId: stage.id,
          caption: "Схема основания — демоиллюстрация",
        },
        headers: {
          "X-CSRF-TOKEN": await csrf(request),
          "Idempotency-Key": crypto.randomUUID(),
        },
      });
      expect(uploaded.status(), await uploaded.text()).toBe(200);
      const file = await uploaded.json();
      const entry = await command(root + "/stages/" + stage.id + "/journal", {
        kind: "NOTE",
        text: "Фундамент выполнен, геометрия проверена",
        photos: [],
        attachmentIds: [file.id],
        idempotencyKey: crypto.randomUUID(),
      });
      stage = await command(root + "/stages/" + stage.id + "/complete", {
        version: stage.version,
      });
      const submission = await command(
        root + "/stages/" + stage.id + "/acceptance",
        {
          expectedStageVersion: stage.version,
          summary: "Фундамент готов к проверке заказчиком",
          entryIds: [entry.id],
          idempotencyKey: crypto.randomUUID(),
        },
      );
      await page.goto("/");
      await login(page, owner);
      await page.getByRole("link").filter({ hasText: project.name }).click();
      await page.getByRole("link", { name: "Фундамент", exact: true }).click();
      await page
        .getByRole("button", { name: "Принять этап", exact: true })
        .click();
      const panel = page.getByRole("region", {
        name: "Приёмка этапа",
        exact: true,
      });
      await expect(
        panel.getByText("Принят заказчиком", { exact: true }).first(),
      ).toBeVisible();
      if (financing === "OWN_FUNDS") {
        await expect(
          page.getByRole("heading", {
            name: "Подтверждение для банка",
            exact: true,
          }),
        ).toHaveCount(0);
        await page
          .getByRole("link", { name: "← Карточка дома", exact: true })
          .click();
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
          page.getByRole("heading", {
            name: "Строительство завершено",
            exact: true,
          }),
        ).toBeVisible();
      } else {
        await page
          .getByText("Подключить демо-дело", { exact: true })
          .first()
          .click();
        const connect = page.getByRole("button", {
          name: "Подключить демо-дело",
          exact: true,
        });
        await expect(connect).toBeDisabled();
        await page
          .getByLabel("Разрешаю подключить синтетическое кредитное дело", {
            exact: true,
          })
          .check();
        await connect.click();
        await expect(
          page.getByRole("button", {
            name: "Проверить и отправить",
            exact: true,
          }),
        ).toBeVisible();
        const original = await command(root + "/request", undefined, "GET");
        expect(original.state).toBe("DRAFT");
        expect(original.financingSource).toBe(financing);
      }
      await accessible(page);
      await expect(
        page.getByRole("img", { name: "РСХБ Цифра", exact: true }),
      ).toHaveCount(0);
      expect(submission.id).toBeTruthy();
      const stageRoot = `/api/v1${root}/stages/${stage.id}`;
      await schema(
        await page.request.get(stageRoot + "/acceptance"),
        "AcceptancePage",
        200,
      );
      await schema(
        await page.request.get("/api/v1" + root + "/completion"),
        "Completion",
        200,
      );
      if (financing === "RSHB_RURAL_MORTGAGE") {
        await schema(
          await page.request.get(stageRoot + "/bank-confirmation"),
          "BankConfirmation",
          200,
        );
      }
    },
  );
}
