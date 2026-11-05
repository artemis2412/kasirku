-- KASIRKU Cloud Database — initial schema
-- Apply in Supabase SQL Editor for a NEW project only.
-- This migration creates the multi-tenant foundation. It does not migrate localStorage data.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.stores (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  owner_user_id uuid not null references auth.users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.branches (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  name text not null,
  is_active boolean not null default true,
  timezone_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (store_id, id),
  unique (store_id, name)
);

create table if not exists public.store_memberships (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  role text not null check (role in ('owner','admin','supervisor','cashier')),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (store_id, user_id)
);

create table if not exists public.branch_memberships (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  unique (branch_id, user_id),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create table if not exists public.menu_categories (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  name text not null,
  created_at timestamptz not null default now(),
  unique (branch_id, name),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  category_id uuid references public.menu_categories(id) on delete set null,
  name text not null,
  price numeric(14,2) not null default 0 check (price >= 0),
  stock_unlimited boolean not null default true,
  stock_quantity numeric(14,3) not null default 0 check (stock_quantity >= 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create table if not exists public.orders (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  local_id text,
  order_number text not null,
  order_date date not null default current_date,
  order_time text,
  created_at timestamptz not null default now(),
  created_by uuid references auth.users(id) on delete set null,
  order_type text not null default 'Takeaway',
  table_label text,
  table_local_id text,
  subtotal numeric(14,2) not null default 0,
  discount_amount numeric(14,2) not null default 0,
  discount_type text,
  discount_input numeric(14,2) not null default 0,
  total numeric(14,2) not null default 0,
  order_status text not null default 'Menunggu',
  payment_status text not null default 'Belum Bayar',
  payment_method text,
  paid_amount numeric(14,2),
  change_amount numeric(14,2),
  paid_at timestamptz,
  paid_date date,
  note text,
  device_id text,
  sync_created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (branch_id, order_number),
  unique (branch_id, local_id),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create table if not exists public.order_items (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.orders(id) on delete cascade,
  product_id uuid,
  product_local_id text,
  product_name text not null,
  unit_price numeric(14,2) not null default 0,
  quantity numeric(14,3) not null default 1 check (quantity > 0),
  line_total numeric(14,2) not null default 0,
  prepared_quantity numeric(14,3) not null default 0,
  created_at timestamptz not null default now()
);

create table if not exists public.cash_entries (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  local_id text,
  entry_type text not null check (entry_type in ('opening','in','out')),
  amount numeric(14,2) not null check (amount > 0),
  category text not null,
  payment_method text not null default 'Tunai',
  note text,
  entry_date date not null default current_date,
  entry_time text,
  created_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (branch_id, local_id),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create table if not exists public.cash_closures (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  local_id text,
  closure_date date not null,
  expected_amount numeric(14,2) not null default 0,
  counted_amount numeric(14,2) not null default 0,
  difference_amount numeric(14,2) not null default 0,
  sales_total numeric(14,2) not null default 0,
  cash_in_total numeric(14,2) not null default 0,
  cash_out_total numeric(14,2) not null default 0,
  is_unlocked boolean not null default false,
  closed_by uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (branch_id, closure_date),
  unique (branch_id, local_id),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create table if not exists public.sync_devices (
  id uuid primary key default gen_random_uuid(),
  store_id uuid not null references public.stores(id) on delete cascade,
  branch_id uuid not null,
  user_id uuid not null references auth.users(id) on delete cascade,
  device_key text not null,
  device_label text not null default '',
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  unique (store_id, device_key),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade
);

create index if not exists idx_branches_store on public.branches(store_id);
create index if not exists idx_store_memberships_user on public.store_memberships(user_id, store_id);
create index if not exists idx_branch_memberships_user on public.branch_memberships(user_id, branch_id);
create index if not exists idx_products_branch on public.products(branch_id, is_active);
create index if not exists idx_orders_branch_date on public.orders(branch_id, order_date desc);
create index if not exists idx_order_items_order on public.order_items(order_id);
create index if not exists idx_cash_entries_branch_date on public.cash_entries(branch_id, entry_date desc);
create index if not exists idx_cash_closures_branch_date on public.cash_closures(branch_id, closure_date desc);

-- Access helpers run with the table owner's privileges, but accept only auth.uid().
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create or replace function private.is_store_member(target_store uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1 from public.store_memberships sm
    where sm.store_id = target_store
      and sm.user_id = (select auth.uid())
      and sm.is_active = true
  );
$$;

create or replace function private.has_branch_access(target_store uuid, target_branch uuid)
returns boolean
language sql stable security definer
set search_path = ''
as $$
  select exists (
    select 1
    from public.store_memberships sm
    where sm.store_id = target_store
      and sm.user_id = (select auth.uid())
      and sm.is_active = true
      and (
        sm.role in ('owner','admin')
        or exists (
          select 1 from public.branch_memberships bm
          where bm.store_id = target_store
            and bm.branch_id = target_branch
            and bm.user_id = (select auth.uid())
        )
      )
  );
$$;

revoke all on function private.is_store_member(uuid) from public, anon, authenticated;
revoke all on function private.has_branch_access(uuid, uuid) from public, anon, authenticated;
grant execute on function private.is_store_member(uuid) to authenticated;
grant execute on function private.has_branch_access(uuid, uuid) to authenticated;

alter table public.profiles enable row level security;
alter table public.stores enable row level security;
alter table public.branches enable row level security;
alter table public.store_memberships enable row level security;
alter table public.branch_memberships enable row level security;
alter table public.menu_categories enable row level security;
alter table public.products enable row level security;
alter table public.orders enable row level security;
alter table public.order_items enable row level security;
alter table public.cash_entries enable row level security;
alter table public.cash_closures enable row level security;
alter table public.sync_devices enable row level security;

create policy "profiles_read_self" on public.profiles for select to authenticated using (id = auth.uid());
create policy "profiles_insert_self" on public.profiles for insert to authenticated with check (id = auth.uid());
create policy "profiles_update_self" on public.profiles for update to authenticated using (id = auth.uid()) with check (id = auth.uid());

create policy "stores_read_members" on public.stores for select to authenticated using (private.is_store_member(id));
create policy "branches_read_members" on public.branches for select to authenticated using (private.has_branch_access(store_id, id));
create policy "store_memberships_read_members" on public.store_memberships for select to authenticated using (private.is_store_member(store_id));
create policy "branch_memberships_read_members" on public.branch_memberships for select to authenticated using (private.has_branch_access(store_id, branch_id));

create policy "categories_branch_access" on public.menu_categories for all to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

create policy "products_branch_access" on public.products for all to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

create policy "orders_branch_access" on public.orders for all to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

create policy "order_items_access" on public.order_items for all to authenticated
using (exists (
  select 1 from public.orders o
  where o.id = order_items.order_id
    and private.has_branch_access(o.store_id, o.branch_id)
))
with check (exists (
  select 1 from public.orders o
  where o.id = order_items.order_id
    and private.has_branch_access(o.store_id, o.branch_id)
));

create policy "cash_entries_branch_access" on public.cash_entries for all to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

create policy "cash_closures_branch_access" on public.cash_closures for all to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

create policy "sync_devices_branch_access" on public.sync_devices for all to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

grant usage on schema public to authenticated;
grant select, insert, update on public.profiles to authenticated;
grant select on public.stores, public.branches, public.store_memberships, public.branch_memberships to authenticated;
grant select, insert, update, delete on public.menu_categories, public.products, public.orders, public.order_items, public.cash_entries, public.cash_closures, public.sync_devices to authenticated;

-- Deliberately no anonymous policies and no service-role key in browser code.


-- Owner/admin-only branch timezone configuration used by Android background sync.
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
      SELECT 1 FROM public.store_memberships AS sm
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
