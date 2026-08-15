import { describe, expect, it } from "vitest";
import {
  passwordVisibilityState,
  togglePasswordVisibility
} from "../src/features/auth/onboarding/password-visibility";

describe("password visibility", () => {
  it("starts hidden with a show-password label", () => {
    expect(passwordVisibilityState(false)).toEqual({
      inputType: "password",
      label: "Show password",
      visible: false
    });
  });

  it("toggles hidden to visible without changing password value", () => {
    const password = "correct horse battery staple";
    const visible = togglePasswordVisibility(false);

    expect(visible).toBe(true);
    expect(passwordVisibilityState(visible)).toMatchObject({
      inputType: "text",
      label: "Hide password"
    });
    expect(password).toBe("correct horse battery staple");
  });

  it("toggles visible back to hidden", () => {
    const hidden = togglePasswordVisibility(true);

    expect(hidden).toBe(false);
    expect(passwordVisibilityState(hidden)).toMatchObject({
      inputType: "password",
      label: "Show password"
    });
  });
});
