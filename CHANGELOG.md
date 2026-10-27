# Changelog

Notable changes to Jaspring. New work goes under **Unreleased**. On release, rename that section to the version and date, update the docs site from its **Docs to update** list, and start a new empty **Unreleased** section.

## Unreleased

### Added

- `data-jsp-ignore` attribute. A patch leaves the element's children and attributes alone and only adds or updates its `data-*` attributes, so hook params still reach `updated`. Use it for elements whose DOM a widget owns (editors, maps, date pickers, Chart.js).

### Fixed

- A browser call result above 8 KB no longer closes the WebSocket. The server joins message fragments itself, so the container's text buffer (8 KB on Tomcat) does not apply. The new limit is 4 MiB characters per client message.
- A call result above the WebSocket limit now fails the call at once with `BrowserCallException`, instead of after the 30 s timeout.
- Large patches queued behind a slow send no longer drop the socket. The send buffer limit went from 512 KB to 16 MB.

### Docs to update

Pages in the docs site (`jaspring-docs`) that describe the last release and need changes when this ships:

- **Browser calls** (`browser-calls`, "Read the result"): remove "Keep results small" and the 8 KB warning. State the 4 MiB limit over the WebSocket. In "Handle failures", add an oversized result as a failure cause.
- **Client hooks** (`client-hooks`, "Patches and hook-managed DOM"): add `data-jsp-ignore`.
- **Third-party JavaScript** (`third-party-libraries`): use `data-jsp-ignore` on the chart in "Wrap it" and "Bind it", drop the `resize()` workaround note, and update the checklist and the "small hook results" wording.
