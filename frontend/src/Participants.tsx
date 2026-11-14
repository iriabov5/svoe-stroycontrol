import type { components } from "./api/schema";
type Snapshot = components["schemas"]["ActorSnapshot"];
export function ActorLabel({
  snapshot,
  role = "CONTRACTOR",
}: Readonly<{
  snapshot?: Snapshot | null;
  role?: "CUSTOMER" | "CONTRACTOR";
}>) {
  const label = role === "CUSTOMER" ? "Заказчик" : "Подрядчик";
  function actorDetails() {
    if (snapshot?.position) {
      return ` · ${snapshot.position}`;
    }
    if (snapshot?.displayName) {
      return ` · ${snapshot.globalRole === "CUSTOMER" ? "Заказчик" : "Подрядчик"}`;
    }
    return "";
  }
  return (
    <div className="actor-label">
      {snapshot?.organizationName && (
        <strong>{snapshot.organizationName}</strong>
      )}
      <span>
        {snapshot?.displayName || label}
        {actorDetails()}
      </span>
      {snapshot?.legacy && (
        <small className="hint">Ранее, без имени в записи</small>
      )}
    </div>
  );
}
