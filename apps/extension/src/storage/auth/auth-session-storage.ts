import { parseStoredRefreshSession, type StoredRefreshSession } from "../../features/auth/model/auth-session";

export const AUTH_SESSION_STORAGE_KEY = "trail.auth.refreshSession";

export interface AuthSessionStorage {
  read(): Promise<StoredRefreshSession | null>;
  write(session: StoredRefreshSession): Promise<void>;
  clear(): Promise<void>;
}

export class ChromeLocalAuthSessionStorage implements AuthSessionStorage {
  async read(): Promise<StoredRefreshSession | null> {
    const values = await chrome.storage.local.get(AUTH_SESSION_STORAGE_KEY);
    return parseStoredRefreshSession(values[AUTH_SESSION_STORAGE_KEY]);
  }

  async write(session: StoredRefreshSession): Promise<void> {
    await chrome.storage.local.set({ [AUTH_SESSION_STORAGE_KEY]: session });
  }

  async clear(): Promise<void> {
    await chrome.storage.local.remove(AUTH_SESSION_STORAGE_KEY);
  }
}
