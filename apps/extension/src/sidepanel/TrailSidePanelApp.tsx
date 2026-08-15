import type { CSSProperties, ReactElement } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import type { ProblemSessionResponsePayload, RecallSearchResultPayload, RecallStatePayload } from "@trail/extension-messages";
import { TRAIL_PRODUCT_NAME, trailColors, trailRadii, trailSpacing, trailTypography } from "@trail/ui";
import {
  ChromeLocalSidePanelRouteIntentStorage,
  type SidePanelRouteIntent,
  type SidePanelRouteIntentStorage
} from "../storage/sidepanel";
import {
  EndSessionOutcomeStep,
  RuntimeEndSessionProblemSessionClient,
  type EndSessionProblemSessionClient
} from "./problem-session";
import {
  GhostReplayPanel,
  RecallSearchPanel,
  RuntimeSidePanelRecallClient,
  SimilarProblemFoundCard,
  SolvedProblemDetailPanel,
  type SidePanelRecallClient
} from "./recall";
import { TimelinePanel, type TimelineRow } from "./timeline";

type SidePanelView = "current" | "home" | "recall" | "settings";

export interface TrailSidePanelAppProps {
  readonly initialProblemSessionState?: ProblemSessionResponsePayload | null;
  readonly initialRecallState?: RecallStatePayload | null;
  readonly initialRouteIntent?: SidePanelRouteIntent | null;
  readonly problemSessionClient?: EndSessionProblemSessionClient;
  readonly recallClient?: SidePanelRecallClient;
  readonly routeIntentStorage?: SidePanelRouteIntentStorage;
}

