package io.github.aayush10016.trail.capture.application.command;

/**
 * Browser source that produced a capture event.
 */
public enum CaptureEventSource {
    /**
     * Chrome tab title/URL update event.
     */
    TAB_UPDATED,

    /**
     * Chrome top-level committed navigation event.
     */
    WEB_NAVIGATION_COMMITTED,

    /**
     * Chrome top-level history-state navigation event.
     */
    WEB_NAVIGATION_HISTORY_STATE
}
