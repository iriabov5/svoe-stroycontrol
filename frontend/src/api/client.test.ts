import { afterEach, expect, it, vi } from "vitest";
import { api, ApiError, request } from "./client";
afterEach(() => vi.unstubAllGlobals());
it("после отклонённого CSRF получает новый токен и повторяет запись один раз", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ token: "old" }))
    .mockResolvedValueOnce(
      Response.json(
        { code: "INVALID_CSRF_TOKEN", title: "Повторите" },
        { status: 403 },
      ),
    )
    .mockResolvedValueOnce(Response.json({ token: "new" }))
    .mockResolvedValueOnce(Response.json({ id: "project" }, { status: 201 }));
  vi.stubGlobal("fetch", fetch);
  await api.create({ name: "Дом", address: "Адрес" });
  expect(fetch).toHaveBeenCalledTimes(4);
  expect(fetch.mock.calls[3][1].headers["X-CSRF-TOKEN"]).toBe("new");
});
it("ошибка сети не повторяет изменяющую операцию автоматически", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ token: "csrf" }))
    .mockRejectedValueOnce(new TypeError("network"));
  vi.stubGlobal("fetch", fetch);
  await expect(api.create({ name: "Дом", address: "Адрес" })).rejects.toThrow(
    "Не удалось связаться",
  );
  expect(fetch).toHaveBeenCalledTimes(2);
});

it("повторный отказ CSRF завершает запрос ошибкой без бесконечного повтора", async () => {
  const denied = () =>
    Response.json(
      { code: "INVALID_CSRF_TOKEN", title: "Нет доступа" },
      { status: 403 },
    );
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ token: "first" }))
    .mockResolvedValueOnce(denied())
    .mockResolvedValueOnce(Response.json({ token: "second" }))
    .mockResolvedValueOnce(denied());
  vi.stubGlobal("fetch", fetch);
  await expect(
    request("/projects/house/financing-planning/plot-checks", "POST", {
      idempotencyKey: "same-key",
    }),
  ).rejects.toMatchObject({ status: 403, code: "INVALID_CSRF_TOKEN" });
  expect(fetch).toHaveBeenCalledTimes(4);
  expect(fetch.mock.calls[1][1].body).toBe(fetch.mock.calls[3][1].body);
});
it("истёкшая сессия личного плана уведомляет оболочку приложения", async () => {
  const expired = vi.fn();
  window.addEventListener("session-expired", expired);
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(
        Response.json(
          { code: "UNAUTHORIZED", title: "Войдите снова" },
          { status: 401 },
        ),
      ),
  );
  try {
    await expect(
      request("/projects/house/financing-planning"),
    ).rejects.toBeInstanceOf(ApiError);
    expect(expired).toHaveBeenCalledOnce();
    await expect(api.me()).rejects.toMatchObject({ status: 401 });
    expect(expired).toHaveBeenCalledOnce();
  } finally {
    window.removeEventListener("session-expired", expired);
  }
});
it("требование заполнить профиль передаёт событие и ошибки полей", async () => {
  const required = vi.fn();
  window.addEventListener("profile-required", required);
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(
      Response.json(
        {
          code: "PROFILE_REQUIRED",
          title: "Заполните профиль",
          fieldErrors: { fullName: "Введите имя" },
        },
        { status: 403 },
      ),
    ),
  );
  try {
    await expect(
      request("/projects/house/financing-planning"),
    ).rejects.toMatchObject({
      code: "PROFILE_REQUIRED",
      fieldErrors: { fullName: "Введите имя" },
    });
    expect(required).toHaveBeenCalledOnce();
  } finally {
    window.removeEventListener("profile-required", required);
  }
});
it("ошибка прокси без JSON получает понятное сообщение для повтора", async () => {
  vi.stubGlobal(
    "fetch",
    vi
      .fn()
      .mockResolvedValue(new Response("Service unavailable", { status: 503 })),
  );
  await expect(
    request("/projects/house/financing-planning"),
  ).rejects.toMatchObject({
    status: 503,
    code: "UNKNOWN",
    message: "Не удалось выполнить действие. Попробуйте ещё раз.",
    fieldErrors: {},
  });
});
it("выход с ответом 204 не пытается читать отсутствующий JSON", async () => {
  const fetch = vi
    .fn()
    .mockResolvedValueOnce(Response.json({ token: "csrf" }))
    .mockResolvedValueOnce(new Response(null, { status: 204 }));
  vi.stubGlobal("fetch", fetch);
  await expect(api.logout()).resolves.toBeUndefined();
  expect(fetch.mock.calls[1][1]).toMatchObject({
    method: "POST",
    credentials: "same-origin",
    headers: { "X-CSRF-TOKEN": "csrf" },
  });
  expect(fetch.mock.calls[1][1].headers["Content-Type"]).toBeUndefined();
});
