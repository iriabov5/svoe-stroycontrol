import { useEffect, useState } from "react";
import { errorMessage, request } from "./api/client";
import type { components } from "./api/schema";

export type ControlGates = components["schemas"]["ControlGatesView"];
export type GatePoint = components["schemas"]["ControlGatePoint"];
export type GateTarget = components["schemas"]["ControlGateTarget"];
export type GuidanceState<T> = {
  data: T | null;
  error: string;
  refreshing: boolean;
  reload: () => void;
};

// Сервер владеет правилами; этот hook отвечает только за актуальность чтения.
export function useGuidance<T>(path: string): GuidanceState<T> {
  const [state, setState] = useState<{
    data: T | null;
    error: string;
    refreshing: boolean;
  }>({ data: null, error: "", refreshing: true });
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let active = true,
      generation = 0;
    setState({ data: null, error: "", refreshing: true });
    async function load() {
      const current = ++generation;
      setState((s) => ({ ...s, error: "", refreshing: true }));
      try {
        const data = await request<T>(path);
        if (active && current === generation)
          setState({ data, error: "", refreshing: false });
      } catch (e) {
        if (active && current === generation)
          setState((s) => ({
            ...s,
            error: errorMessage(e),
            refreshing: false,
          }));
      }
    }
    const changed = () => void load();
    const visible = () => {
      if (document.visibilityState === "visible") void load();
    };
    void load();
    window.addEventListener("construction-changed", changed);
    window.addEventListener("hashchange", changed);
    document.addEventListener("visibilitychange", visible);
    return () => {
      active = false;
      generation++;
      window.removeEventListener("construction-changed", changed);
      window.removeEventListener("hashchange", changed);
      document.removeEventListener("visibilitychange", visible);
    };
  }, [path, attempt]);
  return { ...state, reload: () => setAttempt((v) => v + 1) };
}

export function gateLink(target: GateTarget): string {
  const project = encodeURIComponent(target.projectId);
  const path = target.stageId
    ? `/projects/${project}/stages/${encodeURIComponent(target.stageId)}`
    : `/projects/${project}`;
  const query = new URLSearchParams({ section: target.section.toLowerCase() });
  if (target.submissionId) query.set("submissionId", target.submissionId);
  return `#${path}?${query}`;
}

export function useTargetFocus(route: string) {
  useEffect(() => {
    const params = new URLSearchParams(route.split("?")[1] ?? "");
    const section = params.get("section");
    const selectors: Record<string, string> = {
      journal: "#journal-heading",
      acceptance: "#acceptance-title",
      completion: "#completion-title",
      work: "main h1",
      project: "main h1",
    };
    const selector = section ? selectors[section] : null;
    if (!selector) return;
    let done = false;
    let frame: number | undefined;
    const focus = () => {
      let heading = document.querySelector<HTMLElement>(selector);
      const submissionId = params.get("submissionId");
      if (section === "acceptance" && submissionId) {
        const item = Array.from(
          document.querySelectorAll<HTMLElement>("[data-submission-id]"),
        ).find((el) => el.dataset.submissionId === submissionId);
        if (item && !item.classList.contains("acceptance-panel")) {
          const details = item.closest("details");
          if (details) details.open = true;
          heading = item;
        }
      }
      if (!heading || done) return;
      if (
        section === "acceptance" &&
        !document.querySelector("[data-acceptance-loaded='true']")
      )
        return;
      if (
        section === "journal" &&
        !document.querySelector("[data-journal-loaded='true']")
      )
        return;
      if (
        section === "completion" &&
        !document.querySelector("[data-completion-loaded='true']")
      )
        return;
      done = true;
      const target = heading;
      observer.disconnect();
      frame = requestAnimationFrame(() => {
        if (!target.isConnected) return;
        target.tabIndex = -1;
        target.scrollIntoView?.({ block: "center" });
        target.focus({ preventScroll: true });
      });
    };
    const observer = new MutationObserver(focus);
    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: [
        "data-acceptance-loaded",
        "data-journal-loaded",
        "data-completion-loaded",
      ],
    });
    focus();
    return () => {
      observer.disconnect();
      if (frame !== undefined) cancelAnimationFrame(frame);
    };
  }, [route]);
}
