import { request, chromium } from "@playwright/test";
import { randomUUID, randomInt } from "node:crypto";
import { readFile, writeFile, mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { resolve, dirname } from "node:path";
import { spawnSync } from "node:child_process";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "../..");
export const demoPassword = "example-password-123";

export async function guideClient(baseURL) {
  const context = await request.newContext({ baseURL });
  return {
    context,
    async send(path, method = "GET", data) {
      const headers = {};
      if (method !== "GET") {
        const csrf = await context.get("/api/v1/auth/csrf");
        if (!csrf.ok()) throw new Error(`CSRF: ${csrf.status()}`);
        headers["X-CSRF-TOKEN"] = (await csrf.json()).token;
      }
      const response = await context.fetch("/api/v1" + path, {
        method,
        data,
        headers,
      });
      if (!response.ok())
        throw new Error(
          `${method} ${path}: ${response.status()} ${await response.text()}`,
        );
      return response.status() === 204 ? null : response.json();
    },
    async upload(project, stage, buffer) {
      const token = (await (await context.get("/api/v1/auth/csrf")).json())
        .token;
      const response = await context.post(
        `/api/v1/projects/${project}/attachments`,
        {
          multipart: {
            file: {
              name: "synthetic-construction.png",
              mimeType: "image/png",
              buffer,
            },
            purpose: "JOURNAL",
            stageId: stage,
            caption:
              "Синтетический пример строительных работ. Не подтверждение качества.",
          },
          headers: { "X-CSRF-TOKEN": token, "Idempotency-Key": randomUUID() },
        },
      );
      if (!response.ok())
        throw new Error(
          `Загрузка изображения: ${response.status()} ${await response.text()}`,
        );
      return response.json();
    },
  };
}
export async function signInGuide(client, email) {
  return client.send("/auth/login", "POST", { email, password: demoPassword });
}
async function registerGuide(client, email, role, displayName) {
  await client.send("/auth/register", "POST", {
    email,
    password: demoPassword,
    role,
    displayName,
  });
  return signInGuide(client, email);
}
export async function publishGuidePhoto(
  client,
  project,
  stage,
  buffer,
  text = "Синтетический пример: подтверждения подготовлены для просмотра сценария.",
) {
  const file = await client.upload(project, stage, buffer);
  return client.send(`/projects/${project}/stages/${stage}/journal`, "POST", {
    kind: "NOTE",
    text,
    photos: [],
    attachmentIds: [file.id],
    sourceEntryId: null,
    idempotencyKey: randomUUID(),
  });
}
export async function submitGuideStage(client, project, stage, entry) {
  const current = await client.send(`/projects/${project}/stages/${stage}`);
  return client.send(
    `/projects/${project}/stages/${stage}/acceptance`,
    "POST",
    {
      expectedStageVersion: current.version,
      summary:
        "Синтетическая демонстрационная сдача. Проверьте приложенные подтверждения.",
      entryIds: [entry.id],
      idempotencyKey: randomUUID(),
    },
  );
}
export async function decideGuideStage(
  client,
  project,
  stage,
  submission,
  decision,
  comment,
) {
  return client.send(
    `/projects/${project}/stages/${stage}/acceptance/${submission.id}/decision`,
    "POST",
    {
      expectedVersion: submission.version,
      decision,
      ...(comment ? { comment } : {}),
      idempotencyKey: randomUUID(),
    },
  );
}

