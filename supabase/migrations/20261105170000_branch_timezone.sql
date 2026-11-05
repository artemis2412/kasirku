-- Branch-local timezone is required for consistent order_date/order_time
-- when staff use devices configured for different regions.
-- NULL intentionally blocks cloud sync until an owner/admin configures it.
ALTER TABLE public.branches
  ADD COLUMN IF NOT EXISTS timezone_id text;

COMMENT ON COLUMN public.branches.timezone_id IS
  'IANA timezone identifier for business dates, e.g. Asia/Makassar, Asia/Jakarta, or Asia/Jayapura. NULL means not configured; clients must not guess.';


CREATE OR REPLACE FUNCTION public.set_branch_timezone(
  p_store_id uuid,
  p_branch_id uuid,
  p_timezone_id text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
BEGIN
  IF auth.uid() IS NULL THEN
    RAISE EXCEPTION 'Authentication required';
  END IF;

  IF p_timezone_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM pg_catalog.pg_timezone_names
    WHERE name = p_timezone_id
  ) THEN
    RAISE EXCEPTION 'Invalid IANA timezone identifier';
  END IF;

  UPDATE public.branches AS b
  SET timezone_id = p_timezone_id,
      updated_at = now()
  WHERE b.id = p_branch_id
    AND b.store_id = p_store_id
    AND EXISTS (
      SELECT 1
      FROM public.store_memberships AS sm
      WHERE sm.store_id = p_store_id
        AND sm.user_id = auth.uid()
        AND sm.is_active = true
        AND sm.role IN ('owner', 'admin')
    );

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Branch not found or owner/admin access required';
  END IF;
END;
$$;

REVOKE ALL ON FUNCTION public.set_branch_timezone(uuid, uuid, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.set_branch_timezone(uuid, uuid, text) TO authenticated;
