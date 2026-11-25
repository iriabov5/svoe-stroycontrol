export const APP_NAME = "Своё Село";
export interface PlotCheckResult {
  cadastralNumber: string;
  address: string;
  region: string;
  landCategory: string;
  permittedUse: string;
  isEligible: boolean;
  rate: 3 | 0.1 | null;
  rejectionReason?: string;
}
export interface EstimateItem {
  id: string;
  name: string;
  plannedAmount: number;
  actualAmount: number;
}
export interface EstimateStage {
  id: string;
  title: string;
  isCompleted: boolean;
  items: EstimateItem[];
}
export type ObligationStatus =
  "pending" | "under_review" | "approved" | "overdue";
export interface MortgageObligation {
  id: string;
  type: "employment" | "registration" | "construction_end" | "insurance";
  title: string;
  description: string;
  dueDate: string;
  status: ObligationStatus;
  uploadedFileName?: string;
  fileData?: string;
  fileType?: string;
  policyId?: string;
}
export interface UserProfile {
  isAuthenticated: boolean;
  phone: string;
  fullName?: string;
  authProvider: "phone" | "gosuslugi";
  isVerifiedGosuslugi: boolean;
  savedPlotChecks: string[];
}
export interface MortgageSetup {
  signedAt: string;
  objectType: "ready" | "construction";
  isVeteran: boolean;
}
export interface Application {
  id: string;
  kind: string;
  name: string;
  phone: string;
  date: string;
}
export interface Policy {
  id: string;
  kind: "mortgage" | "construction";
  premium: number;
  insuredAmount: number;
  createdAt: string;
  expiresAt: string;
  obligationId?: string;
}
export interface EstimateArchive {
  id: string;
  name: string;
  stages: EstimateStage[];
}
export interface AppData {
  version: 1;
  estimate: EstimateStage[];
  estimateName: string;
  estimateArchives: EstimateArchive[];
  plotChecks: PlotCheckResult[];
  mortgageSetup: MortgageSetup | null;
  mortgageDraft: MortgageSetup;
  mortgageDraftTouched?: boolean;
  insuranceTouched?: boolean;
  obligations: MortgageObligation[];
  notifications: boolean;
  applications: Application[];
  policies: Policy[];
  insurance: {
    amount: number;
    material: "stone" | "wood";
    age: number;
    life: boolean;
  };
}
export const uid = () => crypto.randomUUID();
export const money = (n: number) =>
  new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    maximumFractionDigits: 0,
  }).format(n);
export const number = (n: number) =>
  new Intl.NumberFormat("ru-RU", { maximumFractionDigits: 0 }).format(n);
