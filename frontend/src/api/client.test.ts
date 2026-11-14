import { afterEach, expect, it, vi } from "vitest";
import { api } from "./client";
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
