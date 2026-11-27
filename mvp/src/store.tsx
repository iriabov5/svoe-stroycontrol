import {
  createContext,
  useContext,
  useRef,
  useState,
  useEffect,
  type ReactNode,
} from "react";
import {
  BadgeCheck,
  Check,
  FileCheck2,
  Phone,
  ShieldCheck,
  X,
} from "lucide-react";
import {
  blankData,
  mergeGuest,
  normalizePhone,
  maskPhone,
  validPhone,
  uid,
  today,
  dateLabel,
  type AppData,
  type UserProfile,
} from "./domain";
import { Modal, Field, Notice, Spinner } from "./ui";
interface Snapshot {
  user: UserProfile | null;
  data: AppData;
}
type Action = (profile: UserProfile) => void;
interface Store extends Snapshot {
  update: (f: (data: AppData) => AppData) => boolean;
  requireAuth: (title: string, action: Action) => void;
  logout: () => void;
  notify: (message: string) => void;
  openLead: (kind: string) => void;
  openApplications: () => void;
}
const Context = createContext<Store | null>(null);
const PROFILE_KEY = "svoe_selo_profile";
function readData(key: string): AppData {
  const fallback = blankData();
  const raw = localStorage.getItem(key);
  if (!raw) return fallback;
  try {
    const d = JSON.parse(raw);
    if (
      d?.version !== 1 ||
      !Array.isArray(d.estimate) ||
      !Array.isArray(d.obligations)
    )
      return fallback;
    return {
      ...fallback,
      ...d,
      insurance: { ...fallback.insurance, ...d.insurance },
    };
  } catch {
    return fallback;
  }
}
function initial(): Snapshot {
  try {
    const u = JSON.parse(
      localStorage.getItem(PROFILE_KEY) || "null",
    ) as UserProfile | null;
    const user = u?.isAuthenticated && validPhone(u.phone) ? u : null;
    return {
      user,
      data: readData(user ? "user_data_" + user.phone : "guest_data"),
    };
  } catch {
    return { user: null, data: blankData() };
  }
}
export function AppProvider({ children }: { children: ReactNode }) {
  const [snapshot, setSnapshot] = useState(initial);
  const current = useRef(snapshot);
  const [toast, setToast] = useState("");
  const timer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [auth, setAuth] = useState<string | null>(null);
  const pending = useRef<Action | null>(null);
  const [lead, setLead] = useState<{
    kind: string;
    phone: string;
    name: string;
  } | null>(null);
  const [applications, setApplications] = useState(false);
  const notify = (message: string) => {
    clearTimeout(timer.current);
    setToast(message);
    timer.current = setTimeout(() => setToast(""), 5000);
  };
  useEffect(() => () => clearTimeout(timer.current), []);
  function update(f: (data: AppData) => AppData): boolean {
    const old = current.current;
    const data = f(old.data);
    try {
      localStorage.setItem(
        old.user ? "user_data_" + old.user.phone : "guest_data",
        JSON.stringify(data),
      );
      current.current = { ...old, data };
      setSnapshot(current.current);
      return true;
    } catch {
      notify(
        "Не удалось сохранить: память браузера заполнена или недоступна. Удалите ненужные документы и повторите.",
      );
      return false;
    }
  }
  function requireAuth(title: string, action: Action) {
    if (current.current.user) action(current.current.user);
    else {
      pending.current = action;
      setAuth(title);
    }
  }
  function login(user: UserProfile) {
    try {
      const data = mergeGuest(
        readData("user_data_" + user.phone),
        current.current.data,
      );
      const profile = {
        ...user,
        savedPlotChecks: data.plotChecks.map((p) => p.cadastralNumber),
      };
      localStorage.setItem("user_data_" + profile.phone, JSON.stringify(data));
      localStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
      localStorage.removeItem("guest_data");
      current.current = { user: profile, data };
      setSnapshot(current.current);
      setAuth(null);
      notify("Вы вошли в аккаунт. Ваши данные сохранены");
      const action = pending.current;
      pending.current = null;
      action?.(profile);
    } catch {
      notify(
        "Не удалось сохранить профиль. Освободите память браузера и повторите вход.",
      );
    }
  }
  function logout() {
    try {
      localStorage.removeItem(PROFILE_KEY);
      current.current = { user: null, data: readData("guest_data") };
      setSnapshot(current.current);
      notify("Вы вышли. Данные остались в вашем профиле.");
    } catch {
      notify("Не удалось выйти: хранилище браузера недоступно.");
    }
  }
  function openLead(kind: string) {
    requireAuth("Войдите, чтобы оставить заявку", (u) =>
      setLead({ kind, phone: maskPhone(u.phone), name: u.fullName || "" }),
    );
  }
  return (
    <Context.Provider
      value={{
        ...snapshot,
        update,
        requireAuth,
        logout,
        notify,
        openLead,
        openApplications: () => setApplications(true),
      }}
    >
      {children}
      {auth && (
        <AuthModal
          title={auth}
          onClose={() => {
            pending.current = null;
            setAuth(null);
          }}
          onLogin={login}
        />
      )}
      {lead && (
        <Modal title={lead.kind} onClose={() => setLead(null)}>
          <p className="muted">
            Оставьте контакты для демонстрационной заявки. Она сохранится только
            в вашем профиле.
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!validPhone(lead.phone)) return;
              if (
                update((d) => ({
                  ...d,
                  applications: [
                    {
                      id: uid(),
                      kind: lead.kind,
                      name: lead.name,
                      phone: normalizePhone(lead.phone),
                      date: today(),
                    },
                    ...d.applications,
                  ],
                }))
              ) {
                setLead(null);
                setApplications(true);
                notify("Демозаявка сохранена в разделе «Мои заявки»");
              }
            }}
          >
            <Field label="Как к вам обращаться">
              <input
                required
                autoComplete="name"
                value={lead.name}
                onChange={(e) => setLead({ ...lead, name: e.target.value })}
                maxLength={80}
              />
            </Field>
            <Field label="Номер телефона">
              <input
                type="tel"
                value={lead.phone}
                onChange={(e) =>
                  setLead({ ...lead, phone: maskPhone(e.target.value) })
                }
                required
              />
            </Field>
            <Notice>
              Демонстрация: заявка не отправляется в банк, звонка не будет.
            </Notice>
            <button
              className="button primary full"
              disabled={!validPhone(lead.phone) || !lead.name.trim()}
            >
              Сохранить заявку
            </button>
          </form>
        </Modal>
      )}
      {applications && (
        <Modal
          title="Мои заявки и полисы"
          onClose={() => setApplications(false)}
          wide
        >
          {snapshot.user?.isVerifiedGosuslugi && (
            <div className="badge green">
              <BadgeCheck size={16} /> Профиль подтверждён через Госуслуги ·
              демо
            </div>
          )}
          {!snapshot.data.applications.length &&
          !snapshot.data.policies.length ? (
            <div className="empty-state">
              <FileCheck2 size={36} />
              <h3>Здесь появятся ваши заявки</h3>
              <p>
                Проверьте участок или рассчитайте страховку, чтобы сделать
                следующий шаг.
              </p>
            </div>
          ) : (
            <div className="records">
              {snapshot.data.applications.map((a) => (
                <article className="record" key={a.id}>
                  <div className="icon-tile">
                    <FileCheck2 />
                  </div>
                  <div>
                    <h3>{a.kind}</h3>
                    <p>
                      {a.name} · {dateLabel(a.date)}
                    </p>
                  </div>
                  <span className="badge neutral">Демозаявка сохранена</span>
                </article>
              ))}
              {snapshot.data.policies.map((p) => (
                <article className="record" key={p.id}>
                  <div className="icon-tile">
                    <ShieldCheck />
                  </div>
                  <div>
                    <h3>
                      {p.kind === "mortgage"
                        ? "Ипотечное страхование"
                        : "Страхование недостроя"}
                    </h3>
                    <p>
                      Демополис № {p.id.slice(0, 8)} · до{" "}
                      {dateLabel(p.expiresAt)}
                    </p>
                  </div>
                  <span className="badge green">Оформлен в демо</span>
                </article>
              ))}
            </div>
          )}
        </Modal>
      )}
      {toast && (
        <div className="toast" role="status">
          <Check size={19} />
          <span>{toast}</span>
          <button onClick={() => setToast("")} aria-label="Закрыть уведомление">
            <X size={17} />
          </button>
        </div>
      )}
    </Context.Provider>
  );
}
function AuthModal({
  title,
  onClose,
  onLogin,
}: {
  title: string;
  onClose: () => void;
  onLogin: (u: UserProfile) => void;
}) {
  const [phone, setPhone] = useState("");
  const [step, setStep] = useState(1);
  const [code, setCode] = useState(["", "", "", ""]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const codeRefs = useRef<(HTMLInputElement | null)[]>([]);
  const alive = useRef(true);
  useEffect(
    () => () => {
      alive.current = false;
    },
    [],
  );
  useEffect(() => {
    if (step === 2) codeRefs.current[0]?.focus();
  }, [step]);
  const submit = () => {
    if (code.join("") !== "1234") {
      setError("Неверный код. В демоверсии используйте 1234.");
      return;
    }
    onLogin({
      isAuthenticated: true,
      phone: normalizePhone(phone),
      authProvider: "phone",
      isVerifiedGosuslugi: false,
      savedPlotChecks: [],
    });
  };
  return (
    <Modal title={title} onClose={onClose}>
      <div className="auth-emblem">
        <Phone size={28} />
      </div>
      <p className="muted">Один вход — и ваши планы на дом всегда под рукой.</p>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          if (step === 1) {
            if (validPhone(phone)) {
              setStep(2);
              setError("");
            } else setError("Введите номер телефона полностью.");
          } else submit();
        }}
      >
        {step === 1 ? (
          <Field label="Номер телефона">
            <input
              autoFocus
              type="tel"
              autoComplete="tel"
              placeholder="+7 (999) 123-45-67"
              value={phone}
              onChange={(e) => setPhone(maskPhone(e.target.value))}
            />
          </Field>
        ) : (
          <>
            <div className="code-heading">
              <span>Код для {phone}</span>
              <button
                type="button"
                className="text-button"
                onClick={() => setStep(1)}
              >
                Изменить
              </button>
            </div>
            <div
              className="code-fields"
              onPaste={(e) => {
                e.preventDefault();
                const digits = e.clipboardData
                  .getData("text")
                  .replace(/\D/g, "")
                  .slice(0, 4);
                setCode(Array.from({ length: 4 }, (_, i) => digits[i] || ""));
                codeRefs.current[Math.min(digits.length, 3)]?.focus();
              }}
            >
              {code.map((v, i) => (
                <input
                  key={i}
                  ref={(n) => {
                    codeRefs.current[i] = n;
                  }}
                  aria-label={`Цифра ${i + 1} кода`}
                  inputMode="numeric"
                  autoComplete={i === 0 ? "one-time-code" : "off"}
                  maxLength={1}
                  value={v}
                  onChange={(e) => {
                    const n = e.target.value.replace(/\D/g, "").slice(-1);
                    setCode((old) => old.map((x, j) => (j === i ? n : x)));
                    setError("");
                    if (n) codeRefs.current[i + 1]?.focus();
                  }}
                  onKeyDown={(e) => {
                    if (e.key === "Backspace" && !v)
                      codeRefs.current[i - 1]?.focus();
                  }}
                />
              ))}
            </div>
            <p className="demo-code">
              Для теста введите код <strong>1234</strong>. SMS не отправляется.
            </p>
          </>
        )}
        {error && (
          <p role="alert" className="error-text">
            {error}
          </p>
        )}
        <button className="button primary full" disabled={busy}>
          {step === 1 ? "Получить код" : "Войти и продолжить"}
        </button>
      </form>
      <div className="or-divider">
        <span>или</span>
      </div>
      <button
        className="button esia full"
        disabled={busy}
        onClick={async () => {
          setBusy(true);
          await new Promise((r) => setTimeout(r, 600));
          if (alive.current)
            onLogin({
              isAuthenticated: true,
              phone: validPhone(phone) ? normalizePhone(phone) : "+79991234567",
              fullName: "Иванов Иван Иванович",
              authProvider: "gosuslugi",
              isVerifiedGosuslugi: true,
              savedPlotChecks: [],
            });
        }}
      >
        {busy ? <Spinner /> : <ShieldCheck size={19} />}Войти через Госуслуги
        (ЕСИА)
      </button>
      <p className="fine-print">
        Демонстрационный вход. Настоящий аккаунт Госуслуг и банковские данные не
        используются.
      </p>
    </Modal>
  );
}
export function useApp() {
  const c = useContext(Context);
  if (!c) throw new Error("AppProvider is missing");
  return c;
}
