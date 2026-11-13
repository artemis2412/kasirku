# KASIRKU Time Zone Policy

## Product decision

KASIRKU does not expose a manual time-zone selector. A branch's time zone is detected automatically from the device's configured time zone when the branch is created, then stored as an IANA time-zone ID.

This follows the automatic time-zone behavior adopted for GUDANGKU.

## Source of truth

- Each branch owns one time zone.
- At branch creation, capture `ZoneId.systemDefault().id` from the device creating the branch.
- Do not request GPS/location permission. Device time-zone detection is sufficient and avoids location permission prompts.
- After creation, the saved branch time zone is authoritative. Opening that branch from a device in another time zone must not silently change it.
- Do not show WIB/WITA/WIT or IANA IDs as a user-editable setting.

## Existing branches

- Existing branches with no stored time zone are backfilled to `Asia/Jakarta` for backward compatibility.
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
