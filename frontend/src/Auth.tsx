import { useState, type SubmitEvent } from "react";

import {
  api,
  ApiError,
  errorMessage,
  type Account,
  type Role,
} from "./api/client";

import { Field, Notice, PageTitle } from "./components";

export function Auth({
  onLogin,
  expired,
}: Readonly<{
  onLogin: (account: Account) => void;
  expired: boolean;
}>) {
  const [register, setRegister] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("CUSTOMER");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [emailError, setEmailError] = useState("");
  const [success, setSuccess] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [nameError, setNameError] = useState("");
  async function submit(event: SubmitEvent) {
    event.preventDefault();
    setBusy(true);
    setError("");
    setEmailError("");
    setNameError("");
    setSuccess("");
    try {
      if (register) {
        const account = await api.register({
          email,
          password,
          role,
          displayName,
        });
        setEmail(account.email);
        setRegister(false);
        setPassword("");
        setSuccess("Аккаунт создан. Введите пароль, чтобы войти.");
      } else {
        onLogin(await api.login({ email, password }));
        setPassword("");
      }
    } catch (error_) {
      if (error_ instanceof ApiError && error_.code === "EMAIL_ALREADY_EXISTS")
        setEmailError(error_.message);
      if (error_ instanceof ApiError)
        setNameError(error_.fieldErrors.displayName ?? "");
      setError(errorMessage(error_));
      setPassword("");
    } finally {
      setBusy(false);
    }
  }
  function submitLabel() {
    if (busy) {
      return "Подождите…";
    }
    if (register) {
      return "Создать аккаунт";
    }
    return "Войти";
  }
  return (
    <div className="auth-layout">
      <section className="intro" aria-label="О сервисе">
        <span className="pill">Один дом. Общая картина.</span>
        <h2>
          Стройте дом.
          <br />
          <span>Будьте на связи.</span>
        </h2>
        <p>
          В карточке дома заказчик проверяет участок и оценивает ипотеку, а
          вместе с подрядчиком следит за строительством.
        </p>
        <div className="house-art" aria-hidden="true">
          <div className="roof" />
          <div className="house">
            <div className="window" />
            <div className="door" />
          </div>
          <span className="ground" />
        </div>
        <div className="intro-footer">
          <span>01 / Карточка дома</span>
          <span>Доступ только участникам</span>
        </div>
      </section>
      <section
        className="panel auth-panel"
        aria-label={register ? "Регистрация" : "Вход"}
      >
        <PageTitle
          eyebrow="Личный кабинет"
          description={
            register
              ? "Выберите свою роль — от неё зависят доступные действия."
              : "Войдите, чтобы открыть свои строительные объекты."
          }
        >
          {register ? "Создать аккаунт" : "С возвращением"}
        </PageTitle>
        {expired && (
          <Notice>Сессия завершилась. Войдите снова, чтобы продолжить.</Notice>
        )}
        {success && <Notice>{success}</Notice>}
        {error && <Notice error>{error}</Notice>}
        <form onSubmit={submit} aria-busy={busy}>
          {register && (
            <Field
              id="register-name"
              error={nameError}
              label={role === "CUSTOMER" ? "Ваше имя" : "Имя и фамилия"}
              autoComplete="name"
              required
              minLength={2}
              maxLength={120}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          )}
          <Field
            id="email"
            label="Email"
            type="email"
            autoComplete="username"
            required
            maxLength={320}
            value={email}
            onChange={(event) => {
              setEmail(event.target.value);
              setEmailError("");
            }}
            error={emailError}
          />
          <Field
            id="password"
            label="Пароль"
            type="password"
            autoComplete={register ? "new-password" : "current-password"}
            required
            minLength={12}
            maxLength={128}
            hint="От 12 до 128 символов. Пробелы учитываются."
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {register && (
            <fieldset>
              <legend>Ваша роль</legend>
              <div className="role-options">
                <label aria-label="Заказчик">
                  <input
                    type="radio"
                    name="role"
                    checked={role === "CUSTOMER"}
                    onChange={() => setRole("CUSTOMER")}
                  />{" "}
                  <span>
                    <strong>Заказчик</strong>
                    <small>Создаю карточку своего дома</small>
                  </span>
                </label>
                <label aria-label="Подрядчик">
                  <input
                    type="radio"
                    name="role"
                    checked={role === "CONTRACTOR"}
                    onChange={() => setRole("CONTRACTOR")}
                  />{" "}
                  <span>
                    <strong>Подрядчик</strong>
                    <small>Работаю на объекте заказчика</small>
                  </span>
                </label>
              </div>
              <p className="hint">Роль выбирается один раз.</p>
            </fieldset>
          )}
          <button type="submit" className="button primary full" disabled={busy}>
            {submitLabel()}
          </button>
        </form>
        <p className="auth-switch">
          {register ? "Уже есть аккаунт?" : "Впервые здесь?"}{" "}
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() => {
              setRegister(!register);
              setError("");
              setEmailError("");
              setSuccess("");
              setPassword("");
            }}
          >
            {register ? "Войти" : "Зарегистрироваться"}
          </button>
        </p>
      </section>
    </div>
  );
}
