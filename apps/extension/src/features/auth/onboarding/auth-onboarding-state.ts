export const AUTH_ONBOARDING_MODES = {
  createAccount: "CREATE_ACCOUNT",
  resetPassword: "RESET_PASSWORD",
  resetRequested: "RESET_REQUESTED",
  signIn: "SIGN_IN",
  startTrail: "START_TRAIL",
  verifyEmail: "VERIFY_EMAIL"
} as const;

export const AUTH_ONBOARDING_STATUS = {
  idle: "IDLE",
  loading: "LOADING"
} as const;

export const AUTH_ONBOARDING_NOTICE_CODES = {
  genericFailure: "GENERIC_FAILURE",
  offline: "OFFLINE",
  passwordResetAccepted: "PASSWORD_RESET_ACCEPTED",
  ready: "READY",
  serviceUnavailable: "SERVICE_UNAVAILABLE",
  verificationRequired: "VERIFICATION_REQUIRED"
} as const;

export type AuthOnboardingMode = (typeof AUTH_ONBOARDING_MODES)[keyof typeof AUTH_ONBOARDING_MODES];
export type AuthOnboardingStatus = (typeof AUTH_ONBOARDING_STATUS)[keyof typeof AUTH_ONBOARDING_STATUS];
export type AuthOnboardingNoticeCode =
  (typeof AUTH_ONBOARDING_NOTICE_CODES)[keyof typeof AUTH_ONBOARDING_NOTICE_CODES];

export interface AuthOnboardingState {
  readonly mode: AuthOnboardingMode;
  readonly status: AuthOnboardingStatus;
  readonly email: string;
  readonly password: string;
  readonly verificationToken: string;
  readonly resetToken: string;
  readonly newPassword: string;
  readonly noticeCode: AuthOnboardingNoticeCode | null;
}

export type AuthOnboardingAction =
  | { readonly type: "CHANGE_EMAIL"; readonly value: string }
  | { readonly type: "CHANGE_PASSWORD"; readonly value: string }
  | { readonly type: "CHANGE_VERIFICATION_TOKEN"; readonly value: string }
  | { readonly type: "CHANGE_RESET_TOKEN"; readonly value: string }
  | { readonly type: "CHANGE_NEW_PASSWORD"; readonly value: string }
  | { readonly type: "SHOW_CREATE_ACCOUNT" }
  | { readonly type: "SHOW_FORGOT_PASSWORD" }
  | { readonly type: "SHOW_SIGN_IN" }
  | { readonly type: "SUBMIT_STARTED" }
  | { readonly type: "AUTHENTICATED" }
  | { readonly type: "REGISTRATION_ACCEPTED" }
  | { readonly type: "PASSWORD_RESET_REQUEST_ACCEPTED" }
  | { readonly type: "PASSWORD_RESET_ACCEPTED" }
  | { readonly type: "EMAIL_VERIFIED" }
  | { readonly type: "OFFLINE_FAILURE" }
  | { readonly type: "SERVICE_UNAVAILABLE_FAILURE" }
  | { readonly type: "GENERIC_FAILURE" };

export function initialAuthOnboardingState(): AuthOnboardingState {
  return {
    mode: AUTH_ONBOARDING_MODES.signIn,
    status: AUTH_ONBOARDING_STATUS.idle,
    email: "",
    password: "",
    verificationToken: "",
    resetToken: "",
    newPassword: "",
    noticeCode: null
  };
}

export function reduceAuthOnboardingState(
  state: AuthOnboardingState,
  action: AuthOnboardingAction
): AuthOnboardingState {
  switch (action.type) {
    case "CHANGE_EMAIL":
      return { ...state, email: action.value, noticeCode: null };
    case "CHANGE_PASSWORD":
      return { ...state, password: action.value, noticeCode: null };
    case "CHANGE_VERIFICATION_TOKEN":
      return { ...state, verificationToken: action.value, noticeCode: null };
    case "CHANGE_RESET_TOKEN":
      return { ...state, resetToken: action.value, noticeCode: null };
    case "CHANGE_NEW_PASSWORD":
      return { ...state, newPassword: action.value, noticeCode: null };
    case "SHOW_CREATE_ACCOUNT":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.createAccount,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        noticeCode: null
      };
    case "SHOW_FORGOT_PASSWORD":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.resetPassword,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        noticeCode: null
      };
    case "SHOW_SIGN_IN":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.signIn,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        newPassword: "",
        noticeCode: null
      };
    case "SUBMIT_STARTED":
      return { ...state, status: AUTH_ONBOARDING_STATUS.loading, noticeCode: null };
    case "AUTHENTICATED":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.startTrail,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        noticeCode: AUTH_ONBOARDING_NOTICE_CODES.ready
      };
    case "REGISTRATION_ACCEPTED":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.verifyEmail,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        noticeCode: AUTH_ONBOARDING_NOTICE_CODES.verificationRequired
      };
    case "PASSWORD_RESET_REQUEST_ACCEPTED":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.resetRequested,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        newPassword: "",
        noticeCode: AUTH_ONBOARDING_NOTICE_CODES.passwordResetAccepted
      };
    case "PASSWORD_RESET_ACCEPTED":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.signIn,
        status: AUTH_ONBOARDING_STATUS.idle,
        password: "",
        newPassword: "",
        resetToken: "",
        noticeCode: null
      };
    case "EMAIL_VERIFIED":
      return {
        ...state,
        mode: AUTH_ONBOARDING_MODES.signIn,
        status: AUTH_ONBOARDING_STATUS.idle,
        verificationToken: "",
        password: "",
        noticeCode: null
      };
    case "OFFLINE_FAILURE":
      return {
        ...state,
        status: AUTH_ONBOARDING_STATUS.idle,
        noticeCode: AUTH_ONBOARDING_NOTICE_CODES.offline
      };
    case "SERVICE_UNAVAILABLE_FAILURE":
      return {
        ...state,
        status: AUTH_ONBOARDING_STATUS.idle,
        noticeCode: AUTH_ONBOARDING_NOTICE_CODES.serviceUnavailable
      };
    case "GENERIC_FAILURE":
      return {
        ...state,
        status: AUTH_ONBOARDING_STATUS.idle,
        noticeCode: AUTH_ONBOARDING_NOTICE_CODES.genericFailure
      };
  }
}

export function canStartTrail(state: AuthOnboardingState): boolean {
  return state.mode === AUTH_ONBOARDING_MODES.startTrail;
}
