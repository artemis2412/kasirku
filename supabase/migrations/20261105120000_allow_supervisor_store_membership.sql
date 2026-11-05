-- Keep store membership roles aligned with staff invitation roles and Android Native access rules.
ALTER TABLE public.store_memberships
  DROP CONSTRAINT IF EXISTS store_memberships_role_check;

ALTER TABLE public.store_memberships
  ADD CONSTRAINT store_memberships_role_check
  CHECK (role IN ('owner', 'admin', 'supervisor', 'cashier'));