export function TrailSidePanelApp(props: TrailSidePanelAppProps): ReactElement {
  const problemSessionClient = useMemo(
    () => props.problemSessionClient ?? new RuntimeEndSessionProblemSessionClient(),
    [props.problemSessionClient]
  );

  const recallClient = useMemo(
    () => props.recallClient ?? new RuntimeSidePanelRecallClient(),
    [props.recallClient]
  );

  const routeIntentStorage = useMemo(
    () =>
      props.routeIntentStorage ??
      new ChromeLocalSidePanelRouteIntentStorage(),
    [props.routeIntentStorage]
  );

  const [problemSessionState, setProblemSessionState] =
    useState<ProblemSessionResponsePayload | null>(
      props.initialProblemSessionState ?? null
    );

  const [recallState, setRecallState] =
    useState<RecallStatePayload | null>(
      props.initialRecallState ?? null
    );

  const [routeIntent, setRouteIntent] =
    useState<SidePanelRouteIntent | null>(
      props.initialRouteIntent ?? null
    );
  const [sidePanelView, setSidePanelView] = useState<SidePanelView>(() =>
    initialSidePanelView(props.initialRouteIntent ?? null, props.initialRecallState ?? null)
  );

  const [recallView, setRecallView] =
    useState<"ghost" | "similar" | "timeline">("similar");

  const [loading, setLoading] = useState(
    props.initialProblemSessionState === undefined &&
      props.initialRecallState === undefined
  );

  const [error, setError] = useState(false);

  useEffect(() => {
    let active = true;

    void Promise.all([
      problemSessionClient.getState(),
      recallClient.getState(),
      routeIntentStorage.read()
    ]).then(([problemResult, recallResult, routeIntentResult]) => {
      if (!active) {
        return;
      }

      if (problemResult.status === "received") {
        setProblemSessionState(problemResult.payload);
        setError(false);
      } else {
        setError(true);
      }

      if (recallResult.status === "received") {
        setRecallState(recallResult.payload);
      }

      setRouteIntent(routeIntentResult);
      if (routeIntentResult === "recall") {
        setSidePanelView("recall");
      }
      setLoading(false);
    });

    return () => {
      active = false;
    };
  }, [problemSessionClient, recallClient, routeIntentStorage]);

  useEffect(() => {
    if (
      routeIntent !== "end_session" ||
      problemSessionState?.state.activeSession?.status !== "CANDIDATE"
    ) {
      return;
    }

    let active = true;
    const timeout = setTimeout(() => {
      void problemSessionClient.getState().then((result) => {
        if (!active || result.status !== "received") {
          return;
        }

        setProblemSessionState(result.payload);
      });
    }, 250);

    return () => {
      active = false;
      clearTimeout(timeout);
    };
  }, [problemSessionClient, problemSessionState, routeIntent]);

  if (loading && problemSessionState === null) {
    return (
      <main aria-label="Trail side panel" style={styles.shell}>
        <header style={styles.header}>
          <p style={styles.eyebrow}>{TRAIL_PRODUCT_NAME}</p>
          <h1 style={styles.title}>Opening Recall…</h1>
        </header>

        <span aria-hidden="true" style={styles.loadingLine} />
      </main>
    );
  }

  if (problemSessionState === null || error) {
    return (
      <main aria-label="Trail side panel" style={styles.shell}>
        <section
          aria-labelledby="trail-unavailable-title"
          style={styles.noticeCard}
        >
          <p style={styles.eyebrow}>{TRAIL_PRODUCT_NAME}</p>

          <h1 id="trail-unavailable-title" style={styles.title}>
            Trail unavailable
          </h1>

          <p role="alert" style={styles.body}>
            Open Trail again. Your local session state remains on this device.
          </p>
        </section>
      </main>
    );
  }

  if (
    routeIntent === "end_session" &&
    problemSessionState.state.activeSession?.status === "CANDIDATE"
  ) {
    return (
      <main aria-label="Trail end session" style={styles.shell}>
        <section
          aria-labelledby="trail-finishing-title"
          style={styles.noticeCard}
        >
          <p style={styles.eyebrow}>{TRAIL_PRODUCT_NAME}</p>
          <h1 id="trail-finishing-title" style={styles.title}>
            Finishing problem…
          </h1>
          <p style={styles.body}>
            Closing the capture boundary before asking what happened.
          </p>
        </section>
      </main>
    );
  }

  if (
    routeIntent === "end_session" ||
    problemSessionState.state.activeSession?.status === "REVIEWABLE"
  ) {
    return (
      <EndSessionOutcomeStep
        client={problemSessionClient}
        initialProblemSessionState={problemSessionState}
      />
    );
  }

  if (sidePanelView === "settings") {
    return (
      <SidePanelShell
        activeView={sidePanelView}
        onNavigate={setSidePanelView}
      >
        <SettingsPanel />
      </SidePanelShell>
    );
  }

  if (sidePanelView === "current") {
    return (
      <SidePanelShell
        activeView={sidePanelView}
        onNavigate={setSidePanelView}
      >
        <CurrentProblemPanel
          onFinish={async () => {
            const result = await problemSessionClient.finish();
            if (result.status === "received") {
              setProblemSessionState(result.payload);
              setRouteIntent("end_session");
            }
          }}
          session={problemSessionState.state.activeSession}
        />
      </SidePanelShell>
    );
  }

  if (sidePanelView === "recall" && isRecallSearchRoute(recallState)) {
    return (
      <SidePanelShell
        activeView={sidePanelView}
        onNavigate={setSidePanelView}
      >
        <RecallRouteView
          onBackToSolution={() => setRecallView("similar")}
          currentContextTitle={
            problemSessionState.state.activeSession?.title ?? null
          }
          onFullTrail={() => setRecallView("timeline")}
          onRecallStateChanged={setRecallState}
          onOpenResource={async (resourceId) => {
            const result = await recallClient.openResource(resourceId);

            if (result.status !== "received") {
              return false;
            }

            setRecallState(result.payload);

            return hasOpenedResource(result.payload, resourceId);
          }}
          onReplay={() => setRecallView("ghost")}
          recallClient={recallClient}
          state={recallState}
          view={recallView}
        />
      </SidePanelShell>
    );
  }

  if (sidePanelView === "recall" && isInvokedRecallState(recallState)) {
    return (
      <SidePanelShell
        activeView={sidePanelView}
        onNavigate={setSidePanelView}
      >
        <RecallRouteView
          onBackToSolution={() => setRecallView("similar")}
          currentContextTitle={
            problemSessionState.state.activeSession?.title ?? null
          }
          onFullTrail={() => setRecallView("timeline")}
          onRecallStateChanged={setRecallState}
          onOpenResource={async (resourceId) => {
            const result = await recallClient.openResource(resourceId);

            if (result.status !== "received") {
              return false;
            }

            setRecallState(result.payload);

            return hasOpenedResource(result.payload, resourceId);
          }}
          onReplay={() => setRecallView("ghost")}
          recallClient={recallClient}
          state={recallState}
          view={recallView}
        />
      </SidePanelShell>
    );
  }

  return (
    <SidePanelShell
      activeView={sidePanelView}
      onNavigate={setSidePanelView}
    >
      <HomePanel
        activeSession={problemSessionState.state.activeSession}
        onCurrent={() => setSidePanelView("current")}
        onRecall={() => setSidePanelView("recall")}
      />
    </SidePanelShell>
  );
}

