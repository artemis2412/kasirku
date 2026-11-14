-- Backfill legacy branches and provide a safe default for branches created
-- by older clients that do not yet send an explicit IANA timezone identifier.
-- Newer clients capture the creating device's timezone and persist it on branch creation.
UPDATE public.branches
SET timezone_id = 'Asia/Jakarta'
WHERE timezone_id IS NULL OR btrim(timezone_id) = '';

ALTER TABLE public.branches
  ALTER COLUMN timezone_id SET DEFAULT 'Asia/Jakarta';

COMMENT ON COLUMN public.branches.timezone_id IS
  'IANA timezone identifier captured from the branch-creation device. Legacy/older-client fallback: Asia/Jakarta.';
