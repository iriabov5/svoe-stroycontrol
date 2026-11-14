import { expect } from "@playwright/test";
import type { APIRequestContext, APIResponse, Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { Ajv2020 } from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { parse } from "yaml";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";

const contract = parse(readFileSync(resolve("../api/openapi.yaml"), "utf8"));
export const ajv = new Ajv2020({ strict: false, allowUnionTypes: true });
addFormats(ajv);
ajv.addSchema({ $id: "contract", components: contract.components });
export const password = "example-password-123";

export async function schema(
  response: APIResponse,
  name: string,
  status: number,
) {
  expect(response.status(), await response.text()).toBe(status);
  const data = await response.json();
  const validate = ajv.compile({
    $ref: `contract#/components/schemas/${name}`,
  });
  expect(validate(data), JSON.stringify(validate.errors)).toBe(true);
  expect(response.headers()["cache-control"]).toContain("no-store");
  return data;
}
export async function csrf(request: APIRequestContext) {
  return (
    await schema(await request.get("/api/v1/auth/csrf"), "CsrfToken", 200)
  ).token as string;
}
export async function register(
  request: APIRequestContext,
  email: string,
  role: string,
  options: {
    displayName?: string;
    organization?: boolean;
    organizationName?: string;
  } = {},
) {
  await schema(
    await request.post("/api/v1/auth/register", {
      data: {
        email,
        password,
        role,
        displayName:
          options.displayName ??
          (role === "CUSTOMER" ? "Иван" : "Сергей Иванов"),
      },
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    }),
    "Account",
    201,
  );
  if (role === "CONTRACTOR" && options.organization !== false) {
    await schema(
      await request.post("/api/v1/auth/login", {
        data: { email, password },
        headers: { "X-CSRF-TOKEN": await csrf(request) },
      }),
      "Account",
      200,
    );
    const digits = String(100000000 + (Date.now() % 800000000));
    const weights = [2, 4, 10, 3, 5, 9, 4, 6, 8];
    const check =
      (weights.reduce((sum, w, i) => sum + Number(digits[i]) * w, 0) % 11) % 10;
    await schema(
      await request.post("/api/v1/organizations", {
        data: {
          type: "LEGAL_ENTITY",
          displayName: options.organizationName ?? "ООО «Тёплый дом»",
          legalName: "Тестовая строительная организация",
          inn: digits + check,
          address: "г. Тестовый, ул. Строителей, 8",
          idempotencyKey: crypto.randomUUID(),
        },
        headers: { "X-CSRF-TOKEN": await csrf(request) },
      }),
      "Organization",
      201,
    );
    await request.post("/api/v1/auth/logout", {
      headers: { "X-CSRF-TOKEN": await csrf(request) },
    });
  }
}
export async function login(page: Page, email: string) {
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Пароль", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Войти", exact: true }).click();
  await expect(
    page.getByRole("heading", { name: "Мои объекты", exact: true }),
  ).toBeVisible();
}
export async function accessible(page: Page) {
  const result = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(result.violations).toEqual([]);
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true);
}

// Прежние сценарии проверяют совместимость с проектами, существовавшими до V3.
export async function legacyFixture(id: string) {
  if (!/^[0-9a-f-]{36}$/.test(id)) throw new Error("Invalid fixture ID");
  const { execFileSync } = await import("node:child_process");
  execFileSync(
    "docker",
    [
      "compose",
      "exec",
      "-T",
      "postgres",
      "psql",
      "-U",
      "stroycontrol",
      "-d",
      "stroycontrol",
      "-c",
      `update house_projects set process_mode='LEGACY' where id='${id}'`,
    ],
    { cwd: ".." },
  );
}
