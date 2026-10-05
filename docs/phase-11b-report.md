# Phase 11b: Local mission edit recovery

Date: 2026-10-05. Scope: preserve a separate working copy of unsaved local mission edits across app process recreation. The saved mission remains unchanged until the operator chooses **Save draft**.

## Delivered

- Every planner edit schedules a debounced recovery write to a dedicated DataStore key. The normal saved draft and recovery copy are separate values.
- On startup, a valid recovery copy that differs from the saved draft is restored and explicitly labeled as recovered unsaved work. The operator can review and save it or continue editing.
- Saving writes the committed draft and removes the recovery copy in the same DataStore transaction. If the save fails, the recovery copy remains available and the screen reports that the operator should retry.
- Recovery-write failures are surfaced in the planner. The screen distinguishes edits currently being recovered from a successfully written recovery copy.
- A corrupt recovery copy does not replace the last saved route; the saved route loads and the recovery problem is reported.

## Boundaries

This stores one working copy, not a mission library or export format. The recovery copy remains in app storage across process recreation and force-stop; clearing app data or uninstalling the app removes it. Drafts remain local and are never sent to a vehicle. Mission transfer and execution remain disabled.

## Validation

No tests or builds were run for this change.