async function prepareProject(owner, builder, email, marker, name, types) {
  const project = await owner.send("/projects", "POST", {
    name: `Демо · ${name} · ${marker}`,
    address: "Синтетический участок, посёлок Берёзки",
  });
  const base = "/projects/" + project.id;
  await owner.send(base + "/contractor", "PUT", { email });
  const wishes = await owner.send(base + "/request", "PATCH", {
    version: 0,
    description: "Демонстрационный дом: параметры и действия синтетические.",
    technology: "Каркас",
    materialsRecommendation: true,
    areaM2: 128,
    floors: 2,
    completionScope: "Тёплый контур",
    desiredBudget: 5000000,
    desiredStart: "2027-04-01",
    desiredFinish: "2027-10-01",
    financingSource: "OWN_FUNDS",
  });
  await owner.send(base + "/request/submit", "POST", {
    version: wishes.version,
  });
  let proposal = await builder.send(base + "/proposals", "POST");
  const stages = types.map((type, i) => ({
    id: randomUUID(),
    name:
      type === "FOUNDATION"
        ? `Фундамент · очередь ${i + 1}`
        : type === "UTILITIES"
          ? "Инженерные сети"
          : "Кровля",
    workType: type,
    expectedResult: "Согласованный демонстрационный результат",
    plannedStart: `2027-0${4 + i}-01`,
    plannedFinish: `2027-0${4 + i}-20`,
  }));
  let path = base + "/proposals/" + proposal.id;
  proposal = await builder.send(path, "PUT", {
    version: proposal.version,
    estimateText: "Синтетическая смета: работы и материалы по плану.",
    termsText: null,
    totalPrice: 5000000,
    images: [],
    stages,
  });
  proposal = await builder.send(path + "/submit", "POST", {
    version: proposal.version,
    contractorConfirmed: true,
  });
  await owner.send(path + "/approve", "POST", {
    version: proposal.version,
    contractorConfirmed: true,
  });
  let contract = await builder.send(base + "/contracts", "POST");
  path = base + "/contracts/" + contract.id;
  contract = await builder.send(path, "PUT", {
    version: contract.version,
    contractText:
      "Демонстрационный договор: стороны, предмет, стоимость, сроки, оплата и порядок приёмки. Не юридический документ.",
  });
  contract = await builder.send(path + "/submit", "POST", {
    version: contract.version,
    contractorConfirmed: true,
  });
  await owner.send(path + "/approve", "POST", {
    version: contract.version,
    contractorConfirmed: true,
  });
  return { ...project, stages: await owner.send(base + "/stages") };
}
export async function createGuideDemo({
  baseURL = "http://localhost:8088",
  setProfiles = true,
  onCheckpoint = async () => {},
} = {}) {
  const marker = `guide-${Date.now().toString(36)}-${randomInt(1000, 9999)}`;
  const ownerEmail = `customer-${marker}@example.test`,
    builderEmail = `builder-${marker}@example.test`,
    lawyerEmail = `lawyer-${marker}@example.test`;
  const owner = await guideClient(baseURL),
    builder = await guideClient(baseURL),
    lawyer = await guideClient(baseURL);
  let browser;
  const manifest = {
    marker,
    baseURL,
    password: demoPassword,
    ownerEmail,
    builderEmail,
    lawyerEmail,
    projects: [],
    step: "registration",
  };
  const checkpoint = async (step) => {
    manifest.step = step;
    await onCheckpoint(manifest);
  };
  try {
    await checkpoint("registration");
    await registerGuide(owner, ownerEmail, "CUSTOMER", "Анна Заказчик");
    await registerGuide(builder, builderEmail, "CONTRACTOR", "Илья Подрядчик");
    await registerGuide(lawyer, lawyerEmail, "CONTRACTOR", "Ольга Юрист");
    const digits = String(randomInt(100000000, 900000000)),
      weights = [2, 4, 10, 3, 5, 9, 4, 6, 8];
    const inn =
      digits +
      ((weights.reduce((sum, w, i) => sum + Number(digits[i]) * w, 0) % 11) %
        10);
    const organization = await builder.send("/organizations", "POST", {
      type: "LEGAL_ENTITY",
      displayName: "ООО «Демо Строй»",
      legalName: "Синтетическая строительная организация",
      inn,
      address: "Синтетический адрес, ул. Строителей, 1",
      idempotencyKey: randomUUID(),
    });
    await checkpoint("projects");
    const customerProject = await prepareProject(
      owner,
      builder,
      builderEmail,
      marker,
      "Дом семьи Орловых",
      ["FOUNDATION", "FOUNDATION"],
    );
    manifest.projects.push(customerProject);
    await checkpoint("customer-project");
    const contractorProject = await prepareProject(
      owner,
      builder,
      builderEmail,
      marker,
      "Каркасный дом на Озёрной",
      ["UTILITIES", "ROOF"],
    );
    manifest.projects.push(contractorProject);
    await checkpoint("contractor-project");
    const emptyProject = await owner.send("/projects", "POST", {
      name: `Демо · Новая карточка · ${marker}`,
      address: "Синтетический участок без плана",
    });
    manifest.emptyProject = emptyProject;
    const invitation = await builder.send(
      `/organizations/${organization.id}/invitations`,
      "POST",
      {
        recipientEmail: lawyerEmail,
        position: "Юрист",
        idempotencyKey: randomUUID(),
      },
    );
    await lawyer.send(
      `/organizations/invitations/${invitation.id}/accept`,
      "POST",
      { version: invitation.version },
    );
    const membership = (await lawyer.send("/organizations/me")).membership;
    await builder.send(
      `/projects/${contractorProject.id}/contractor-team`,
      "PUT",
      {
        version: 0,
        members: [
          { membershipId: membership.id, documents: true, works: false },
        ],
      },
    );
    browser = await chromium.launch();
    const page = await browser.newPage({
      viewport: { width: 960, height: 680 },
    });
    await page.setContent(
      await readFile(
        resolve(root, "frontend/e2e/fixtures/construction-foundation.svg"),
        "utf8",
      ),
    );
    const image = await page.locator("svg").screenshot();
    const foundation = customerProject.stages[0],
      utilities = contractorProject.stages[0];
    await builder.send(
      `/projects/${customerProject.id}/stages/${foundation.id}/start`,
      "POST",
      { version: foundation.version },
    );
    const entry = await publishGuidePhoto(
      builder,
      customerProject.id,
      foundation.id,
      image,
    );
    const current = await builder.send(
      `/projects/${customerProject.id}/stages/${foundation.id}`,
    );
    await builder.send(
      `/projects/${customerProject.id}/stages/${foundation.id}/complete`,
      "POST",
      { version: current.version },
    );
    manifest.submission = await submitGuideStage(
      builder,
      customerProject.id,
      foundation.id,
      entry,
    );
    manifest.entry = entry;
    await builder.send(
      `/projects/${contractorProject.id}/stages/${utilities.id}/start`,
      "POST",
      { version: utilities.version },
    );
    await builder.send(
      `/projects/${contractorProject.id}/stages/${utilities.id}/journal`,
      "POST",
      {
        kind: "NOTE",
        text: "Синтетический сценарий: сети смонтированы. Следующий шаг — загрузить и опубликовать фото.",
        photos: [],
        attachmentIds: [],
        sourceEntryId: null,
        idempotencyKey: randomUUID(),
      },
    );
    await checkpoint("profiles");
    if (setProfiles) {
      const args = [
        "compose",
        "exec",
        "-T",
        "backend",
        "java",
        "-jar",
        "/app/app.jar",
        "--spring.profiles.active=construction-demo",
        "--server.port=0",
        "--bank.stage.worker-delay-ms=3600000",
        `--demo.projects=${customerProject.id},${contractorProject.id}`,
        `--demo.owner=${ownerEmail}`,
        `--demo.marker=${marker}`,
      ];
      const result = spawnSync("docker", args, {
        cwd: root,
        encoding: "utf8",
        timeout: 120000,
      });
      if (result.status !== 0)
        throw new Error(
          `Dev-заполнение паспорта завершилось ошибкой: ${result.stderr || result.stdout}`,
        );
    }
    await checkpoint("ready");
    return manifest;
  } finally {
    await browser?.close();
    await Promise.all([
      owner.context.dispose(),
      builder.context.dispose(),
      lawyer.context.dispose(),
    ]);
  }
}
if (
  process.argv[1] &&
  resolve(process.argv[1]) === fileURLToPath(import.meta.url)
) {
  const output = resolve(root, "output/construction-guide-demo.json");
  await mkdir(dirname(output), { recursive: true });
  try {
    const result = await createGuideDemo({
      baseURL: process.env.BASE_URL ?? "http://localhost:8088",
      onCheckpoint: async (data) =>
        writeFile(output, JSON.stringify(data, null, 2) + "\n"),
    });
    console.log(
      `Демонабор готов. Заказчик: ${result.ownerEmail}\nПодрядчик: ${result.builderEmail}\nСотрудник с правами на документы: ${result.lawyerEmail}\nСинтетический пароль: ${result.password}\nДанные и ID: ${output}\nВход: ${result.baseURL}`,
    );
  } catch (e) {
    console.error(
      `Демонабор не завершён. Последний шаг и созданные ID сохранены в ${output}. Повторный запуск создаст отдельный набор.\n${e.message}`,
    );
    process.exitCode = 1;
  }
}
