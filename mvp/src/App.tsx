import { useEffect, useState } from "react";
import {
  BrowserRouter,
  Link,
  NavLink,
  Route,
  Routes,
  useLocation,
} from "react-router-dom";
import {
  House,
  Leaf,
  LogIn,
  UserRound,
  ChevronDown,
  LogOut,
  FileText,
  Menu,
  X,
  MapPin,
  Calculator,
  CalendarDays,
  ShieldCheck,
} from "lucide-react";
import { APP_NAME, privatePhone } from "./domain";
import { AppProvider, useApp } from "./store";
import { Home } from "./Home";
import { Estimate } from "./Estimate";
import { MortgageCare } from "./MortgageCare";
import { Insurance } from "./Insurance";
const navigation = [
  { to: "/", name: "Проверка участка", short: "Участок", icon: MapPin },
  { to: "/estimate", name: "Смета стройки", short: "Смета", icon: Calculator },
  {
    to: "/mortgage-care",
    name: "Анти-штраф",
    short: "Календарь",
    icon: CalendarDays,
  },
  { to: "/insurance", name: "Страхование", short: "Защита", icon: ShieldCheck },
];
function Brand() {
  return (
    <Link to="/" className="brand" aria-label={`${APP_NAME} — главная`}>
      <span className="brand-symbol">
        <House size={26} strokeWidth={1.9} />
        <Leaf size={12} className="brand-leaf" />
      </span>
      <span>
        {APP_NAME}
        <small>МЕСТО ДЛЯ ВАШЕЙ ЖИЗНИ</small>
      </span>
    </Link>
  );
}
function Shell() {
  const { user, requireAuth, logout, openApplications } = useApp();
  const [menu, setMenu] = useState(false);
  const [profile, setProfile] = useState(false);
  const location = useLocation();
  useEffect(() => {
    setMenu(false);
    setProfile(false);
    window.scrollTo({ top: 0, behavior: "instant" });
    document.title = `${navigation.find((n) => n.to === location.pathname)?.name ?? "Главная"} — ${APP_NAME}`;
  }, [location.pathname]);
  return (
    <>
      <a className="skip-link" href="#main">
        Перейти к содержимому
      </a>
      <header className="site-header">
        <div className="header-inner">
          <Brand />
          <nav className="desktop-nav" aria-label="Основная навигация">
            {navigation.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.to === "/"}>
                {n.name}
              </NavLink>
            ))}
          </nav>
          <div className="header-actions">
            {user ? (
              <div className="profile-menu">
                <button
                  className="button profile-button"
                  aria-expanded={profile}
                  onClick={() => setProfile(!profile)}
                >
                  <UserRound size={19} />
                  <span>{privatePhone(user.phone)}</span>
                  <ChevronDown size={16} />
                </button>
                {profile && (
                  <>
                    <button
                      className="menu-dismiss"
                      tabIndex={-1}
                      aria-label="Закрыть меню профиля"
                      onClick={() => setProfile(false)}
                    />
                    <div className="profile-dropdown">
                      {user.fullName && <strong>{user.fullName}</strong>}
                      <button
                        onClick={() => {
                          setProfile(false);
                          openApplications();
                        }}
                      >
                        <FileText size={17} /> Мои заявки
                      </button>
                      <button
                        onClick={() => {
                          setProfile(false);
                          logout();
                        }}
                      >
                        <LogOut size={17} /> Выйти
                      </button>
                    </div>
                  </>
                )}
              </div>
            ) : (
              <button
                className="button login-button"
                onClick={() =>
                  requireAuth("Войдите в своё пространство", () => {})
                }
              >
                <LogIn size={17} /> Войти
              </button>
            )}
            <button
              className="icon-button mobile-menu-button"
              onClick={() => setMenu(!menu)}
              aria-expanded={menu}
              aria-label={menu ? "Закрыть меню" : "Открыть меню"}
            >
              {menu ? <X /> : <Menu />}
            </button>
          </div>
        </div>
        {menu && (
          <nav className="mobile-menu" aria-label="Мобильное меню">
            {navigation.map((n) => (
              <NavLink key={n.to} to={n.to} end={n.to === "/"}>
                <n.icon size={19} />
                {n.name}
              </NavLink>
            ))}
          </nav>
        )}
      </header>
      <main id="main" className="container">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/estimate" element={<Estimate />} />
          <Route path="/mortgage-care" element={<MortgageCare />} />
          <Route path="/insurance" element={<Insurance />} />
          <Route
            path="*"
            element={
              <div className="empty-state">
                <House size={48} />
                <h1>Здесь пока нет дома</h1>
                <p>Такой страницы не существует.</p>
                <Link className="button primary" to="/">
                  На главную
                </Link>
              </div>
            }
          />
        </Routes>
      </main>
      <footer className="site-footer">
        <div className="container">
          <div className="footer-top">
            <div>
              <Brand />
              <p>От мечты о доме — к своей жизни за городом.</p>
            </div>
            <span className="footer-promise">
              <Leaf size={19} /> Растём вместе с вашими планами
            </span>
          </div>
          <div className="footer-bottom">
            <span>
              © {new Date().getFullYear()} {APP_NAME}
            </span>
            <p>
              Демонстрационный сервис. Данные хранятся в этом браузере.
              <br />
              Проверки, заявки, вход и оплата имитируются.
            </p>
            <span className="badge neutral">MVP · ИТ Чемпионат</span>
          </div>
          <p className="legal-footer">
            Сервис входит в экосистему Россельхозбанка (АО «Россельхозбанк»).
            Генеральная лицензия Банка России № 3349
          </p>
        </div>
      </footer>
      <nav className="bottom-nav" aria-label="Быстрые разделы">
        {navigation.map((n) => (
          <NavLink key={n.to} to={n.to} end={n.to === "/"}>
            <n.icon size={21} />
            <span>{n.short}</span>
          </NavLink>
        ))}
      </nav>
    </>
  );
}
export default function App() {
  return (
    <BrowserRouter>
      <AppProvider>
        <Shell />
      </AppProvider>
    </BrowserRouter>
  );
}
