package dev.isira.jaspring.component;

public enum ChangeDetectionStrategy {
    /// Checked on every change detection pass.
    DEFAULT,
    /// Checked only when an input changed, an event fired inside it, a signal it reads changed, or `markForCheck` was called.
    ON_PUSH
}
