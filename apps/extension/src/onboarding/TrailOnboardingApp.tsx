import type { CSSProperties, ReactElement } from "react";
import { useState } from "react";
import { trailColors, trailTypography } from "@trail/ui";
import { AuthOnboardingScreen } from "../features/auth";
import { ConsentOnboardingScreen } from "./consent/ConsentOnboardingScreen";

export type TrailOnboardingStep = "auth" | "consent";

export interface TrailOnboardingAppProps {
  readonly initialStep?: TrailOnboardingStep;
}

export function TrailOnboardingApp(props: TrailOnboardingAppProps): ReactElement {
  const [step, setStep] = useState<TrailOnboardingStep>(props.initialStep ?? "auth");

  if (step === "consent") {
    return (
      <main aria-label="Trail consent onboarding" style={styles.screen}>
        <ConsentOnboardingScreen />
      </main>
    );
  }

  return <AuthOnboardingScreen onStartTrail={() => setStep("consent")} />;
}

const styles = {
  screen: {
    alignItems: "center",
    background: `linear-gradient(135deg, ${trailColors.background} 0%, ${trailColors.surfaceElevated} 58%, #151325 100%)`,
    display: "grid",
    fontFamily: trailTypography.family,
    justifyItems: "center",
    minHeight: "100vh",
    padding: "48px"
  }
} satisfies Record<string, CSSProperties>;
