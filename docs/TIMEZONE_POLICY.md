# KASIRKU Time Zone Policy

## Product decision

KASIRKU does not expose a manual time-zone selector. A branch's time zone is detected automatically from the configured time zone of the device used by an Owner/Admin when the branch is first activated, then stored as an IANA time-zone ID.

This follows the automatic time-zone behavior adopted for GUDANGKU.

## Source of truth

- Each branch owns one time zone.
- On first activation of a new branch by an Owner/Admin, capture `ZoneId.systemDefault().id` from that device and persist it to the selected branch.
- Never apply one device's time zone to every branch in the store.
- Do not request GPS/location permission. Device time-zone detection is sufficient and avoids location permission prompts.
- After creation, the saved branch time zone is authoritative. Opening that branch from a device in another time zone must not silently change it.
- Do not show WIB/WITA/WIT or IANA IDs as a user-editable setting.

## Existing branches

- Existing branches with no stored time zone are backfilled to `Asia/Jakarta` for backward compatibility by a dedicated migration.
- A new branch with no stored time zone is initialized automatically when an Owner/Admin first activates it.
- Never overwrite a non-empty branch time zone during normal app startup or sign-in.

## Time handling

- Persist event timestamps in UTC.
- Convert timestamps to the selected branch's stored IANA time zone for receipts, transaction dates, daily reports, cash closing, business-day boundaries, and date-based filters.
- Background sync and workers must use the branch time zone when deriving business dates; they must not depend on the worker device's current default time zone.
- Changing the Android device time zone after branch creation must not change that branch's reporting day.

## Multi-branch behavior

- Switching branches switches the active time-zone context.
- Each branch may have a different time zone, even when the same owner or cashier uses the same device.
- A cashier device located elsewhere still displays and records business dates using the selected branch's time zone.

## Validation

- Validate stored IDs with Java `ZoneId.of(id)` before use.
- If a stored ID is missing or invalid, use the migration/backfill policy and surface a diagnostic; do not silently infer a new zone from GPS.
- Test WIB (`Asia/Jakarta`), WITA (`Asia/Makassar`), and WIT (`Asia/Jayapura`), including midnight boundaries and a device whose local zone differs from the selected branch.

## Web implementation

- Web business dates are derived with `Intl.DateTimeFormat` using the active branch's IANA time-zone ID.
- For a branch mapped to Cloud, the Cloud `branches.timezone_id` value is authoritative. Owner/Admin initializes a missing value for only the currently selected mapped branch through `set_branch_timezone`, using that local branch's saved time zone; legacy local branches without a saved value retain the Asia/Jakarta default.
- For offline/local-only branches, use the branch record's saved `timeZoneId`; older records without one use Asia/Jakarta.
- The active branch zone governs transaction dates and displayed times, paid dates, daily cash operations and closure, reports, archive cutoffs, and the date shown in the application header.
- Calendar arithmetic for report windows and archive cutoffs is performed on date-only values, not the browser device's local calendar.
