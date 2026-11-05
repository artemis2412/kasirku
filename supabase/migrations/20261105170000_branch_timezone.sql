-- Branch-local timezone is required for consistent order_date/order_time
-- when staff use devices configured for different regions.
-- NULL intentionally blocks cloud sync until an owner/admin configures it.
ALTER TABLE public.branches
  ADD COLUMN IF NOT EXISTS timezone_id text;

COMMENT ON COLUMN public.branches.timezone_id IS
  'IANA timezone identifier for business dates, e.g. Asia/Makassar, Asia/Jakarta, or Asia/Jayapura. NULL means not configured; clients must not guess.';
