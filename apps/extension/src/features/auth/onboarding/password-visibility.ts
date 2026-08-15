export interface PasswordVisibilityState {
  readonly inputType: "password" | "text";
  readonly label: "Hide password" | "Show password";
  readonly visible: boolean;
}

export function passwordVisibilityState(visible: boolean): PasswordVisibilityState {
  return {
    inputType: visible ? "text" : "password",
    label: visible ? "Hide password" : "Show password",
    visible
  };
}

export function togglePasswordVisibility(visible: boolean): boolean {
  return !visible;
}
