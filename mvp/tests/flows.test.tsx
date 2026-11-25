import {
  render,
  screen,
  waitFor,
  fireEvent,
  within,
} from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import App from "../src/App";
import { blankData, generateObligations } from "../src/domain";
function start(path: string) {
  history.replaceState({}, "", path);
  return render(<App />);
}
async function sms(
  user: ReturnType<typeof userEvent.setup>,
  phone = "+79990001122",
) {
  await user.type(screen.getByLabelText("Номер телефона"), phone);
  await user.click(screen.getByRole("button", { name: "Получить код" }));
  for (let i = 0; i < 4; i++)
    await user.type(
      screen.getByLabelText(`Цифра ${i + 1} кода`),
      String(i + 1),
    );
  await user.click(screen.getByRole("button", { name: "Войти и продолжить" }));
}
describe("Основные пользовательские сценарии", () => {
  it("Гостевой экспорт продолжает действие после SMS и сохраняет смету при повторном открытии", async () => {
    const user = userEvent.setup();
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    const view = start("/estimate");
    await user.click(screen.getByRole("button", { name: /Каркасный дом/ }));
    fireEvent.change(screen.getByLabelText("Фундамент: Факт 1"), {
      target: { value: "350000" },
    });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Скачать смету" }));
    expect(screen.getByRole("dialog")).toHaveAccessibleName(
      "Войдите, чтобы скачать смету",
    );
    await sms(user);
    expect(click).toHaveBeenCalledTimes(1);
    expect(localStorage.getItem("guest_data")).toBeNull();
    expect(
      JSON.parse(localStorage.getItem("user_data_+79990001122")!).estimate[0]
        .items[0].actualAmount,
    ).toBe(350000);
    view.unmount();
    start("/estimate");
    expect(screen.getByLabelText("Фундамент: Факт 1")).toHaveValue(350000);
  });
  it("Отмена авторизации отменяет отложенный экспорт", async () => {
    const user = userEvent.setup();
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    start("/estimate");
    await user.click(screen.getByRole("button", { name: /Начать с чистого/ }));
    await user.click(screen.getByRole("button", { name: "Скачать смету" }));
    await user.click(screen.getByLabelText("Закрыть окно"));
    await user.click(
      screen.getByRole("button", { name: "Войти", exact: true }),
    );
    await sms(user);
    expect(click).not.toHaveBeenCalled();
  });
  it("Кадастр показывает отказ, ставку и неизвестный номер без требования входа", async () => {
    const user = userEvent.setup();
    start("/");
    await user.click(
      screen.getByRole("button", { name: "Московская область", exact: true }),
    );
    expect(
      await screen.findByText("Не подходит по демопроверке"),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Татарстан · 3%" }));
    expect(
      await screen.findByText("Подходит по демопроверке"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    fireEvent.change(
      screen.getByLabelText("Подходит ли ваш участок для сельской ипотеки?"),
      { target: { value: "01:01:0000001:1" } },
    );
    await user.click(screen.getByRole("button", { name: "Проверить участок" }));
    expect(
      await screen.findByText(/В демонстрационной базе такого участка нет/),
    ).toBeInTheDocument();
  });
  it("Гостевой документ возобновляет загрузку после ЕСИА и остаётся после перезагрузки", async () => {
    const d = blankData();
    d.mortgageSetup = {
      signedAt: "2026-01-01",
      objectType: "construction",
      isVeteran: false,
    };
    d.obligations = generateObligations(d.mortgageSetup);
    localStorage.setItem("guest_data", JSON.stringify(d));
    const view = start("/mortgage-care");
    const input = screen.getAllByLabelText(/Документ:/)[0];
    fireEvent.change(input, {
      target: {
        files: [
          new File(["%PDF-1.7 test"], "spravka.pdf", {
            type: "application/pdf",
          }),
        ],
      },
    });
    expect(screen.getByRole("dialog")).toHaveAccessibleName(
      "Войдите, чтобы отправить документы в банк",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Войти через Госуслуги (ЕСИА)" }),
    );
    expect(await screen.findByText("spravka.pdf")).toBeInTheDocument();
    await waitFor(() =>
      expect(
        JSON.parse(localStorage.getItem("user_data_+79991234567")!)
          .obligations[0].status,
      ).toBe("under_review"),
    );
    view.unmount();
    start("/mortgage-care");
    expect(screen.getByText("spravka.pdf")).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole("button", {
        name: "Демо: симулировать одобрение банком",
      }),
    );
    expect(
      screen.getByText("Принято — ставка 3% сохранена"),
    ).toBeInTheDocument();
  });
  it("Демопокупка полиса после входа закрывает страховой срок и сохраняет остальные данные", async () => {
    const user = userEvent.setup();
    const d = blankData();
    d.mortgageSetup = {
      signedAt: "2026-01-01",
      objectType: "construction",
      isVeteran: true,
    };
    d.obligations = generateObligations(d.mortgageSetup);
    const target = d.obligations.find((o) => o.type === "insurance")!;
    localStorage.setItem("guest_data", JSON.stringify(d));
    start("/insurance?obligation=" + target.id);
    await user.click(screen.getByRole("button", { name: "Оформить полис" }));
    await sms(user);
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveAccessibleName("Оформим вашу защиту");
    await user.type(
      within(dialog).getByLabelText("ФИО"),
      "Тестовый Пользователь",
    );
    await user.type(
      within(dialog).getByLabelText("Электронная почта"),
      "test@example.com",
    );
    await user.click(
      within(dialog).getByRole("button", { name: /Подтвердить демооплату/ }),
    );
    expect(await screen.findByText("Демополис оформлен")).toBeInTheDocument();
    const saved = JSON.parse(localStorage.getItem("user_data_+79990001122")!);
    expect(
      saved.obligations.find((o: { id: string }) => o.id === target.id).status,
    ).toBe("approved");
    expect(
      saved.obligations.filter(
        (o: { status: string }) => o.status === "approved",
      ),
    ).toHaveLength(1);
    expect(saved.policies).toHaveLength(1);
  });
  it("Переполнение памяти не теряет прежнюю смету", async () => {
    const user = userEvent.setup();
    start("/estimate");
    await user.click(screen.getByRole("button", { name: /Каркасный дом/ }));
    const before = localStorage.getItem("guest_data");
    vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("full", "QuotaExceededError");
    });
    fireEvent.change(screen.getByLabelText("Фундамент: Факт 1"), {
      target: { value: "900000" },
    });
    expect(localStorage.getItem("guest_data")).toBe(before);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Не удалось сохранить",
    );
  });
});