function initialSidePanelView(
  routeIntent: SidePanelRouteIntent | null,
  recallState: RecallStatePayload | null
): SidePanelView {
  if (routeIntent === "recall") {
    return "recall";
  }

  if (
    recallState !== null &&
    (recallState.source !== null || recallState.route.route !== "search")
  ) {
    return "recall";
  }

  return "home";
}

function SidePanelShell(props: {
  readonly activeView: SidePanelView;
  readonly children: ReactElement;
  readonly onNavigate: (view: SidePanelView) => void;
}): ReactElement {
  return (
    <main aria-label="Trail side panel" style={styles.shell}>
      <nav aria-label="Trail side panel navigation" style={styles.nav}>
        {(["home", "recall", "current", "settings"] as const).map((view) => (
          <button
            aria-current={props.activeView === view ? "page" : undefined}
            key={view}
            onClick={() => props.onNavigate(view)}
            style={props.activeView === view ? styles.navButtonActive : styles.navButton}
            type="button"
          >
            {viewLabel(view)}
          </button>
        ))}
      </nav>
      {props.children}
    </main>
  );
}

function HomePanel(props: {
  readonly activeSession: ProblemSessionResponsePayload["state"]["activeSession"];
  readonly onCurrent: () => void;
  readonly onRecall: () => void;
}): ReactElement {
  return (
    <section aria-labelledby="trail-home-title" style={styles.noticeCard}>
      <p style={styles.eyebrow}>{TRAIL_PRODUCT_NAME}</p>
      <h1 id="trail-home-title" style={styles.title}>Your engineering memory</h1>
      <p style={styles.body}>Search solved problems, replay resources, or finish the current Problem Session without closing this panel.</p>
      <div style={styles.navActions}>
        <button onClick={props.onRecall} style={styles.primaryAction} type="button">Open Recall</button>
        <button disabled={props.activeSession === null} onClick={props.onCurrent} style={props.activeSession === null ? styles.disabledAction : styles.secondaryAction} type="button">
          Current Problem
        </button>
      </div>
    </section>
  );
}

function CurrentProblemPanel(props: {
  readonly onFinish: () => Promise<void>;
  readonly session: ProblemSessionResponsePayload["state"]["activeSession"];
}): ReactElement {
  if (props.session === null) {
    return (
      <section aria-labelledby="current-problem-title" style={styles.noticeCard}>
        <p style={styles.eyebrow}>Current Problem</p>
        <h1 id="current-problem-title" style={styles.title}>No active problem</h1>
        <p style={styles.body}>Start a titled Problem Session from the popup when debugging work begins.</p>
      </section>
    );
  }

  return (
    <section aria-labelledby="current-problem-title" style={styles.noticeCard}>
      <p style={styles.eyebrow}>Current Problem</p>
      <h1 id="current-problem-title" style={styles.title}>{props.session.title}</h1>
      <p style={styles.body}>{props.session.status === "CANDIDATE" ? "Capture is active for this problem." : "Capture is finished. Choose what happened next."}</p>
      <button disabled={props.session.status !== "CANDIDATE"} onClick={() => void props.onFinish()} style={props.session.status === "CANDIDATE" ? styles.primaryAction : styles.disabledAction} type="button">
        Finish problem
      </button>
    </section>
  );
}

function SettingsPanel(): ReactElement {
  return (
    <section aria-labelledby="trail-settings-title" style={styles.noticeCard}>
      <p style={styles.eyebrow}>Settings</p>
      <h1 id="trail-settings-title" style={styles.title}>Privacy-first by default</h1>
      <p style={styles.body}>The extension keeps only lightweight controls here. The future dashboard will own account, export, analytics, and advanced settings.</p>
      <p style={styles.body}>Use the popup’s Privacy & Consent entry to revisit capture consent.</p>
    </section>
  );
}

function viewLabel(view: SidePanelView): string {
  switch (view) {
    case "current":
      return "Current";
    case "home":
      return "Home";
    case "recall":
      return "Recall";
    case "settings":
      return "Settings";
  }
}

