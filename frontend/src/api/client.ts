import type { components } from "./schema";
export type Account = components["schemas"]["Account"];
export type Project = components["schemas"]["Project"];
export type Role = components["schemas"]["Role"];
export type Stage = components["schemas"]["Stage"];
export type StagePlan = components["schemas"]["CreateStageRequest"];
type Problem = components["schemas"]["Problem"];

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
  }
}

export async function request<T>(
  path: string,
  method = "GET",
  body?: unknown,
  retry = true,
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json, application/problem+json",
  };
  if (method !== "GET") {
    const csrf =
      await request<components["schemas"]["CsrfToken"]>("/auth/csrf");
    headers["X-CSRF-TOKEN"] = csrf.token;
  }
  if (body !== undefined) headers["Content-Type"] = "application/json";
  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch {
    throw new Error(
      "Не удалось связаться с сервисом. Проверьте соединение и повторите действие.",
    );
  }
  if (!response.ok) {
    const problem = (await response.json().catch(() => null)) as Problem | null;
    if (problem?.code === "INVALID_CSRF_TOKEN" && retry && method !== "GET")
      return request<T>(path, method, body, false);
    if (problem?.code === "PROFILE_REQUIRED")
      window.dispatchEvent(new Event("profile-required"));
    if (response.status === 401 && !path.startsWith("/auth/"))
      window.dispatchEvent(new Event("session-expired"));
    throw new ApiError(
      response.status,
      problem?.code ?? "UNKNOWN",
      problem?.title ?? "Не удалось выполнить действие. Попробуйте ещё раз.",
      problem?.fieldErrors ?? {},
    );
  }
  const result =
    response.status === 204
      ? (undefined as T)
      : await (response.json() as Promise<T>);
  notifyConstructionChange(method, path);
  return result;
}

function notifyConstructionChange(method: string, path: string) {
  if (method !== "GET" && !path.startsWith("/auth/"))
    window.dispatchEvent(new Event("construction-changed"));
}

export const api = {
  me: () => request<Account>("/auth/me"),
  register: (body: components["schemas"]["RegisterRequest"]) =>
    request<Account>("/auth/register", "POST", body),
  login: (body: components["schemas"]["LoginRequest"]) =>
    request<Account>("/auth/login", "POST", body),
  logout: () => request<void>("/auth/logout", "POST"),
  list: () => request<Project[]>("/projects"),
  get: (id: string) => request<Project>(`/projects/${encodeURIComponent(id)}`),
  create: (body: components["schemas"]["CreateProjectRequest"]) =>
    request<Project>("/projects", "POST", body),
  assign: (
    id: string,
    body: components["schemas"]["AssignContractorRequest"],
  ) =>
    request<Project>(
      `/projects/${encodeURIComponent(id)}/contractor`,
      "PUT",
      body,
    ),
  listStages: (projectId: string) =>
    request<Stage[]>(`/projects/${encodeURIComponent(projectId)}/stages`),
  getStage: (projectId: string, stageId: string) =>
    request<Stage>(
      `/projects/${encodeURIComponent(projectId)}/stages/${encodeURIComponent(stageId)}`,
    ),
  createStage: (projectId: string, body: StagePlan) =>
    request<Stage>(
      `/projects/${encodeURIComponent(projectId)}/stages`,
      "POST",
      body,
    ),
  updateStage: (
    projectId: string,
    stageId: string,
    body: components["schemas"]["UpdateStageRequest"],
  ) =>
    request<Stage>(
      `/projects/${encodeURIComponent(projectId)}/stages/${encodeURIComponent(stageId)}`,
      "PUT",
      body,
    ),
  reorderStages: (
    projectId: string,
    stages: components["schemas"]["StageVersion"][],
  ) =>
    request<Stage[]>(
      `/projects/${encodeURIComponent(projectId)}/stages/order`,
      "PUT",
      { stages },
    ),
  startStage: (projectId: string, stageId: string, version: number) =>
    request<Stage>(
      `/projects/${encodeURIComponent(projectId)}/stages/${encodeURIComponent(stageId)}/start`,
      "POST",
      { version },
    ),
  completeStage: (projectId: string, stageId: string, version: number) =>
    request<Stage>(
      `/projects/${encodeURIComponent(projectId)}/stages/${encodeURIComponent(stageId)}/complete`,
      "POST",
      { version },
    ),
};
export function errorMessage(error: unknown): string {
  return error instanceof Error
    ? error.message
    : "Не удалось выполнить действие. Попробуйте ещё раз.";
}

export function commandKey(
  ref: { current: { body: string; key: string } | null },
  body: unknown,
) {
  const encoded = JSON.stringify(body);
  if (ref.current?.body !== encoded)
    ref.current = { body: encoded, key: crypto.randomUUID() };
  return ref.current!.key;
}
