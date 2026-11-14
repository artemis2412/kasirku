-- Preserve historical KASIRKU business-day behavior for branches that existed
-- before branch-local time zones were introduced.
-- New branches remain NULL until an Owner/Admin first activates them; the
-- Android client then stores that device's IANA time-zone ID for that branch.
UPDATE public.branches
SET timezone_id = 'Asia/Jakarta',
    updated_at = now()
WHERE timezone_id IS NULL OR btrim(timezone_id) = '';

COMMENT ON COLUMN public.branches.timezone_id IS
  'IANA timezone identifier for business dates. Existing branches are backfilled to Asia/Jakarta; new branches are initialized automatically from the Owner/Admin device time zone on first activation.';