function RecallRouteView(props: {
  readonly currentContextTitle: string | null;
  readonly onBackToSolution: () => void;
  readonly onFullTrail: () => void;
  readonly onRecallStateChanged: (state: RecallStatePayload) => void;
  readonly onOpenResource: (resourceId: string) => Promise<boolean>;
  readonly onReplay: () => void;
  readonly recallClient: SidePanelRecallClient;
  readonly state: RecallStatePayload;
  readonly view: "ghost" | "similar" | "timeline";
}): ReactElement {

  const handleSearch = useCallback(
    async (query: string | null) => {
      const result = await props.recallClient.search(query);

      if (result.status === "received") {
        return result.payload;
      }

      return {
        degradationMode: null,
        query,
        results: [],
        status: "unavailable" as const
      };
    },
    [props.recallClient]
  );

  const handleOpenResult = useCallback(
    async (result: RecallSearchResultPayload) => {
      const opened = await props.recallClient.openSolvedProblem(result);
      if (opened.status !== "received") {
        return;
      }
      props.onRecallStateChanged(opened.payload);
      props.onBackToSolution();
    },
    [props]
  );

  if (props.state.route.route === "solved_problem") {
    if (props.view === "timeline") {
      return (
        <section aria-label="Trail Timeline" style={styles.routeSection}>
          <TimelinePanel
            mode="historical"
            onBack={props.onBackToSolution}
            problemTitle={props.state.route.title ?? "Solved problem"}
            rows={timelineRowsFromResources(props.state.route.resources)}
          />
        </section>
      );
    }

    return (
      <section aria-label="Trail solved problem" style={styles.routeSection}>
        {props.view === "ghost" ? (
          <GhostReplayPanel
            onBackToSolution={props.onBackToSolution}
            onFullTrail={props.onFullTrail}
            onOpenResource={props.onOpenResource}
            recommendation={recommendationFromSolvedProblem(props.state.route)}
          />
        ) : (
          <SolvedProblemDetailPanel
            finalSolution={props.state.route.finalSolution}
            onFullTrail={props.onFullTrail}
            onOpenResource={props.onOpenResource}
            onReplay={props.onReplay}
            resources={props.state.route.resources}
            {...(props.state.route.title === undefined ? {} : { title: props.state.route.title })}
            updatedAt={props.state.route.updatedAt}
          />
        )}
      </section>
    );
  }

  if (props.state.route.route === "similar_problem") {
    if (props.view === "timeline") {
      return (
        <section aria-label="Trail Timeline" style={styles.routeSection}>
          <TimelinePanel
            mode="ghost"
            onBack={props.onBackToSolution}
            problemTitle={
              props.state.route.recommendation.title ??
              "Previous solved problem"
            }
            rows={timelineRowsFromRecommendation(
              props.state.route.recommendation.resources ?? []
            )}
          />
        </section>
      );
    }

    return (
      <section aria-label="Trail Recall" style={styles.routeSection}>
        {props.view === "ghost" ? (
          <GhostReplayPanel
            onBackToSolution={props.onBackToSolution}
            onFullTrail={props.onFullTrail}
            onOpenResource={props.onOpenResource}
            recommendation={props.state.route.recommendation}
          />
        ) : (
          <SimilarProblemFoundCard
            onReplay={props.onReplay}
            recommendation={props.state.route.recommendation}
          />
        )}
      </section>
    );
  }

  return (
    <section aria-label="Trail Recall search" style={styles.routeSection}>
      <RecallSearchPanel
        currentContextTitle={props.currentContextTitle}
        initialSearchResult={emptySearchResult(props.state.route.reason)}
        onOpenResult={handleOpenResult}
        onSearch={handleSearch}
        surface="side-panel"
      />
    </section>
  );
}

function timelineRowsFromRecommendation(
  resources: NonNullable<
    Extract<
      RecallStatePayload["route"],
      { route: "similar_problem" }
    >["recommendation"]["resources"]
  >
): readonly TimelineRow[] {
  return resources.map((resource) => ({
    decisive: resource.decisive,
    hostname: resource.hostname ?? null,
    id: resource.id,
    openedNow: resource.openedNow,
    occurredAt: resource.occurredAt,
    queryText: resource.queryText ?? null,
    title: resource.title ?? null,
    type: resource.kind,
    url: resource.url ?? null
  }));
}

function timelineRowsFromResources(resources: readonly {
  readonly decisive: boolean;
  readonly hostname?: string | null;
  readonly id: string;
  readonly kind: string;
  readonly occurredAt: string;
  readonly openedNow: boolean;
  readonly queryText?: string | null;
  readonly title?: string | null;
  readonly url?: string | null;
}[]): readonly TimelineRow[] {
  return resources.map((resource) => ({
    decisive: resource.decisive,
    hostname: resource.hostname ?? null,
    id: resource.id,
    openedNow: resource.openedNow,
    occurredAt: resource.occurredAt,
    queryText: resource.queryText ?? null,
    title: resource.title ?? null,
    type: resource.kind,
    url: resource.url ?? null
  }));
}

