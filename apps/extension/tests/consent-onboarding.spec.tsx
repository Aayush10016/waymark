import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { ConsentOnboardingScreen } from "../src/onboarding/consent/ConsentOnboardingScreen";
import { TrailOnboardingApp } from "../src/onboarding/TrailOnboardingApp";
import { CaptureConsentRuntimeService } from "../src/services/consent";
import type { CaptureConsentStorage, StoredCaptureConsent } from "../src/storage/consent/capture-consent-storage";

describe("ConsentOnboardingScreen", () => {
  it("explains capture scope, privacy exclusions, purpose, and guarantees", () => {
    const markup = renderToStaticMarkup(
      <ConsentOnboardingScreen service={new CaptureConsentRuntimeService({ storage: new InMemoryCaptureConsentStorage() })} />
    );

    expect(markup).toContain("Trail captures");
    expect(markup).toContain("Page title");
    expect(markup).toContain("Sanitized URL");
    expect(markup).toContain("Search query only from allowlisted search engines");
    expect(markup).toContain("Trail never captures");
    expect(markup).toContain("Page content");
    expect(markup).toContain("keystrokes");
    expect(markup).toContain("Why capture exists");
    expect(markup).toContain("Privacy guarantees");
    expect(markup).toContain("Start Trail");
    expect(markup).toContain("Not now");
  });

  it("integrates as the post-auth onboarding step", () => {
    const authMarkup = renderToStaticMarkup(<TrailOnboardingApp initialStep="auth" />);
    const consentMarkup = renderToStaticMarkup(<TrailOnboardingApp initialStep="consent" />);

    expect(authMarkup).toContain("Sign in");
    expect(consentMarkup).toContain("Choose what Trail can remember");
    expect(consentMarkup).toContain("Start Trail");
  });
});

class InMemoryCaptureConsentStorage implements CaptureConsentStorage {
  read(): Promise<StoredCaptureConsent | null> {
    return Promise.resolve(null);
  }

  write(_consent: StoredCaptureConsent): Promise<void> {
    return Promise.resolve();
  }

  clear(): Promise<void> {
    return Promise.resolve();
  }
}