export function dateISO(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
export const today = () => dateISO(new Date());
export const dateLabel = (value: string) =>
  new Date(value + "T12:00:00").toLocaleDateString("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
export function addMonths(value: string, count: number) {
  const d = new Date(value + "T12:00:00");
  const day = d.getDate();
  d.setDate(1);
  d.setMonth(d.getMonth() + count);
  const last = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
  d.setDate(Math.min(day, last));
  return dateISO(d);
}
export function addDays(value: string, count: number) {
  const d = new Date(value + "T12:00:00");
  d.setDate(d.getDate() + count);
  return dateISO(d);
}
export function daysLeft(value: string, from = today()) {
  const utc = (v: string) => {
    const [y, m, d] = v.split("-").map(Number);
    return Date.UTC(y, m - 1, d);
  };
  return Math.round((utc(value) - utc(from)) / 86400000);
}
export function blankData(): AppData {
  return {
    version: 1,
    estimate: [],
    estimateName: "",
    estimateArchives: [],
    plotChecks: [],
    mortgageSetup: null,
    mortgageDraft: {
      signedAt: today(),
      objectType: "construction",
      isVeteran: false,
    },
    obligations: [],
    notifications: false,
    applications: [],
    policies: [],
    insurance: { amount: 4000000, material: "stone", age: 35, life: true },
  };
}
export const plotExamples: PlotCheckResult[] = [
  {
    cadastralNumber: "50:21:0040112:105",
    address: "Московская область, Ленинский городской округ",
    region: "Московская область",
    landCategory: "Земли населённых пунктов",
    permittedUse: "ИЖС",
    isEligible: false,
    rate: null,
    rejectionReason:
      "В демосценарии Московская область входит в список регионов-исключений программы «Сельская ипотека».",
  },
  {
    cadastralNumber: "16:24:070801:412",
    address: "Республика Татарстан, Лаишевский район, село Сокуры",
    region: "Республика Татарстан",
    landCategory: "Земли населённых пунктов",
    permittedUse: "ИЖС",
    isEligible: true,
    rate: 3,
  },
  {
    cadastralNumber: "31:15:1203001:88",
    address: "Белгородская область, приграничная сельская территория",
    region: "Белгородская область",
    landCategory: "Земли населённых пунктов",
    permittedUse: "ЛПХ",
    isEligible: true,
    rate: 0.1,
  },
];
export function maskCadastre(value: string) {
  const parts = value
    .replace(/[^0-9:]/g, "")
    .split(":")
    .slice(0, 4);
  for (let i = 0; i < 3; i++) {
    const width = i < 2 ? 2 : 7;
    if ((parts[i]?.length ?? 0) > width) {
      const rest = parts[i].slice(width);
      parts[i] = parts[i].slice(0, width);
      parts[i + 1] = rest + (parts[i + 1] ?? "");
    }
  }
  if (parts[3]) parts[3] = parts[3].slice(0, 7);
  return parts.join(":");
}
export const validCadastre = (s: string) =>
  /^\d{2}:\d{2}:\d{6,7}:\d{1,7}$/.test(s);
export function maskPhone(value: string) {
  let n = value.replace(/\D/g, "");
  if (!n) return value.includes("+") ? "+" : "";
  if (n[0] === "7" || n[0] === "8") n = n.slice(1);
  n = n.slice(0, 10);
  return (
    "+7" +
    (n ? " (" + n.slice(0, 3) : "") +
    (n.length >= 3 ? ") " : "") +
    n.slice(3, 6) +
    (n.length > 6 ? "-" + n.slice(6, 8) : "") +
    (n.length > 8 ? "-" + n.slice(8, 10) : "")
  );
}
export const normalizePhone = (s: string) =>
  "+7" + s.replace(/\D/g, "").slice(-10);
export const validPhone = (s: string) => /^7\d{10}$/.test(s.replace(/\D/g, ""));
export const privatePhone = (s: string) =>
  `+7 (${s.slice(2, 5)}) •••-${s.slice(-4, -2)}-${s.slice(-2)}`;
export function payment(principal: number, rate: number, years: number) {
  const n = years * 12,
    r = rate / 1200;
  return r ? (principal * r) / (1 - (1 + r) ** -n) : principal / n;
}
export const templates = [
  {
    id: "frame",
    name: "Каркасный дом",
    area: 100,
    budget: 4500000,
    tag: "Быстрый старт",
    material: "wood" as const,
    description: "Тёплый и лёгкий. Для жизни за городом.",
  },
  {
    id: "blocks",
    name: "Дом из газобетона",
    area: 120,
    budget: 6200000,
    tag: "Золотая середина",
    material: "stone" as const,
    description: "Комфорт для семьи и простор для планов.",
  },
  {
    id: "brick",
    name: "Кирпичный дом",
    area: 150,
    budget: 8500000,
    tag: "На поколения",
    material: "stone" as const,
    description: "Основательный дом с запасом пространства.",
  },
];
const stageDefinitions = [
  [
    "foundation",
    "Фундамент",
    0.2,
    [
      "Подготовка участка и земляные работы",
      "Материалы и устройство фундамента",
    ],
  ],
  ["walls", "Коробка", 0.27, ["Стены и перегородки", "Перекрытия и монтаж"]],
  ["roof", "Кровля", 0.16, ["Стропильная система", "Кровельное покрытие"]],
  ["windows", "Окна и двери", 0.1, ["Окна и входная дверь", "Установка"]],
  [
    "utilities",
    "Инженерия",
    0.14,
    ["Электрика, вода и отопление", "Монтаж оборудования"],
  ],
  [
    "finish",
    "Отделка",
    0.13,
    ["Внутренняя отделка", "Фасад и благоустройство"],
  ],
] as const;
export function makeEstimate(budget: number): EstimateStage[] {
  return stageDefinitions.map(([id, title, share, names]) => ({
    id,
    title,
    isCompleted: false,
    items: names.map((name, i) => ({
      id: uid(),
      name,
      plannedAmount: Math.round(budget * share * (i ? 0.65 : 0.35)),
      actualAmount: 0,
    })),
  }));
}
export function totals(stages: EstimateStage[]) {
  return stages.reduce(
    (a, s) =>
      s.items.reduce(
        (b, i) => ({
          planned: b.planned + i.plannedAmount,
          actual: b.actual + i.actualAmount,
        }),
        a,
      ),
    { planned: 0, actual: 0 },
  );
}
export function overrun(stages: EstimateStage[]) {
  const all = totals(stages);
  const exceeds = (p: number, a: number) => a > p && a - p + 0.000001 >= p / 10;
  if (exceeds(all.planned, all.actual))
    return { amount: all.actual - all.planned, title: "по всей смете" };
  const s = stages.find((s) => {
    const t = totals([s]);
    return exceeds(t.planned, t.actual);
  });
  if (!s) return null;
  const t = totals([s]);
  return { amount: t.actual - t.planned, title: `на этапе «${s.title}»` };
}
export function generateObligations(
  setup: MortgageSetup,
): MortgageObligation[] {
  const list: MortgageObligation[] = [];
  const add = (
    type: MortgageObligation["type"],
    title: string,
    description: string,
    dueDate: string,
  ) =>
    list.push({
      id: `${type}-${dueDate}`,
      type,
      title,
      description,
      dueDate,
      status: "pending",
    });
  if (!setup.isVeteran)
    for (let n = 6; n <= 60; n += 6)
      add(
        "employment",
        "Подтвердить занятость",
        "Выписка СТД-СФР с Госуслуг в формате PDF. Для демосценария работы в АПК или социальной сфере.",
        addMonths(setup.signedAt, n),
      );
  add(
    "registration",
    "Подтвердить прописку",
    "Страница паспорта с постоянной регистрацией или справка о регистрации по месту жительства.",
    addDays(setup.signedAt, 180),
  );
  if (setup.objectType === "construction")
    add(
      "construction_end",
      "Завершить строительство",
      "Выписка ЕГРН о зарегистрированном жилом доме или документ о вводе дома в эксплуатацию.",
      addMonths(setup.signedAt, 24),
    );
  for (let n = 12; n <= 60; n += 12)
    add(
      "insurance",
      "Продлить страхование",
      "Действующий полис страхования конструктива и подтверждение оплаты.",
      addMonths(setup.signedAt, n),
    );
  return list.sort(
    (a, b) =>
      a.dueDate.localeCompare(b.dueDate) || a.type.localeCompare(b.type),
  );
}
export function effectiveStatus(o: MortgageObligation): ObligationStatus {
  return o.status === "approved" || o.status === "under_review"
    ? o.status
    : daysLeft(o.dueDate) < 0
      ? "overdue"
      : "pending";
}
export function insurancePrice(
  amount: number,
  material: "stone" | "wood",
  life: boolean,
  kind: "mortgage" | "construction",
) {
  const structure = amount * 0.0025 * (material === "wood" ? 1.4 : 1);
  return {
    structure,
    life: kind === "mortgage" && life ? amount * 0.003 : 0,
    total: structure + (kind === "mortgage" && life ? amount * 0.003 : 0),
  };
}
function union<T>(a: T[], b: T[], key: (v: T) => string) {
  return [...new Map([...a, ...b].map((v) => [key(v), v])).values()];
}
export function mergeGuest(user: AppData, guest: AppData): AppData {
  const hasEstimate = guest.estimate.length > 0;
  let archives = union(
    user.estimateArchives,
    guest.estimateArchives,
    (x) => x.id,
  );
  if (
    hasEstimate &&
    user.estimate.length &&
    JSON.stringify(user.estimate) !== JSON.stringify(guest.estimate)
  )
    archives = [
      ...archives,
      {
        id: uid(),
        name: user.estimateName || "Сохранённая смета",
        stages: user.estimate,
      },
    ];
  return {
    ...user,
    estimate: hasEstimate ? guest.estimate : user.estimate,
    estimateName: hasEstimate ? guest.estimateName : user.estimateName,
    estimateArchives: archives,
    plotChecks: union(
      user.plotChecks,
      guest.plotChecks,
      (x) => x.cadastralNumber,
    ),
    mortgageSetup: guest.mortgageSetup ?? user.mortgageSetup,
    mortgageDraft: guest.mortgageDraftTouched
      ? guest.mortgageDraft
      : user.mortgageDraft,
    obligations: union(guest.obligations, user.obligations, (x) => x.id),
    applications: union(user.applications, guest.applications, (x) => x.id),
    policies: union(user.policies, guest.policies, (x) => x.id),
    insurance:
      guest.insuranceTouched || hasEstimate ? guest.insurance : user.insurance,
  };
}
export function applyPolicy(data: AppData, policy: Policy): AppData {
  if (policy.kind === "construction")
    return { ...data, policies: [...data.policies, policy] };
  const target =
    data.obligations.find(
      (o) =>
        o.type === "insurance" &&
        o.status !== "approved" &&
        o.id === policy.obligationId,
    ) ??
    data.obligations
      .filter((o) => o.type === "insurance" && o.status !== "approved")
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0];
  return {
    ...data,
    policies: [...data.policies, { ...policy, obligationId: target?.id }],
    obligations: data.obligations.map((o) =>
      o.id === target?.id
        ? { ...o, status: "approved", policyId: policy.id }
        : o,
    ),
  };
}
export function estimateCSV(stages: EstimateStage[]): string {
  const cell = (s: string) =>
    '"' + (/^[=+@\-\t\r]/.test(s) ? "'" : "") + s.replaceAll('"', '""') + '"';
  return (
    "\uFEFF" +
    [
      "Этап;Позиция;План (₽);Факт (₽);Завершён",
      ...stages.flatMap((s) =>
        s.items.map((i) =>
          [
            cell(s.title),
            cell(i.name),
            i.plannedAmount,
            i.actualAmount,
            s.isCompleted ? "Да" : "Нет",
          ].join(";"),
        ),
      ),
    ].join("\r\n")
  );
}
export function download(name: string, body: string, type: string) {
  const url = URL.createObjectURL(new Blob([body], { type }));
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export function validateFile(file: File) {
  if (!["application/pdf", "image/jpeg", "image/png"].includes(file.type))
    return "Выберите PDF, JPG или PNG.";
  if (file.size > 1.5 * 1024 * 1024)
    return "Файл слишком большой. Максимум — 1,5 МБ для локального демо.";
  if (!file.size) return "Файл пустой. Выберите другой документ.";
  return "";
}
