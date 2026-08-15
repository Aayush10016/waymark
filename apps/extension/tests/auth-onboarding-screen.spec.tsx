import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { AuthOnboardingScreen } from "../src/features/auth";

describe("AuthOnboardingScreen", () => {
  it("renders a compact sign-in hierarchy with a safe password toggle", () => {
    const markup = renderToStaticMarkup(<AuthOnboardingScreen />);

    expect(markup).toContain("Trail");
    expect(markup).toContain("Sign in");
    expect(markup).toContain("Show password");
    expect(markup).toContain('type="button"');
    expect(markup).toContain('type="password"');
    expect(markup).not.toContain("Private beta");
    expect(markup).not.toContain("Start Trail</p>");
    expect(markup).not.toContain("accessToken");
    expect(markup).not.toContain("refreshToken");
    expect(markup).not.toContain("jwt");
  });

  it("does not apply account-creation password length validation to sign-in", () => {
    const markup = renderToStaticMarkup(<AuthOnboardingScreen />);
    const currentPasswordInput = markup.match(/<input[^>]+autoComplete="current-password"[^>]*>/)?.[0];

    expect(currentPasswordInput).toBeDefined();
    expect(currentPasswordInput).not.toContain("minLength");
    expect(currentPasswordInput).not.toContain("minlength");
  });

  it("includes responsive sizing rules without hiding overflow", () => {
    const markup = renderToStaticMarkup(<AuthOnboardingScreen />);

    expect(markup).toContain("100dvh");
    expect(markup).toContain("box-sizing: border-box");
    expect(markup).toContain("@media (max-width: 860px)");
    expect(markup).not.toContain("overflow: hidden");
  });
});