function recommendationFromSolvedProblem(
  route: Extract<RecallStatePayload["route"], { route: "solved_problem" }>
) {
  return {
    algorithmVersion: "deterministic-session-detail-v1",
    durationSeconds: null,
    evidence: [
      {
        evidenceCode: "LEXICAL_TEXT" as const,
        problemSessionId: route.problemSessionId
      }
    ],
    finalSolution: route.finalSolution,
    recommendationId: route.problemSessionId,
    recommendedProblemSessionId: route.problemSessionId,
    resources: route.resources.slice(0, 5),
    solvedAt: route.updatedAt,
    strength: "STRONG" as const,
    title: route.title ?? null,
    whyThis: [
      {
        evidenceCode: "LEXICAL_TEXT" as const,
        message: "Opened from your saved solved problems.",
        problemSessionId: route.problemSessionId
      }
    ]
  };
}

function hasOpenedResource(
  state: RecallStatePayload,
  resourceId: string
): boolean {
  if (state.route.route === "solved_problem") {
    return state.route.resources.some(
      (resource) =>
        resource.id === resourceId &&
        resource.openedNow
    );
  }

  if (state.route.route !== "similar_problem") {
    return false;
  }

  return (state.route.recommendation.resources ?? []).some(
    (resource) =>
      resource.id === resourceId &&
      resource.openedNow
  );
}

function isInvokedRecallState(
  state: RecallStatePayload | null
): state is RecallStatePayload {
  return (
    state !== null &&
    state.invokedAt !== null &&
    state.source !== null
  );
}

function isRecallSearchRoute(
  state: RecallStatePayload | null
): state is RecallStatePayload {
  return state !== null && state.route.route === "search";
}

function emptySearchResult(
  reason: Extract<
    RecallStatePayload["route"],
    { route: "search" }
  >["reason"]
) {
  return {
    degradationMode: null,
    query: null,
    results: [],
    status:
      reason === "AUTHENTICATION_REQUIRED"
        ? ("authentication_required" as const)
        : ("received" as const)
  };
}

const styles = {
  body: {
    color: trailColors.textMuted,
    fontSize: trailTypography.body,
    lineHeight: 1.5,
    margin: 0
  },

  eyebrow: {
    color: trailColors.accentStrong,
    fontSize: trailTypography.caption,
    fontWeight: 850,
    letterSpacing: "0.08em",
    margin: 0,
    textTransform: "uppercase"
  },

  header: {
    display: "grid",
    gap: trailSpacing.sm
  },

  loadingLine: {
    background: `linear-gradient(90deg, ${trailColors.accent}, transparent)`,
    borderRadius: trailRadii.pill,
    height: "2px"
  },

  disabledAction: {
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.button,
    color: trailColors.muted,
    cursor: "not-allowed",
    fontSize: trailTypography.control,
    fontWeight: 750,
    padding: "9px 11px"
  },

  nav: {
    alignItems: "center",
    background: trailColors.surface,
    border: `1px solid ${trailColors.borderMuted}`,
    borderRadius: trailRadii.pill,
    display: "flex",
    flexWrap: "wrap",
    gap: trailSpacing.xs,
    padding: "6px"
  },

  navActions: {
    display: "flex",
    flexWrap: "wrap",
    gap: trailSpacing.sm
  },

  navButton: {
    background: "transparent",
    border: "1px solid transparent",
    borderRadius: trailRadii.pill,
    color: trailColors.textMuted,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 750,
    padding: "7px 10px"
  },

  navButtonActive: {
    background: trailColors.surfaceSubtle,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.pill,
    color: trailColors.text,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 800,
    padding: "7px 10px"
  },

  noticeCard: {
    background: trailColors.surfaceElevated,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.card,
    display: "grid",
    gap: trailSpacing.sm,
    padding: trailSpacing.xl
  },

  primaryAction: {
    background: trailColors.accent,
    border: `1px solid ${trailColors.accentStrong}`,
    borderRadius: trailRadii.button,
    color: trailColors.background,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 800,
    padding: "9px 11px"
  },

  secondaryAction: {
    background: trailColors.surfaceSubtle,
    border: `1px solid ${trailColors.border}`,
    borderRadius: trailRadii.button,
    color: trailColors.text,
    cursor: "pointer",
    fontSize: trailTypography.control,
    fontWeight: 750,
    padding: "9px 11px"
  },

  shell: {
    background: trailColors.background,
    color: trailColors.text,
    display: "grid",
    fontFamily: trailTypography.family,
    gap: trailSpacing.lg,
    minHeight: "100vh",
    padding: trailSpacing.xl
  },

  routeSection: {
    display: "grid",
    gap: trailSpacing.lg
  },

  title: {
    color: trailColors.text,
    fontSize: trailTypography.display,
    letterSpacing: "-0.05em",
    lineHeight: 1.05,
    margin: 0
  }
} satisfies Record<string, CSSProperties>;
