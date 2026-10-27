package dev.isira.jaspring.browser;

/// The `Notification` API.
public interface Notifications {

    PermissionState permission();

    BrowserPromise<PermissionState> requestPermission();

    /// Shows a notification when permission was granted; otherwise does nothing.
    void show(String title, NotificationOptions options);

    enum PermissionState { GRANTED, DENIED, PROMPT }

    record NotificationOptions(String body, String icon, String tag) {

        public static final NotificationOptions NONE = new NotificationOptions(null, null, null);
    }
}
