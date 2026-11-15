import { BrandMark } from "./BrandMark";
import { useEffect, useState, useRef } from "react";
import { api, ApiError, errorMessage, type Account } from "./api/client";

import { Profile } from "./Profile";
import { Auth } from "./Auth";
import { Notice } from "./components";
import { CreateProject, ProjectDetail, ProjectList } from "./Projects";
import { StageDetail } from "./Stages";

export function App() {
  const [account, setAccount] = useState<Account | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expired, setExpired] = useState(false);
  const [route, setRoute] = useState(
    window.location.hash.slice(1) || "/projects",
  );
  const [attempt, setAttempt] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [profileRequired, setProfileRequired] = useState(false);
  const profileFocus = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (!profileRequired) return;
    document.getElementById("profile-name")?.focus();
    const escape = (e: KeyboardEvent) => {
      if (e.key === "Tab") {
        const focusable = Array.from(
          document.querySelectorAll<HTMLElement>(
            ".profile-gate a[href], .profile-gate button:not([disabled]), .profile-gate input:not([disabled]), .profile-gate select:not([disabled]), .profile-gate textarea:not([disabled])",
          ),
        ).filter((item) => item.getClientRects().length > 0);
        const first = focusable[0],
          last = focusable.at(-1);
        if (e.shiftKey && document.activeElement === first) {
          e.preventDefault();
          last?.focus();
        } else if (!e.shiftKey && document.activeElement === last) {
          e.preventDefault();
          first?.focus();
        }
      }
      if (e.key === "Escape") {
        setProfileRequired(false);
        requestAnimationFrame(() => profileFocus.current?.focus());
      }
    };
    window.addEventListener("keydown", escape);
    return () => window.removeEventListener("keydown", escape);
  }, [profileRequired]);
  const [returnRoute, setReturnRoute] = useState("/projects");
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError("");
    api
      .me()
      .then((data) => {
        if (current) setAccount(data);
      })
      .catch((error_) => {
        if (current && !(error_ instanceof ApiError && error_.status === 401))
          setError(errorMessage(error_));
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [attempt]);
  useEffect(() => {
    const navigate = () =>
      setRoute(window.location.hash.slice(1) || "/projects");
    const expire = () => {
      setAccount(null);
      setExpired(true);
    };
    const needProfile = () => {
      const active = document.activeElement as HTMLElement;
      profileFocus.current =
        active && active !== document.body
          ? active
          : document.querySelector<HTMLElement>(
              'main form[aria-busy="true"] button:not([type="button"]):not([type="reset"])',
            );
      setReturnRoute(window.location.hash.slice(1) || "/projects");
      setProfileRequired(true);
    };
    window.addEventListener("profile-required", needProfile);
    window.addEventListener("hashchange", navigate);
    window.addEventListener("session-expired", expire);
    return () => {
      window.removeEventListener("profile-required", needProfile);
      window.removeEventListener("hashchange", navigate);
      window.removeEventListener("session-expired", expire);
    };
  }, []);
  async function logout() {
    setLoggingOut(true);
    setError("");
    try {
      await api.logout();
      setAccount(null);
      setExpired(false);
      window.location.hash = "/projects";
    } catch (error_) {
      if (error_ instanceof ApiError && error_.status === 401) {
        setAccount(null);
        setExpired(true);
      } else setError(errorMessage(error_));
    } finally {
      setLoggingOut(false);
    }
  }
  const detail = /^\/projects\/([^/?]+)(\?created)?$/.exec(route);
  const stageRoute = /^\/projects\/([^/?]+)\/stages\/([^/?]+)$/.exec(route);
  function renderPage() {
    if (loading) {
      return <Notice>Проверяем сессию…</Notice>;
    }
    if (!account) {
      return (
        <Auth
          expired={expired}
          onLogin={(data) => {
            setAccount(data);
            setExpired(false);
            setError("");
          }}
        />
      );
    }
    if (route.startsWith("/profile")) {
      return (
        <Profile
          account={account}
          onAccount={setAccount}
          onContinue={() => {
            setProfileRequired(false);
            window.location.hash = returnRoute;
          }}
        />
      );
    }
    if (route === "/projects/new" && account.role === "CUSTOMER") {
      return <CreateProject />;
    }
    if (stageRoute) {
      return (
        <StageDetail
          key={`${stageRoute[1]}/${stageRoute[2]}`}
          projectId={stageRoute[1]}
          stageId={stageRoute[2]}
          account={account}
        />
      );
    }
    if (detail && detail[1] !== "new") {
      return (
        <ProjectDetail
          key={detail[1]}
          id={detail[1]}
          account={account}
          created={Boolean(detail[2])}
        />
      );
    }
    return <ProjectList account={account} />;
  }
  return (
    <>
      <a
        className="skip-link"
        inert={profileRequired || undefined}
        href="#main-content"
        onClick={(event) => {
          event.preventDefault();
          document.getElementById("main-content")?.focus();
        }}
      >
        К содержимому
      </a>
      <header
        className={account ? "site-header" : "site-header auth-header"}
        inert={profileRequired || undefined}
      >
        <div className="header-inner">
          <div className="brand-suite">
            <a
              className="brand"
              href="#/projects"
              aria-label="СВОЁ СтройКонтроль — мои объекты"
            >
              <span className="brand-mark" aria-hidden="true">
                ⌂
              </span>
              <span>
                <strong>СВОЁ</strong>
                <span>СтройКонтроль</span>
              </span>
            </a>
            <div className="header-bank-mark">
              <BrandMark />
            </div>
          </div>
          {account ? (
            <div className="account-bar">
              <div>
                <a
                  className="account-email profile-link"
                  href="#/profile"
                  onClick={() =>
                    setReturnRoute(
                      route.startsWith("/profile") ? "/projects" : route,
                    )
                  }
                >
                  {account.displayName ||
                    (account.role === "CUSTOMER"
                      ? "Заказчик"
                      : "Подрядчик")}{" "}
                  · профиль
                </a>
                <span className="account-role">
                  {account.role === "CUSTOMER" ? "Заказчик" : "Подрядчик"}
                </span>
              </div>
              <button
                type="button"
                className="button secondary"
                onClick={logout}
                disabled={loggingOut}
              >
                {loggingOut ? "Выходим…" : "Выйти"}
              </button>
            </div>
          ) : (
            <span className="header-caption">Дом начинается с доверия</span>
          )}
        </div>
      </header>
      <main
        id="main-content"
        className={account ? undefined : "auth-main"}
        tabIndex={-1}
        inert={profileRequired || undefined}
      >
        {error && (
          <Notice error>
            {error}{" "}
            {!account && (
              <button
                type="button"
                className="text-button"
                onClick={() => setAttempt(attempt + 1)}
              >
                Повторить подключение
              </button>
            )}
          </Notice>
        )}
        {renderPage()}
      </main>
      {profileRequired && account && (
        <dialog
          open
          className="profile-gate"
          aria-modal="true"
          aria-label="Заполните имя"
        >
          <div className="profile-gate-content">
            <button
              type="button"
              className="button secondary"
              onClick={() => {
                setProfileRequired(false);
                requestAnimationFrame(() => profileFocus.current?.focus());
              }}
            >
              Продолжить просмотр
            </button>
            <Profile
              embedded
              account={account}
              onAccount={setAccount}
              onContinue={() => {
                setProfileRequired(false);
                requestAnimationFrame(() => profileFocus.current?.focus());
              }}
            />
          </div>
        </dialog>
      )}
    </>
  );
}
