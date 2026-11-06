-- Native shared catalog with store-level products and branch-level price/stock.
-- This is additive: existing web tables (products/menu_categories) remain untouched.
-- Apply only after reviewing the migration against the target Supabase project.

create table if not exists public.catalog_categories (
  id uuid primary key,
  store_id uuid not null references public.stores(id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 100),
  sort_order integer not null default 0,
  is_active boolean not null default true,
  updated_at timestamptz not null default now(),
  unique (store_id, id),
  unique (store_id, name)
);

create table if not exists public.catalog_products (
  id uuid primary key,
  store_id uuid not null references public.stores(id) on delete cascade,
  category_id uuid,
  name text not null check (length(btrim(name)) between 1 and 160),
  description text not null default '',
  base_price bigint not null check (base_price > 0),
  photo_uri text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (store_id, id),
  foreign key (store_id, category_id)
    references public.catalog_categories(store_id, id)
);

create table if not exists public.branch_product_prices (
  store_id uuid not null,
  branch_id uuid not null,
  product_id uuid not null,
  selling_price bigint not null check (selling_price > 0),
  updated_at timestamptz not null default now(),
  primary key (branch_id, product_id),
  foreign key (store_id, branch_id)
    references public.branches(store_id, id) on delete cascade,
  foreign key (store_id, product_id)
    references public.catalog_products(store_id, id) on delete cascade
);

create table if not exists public.branch_inventory (
  store_id uuid not null,
  branch_id uuid not null,
  product_id uuid not null,
  quantity numeric(14,3) not null default 0 check (quantity >= 0),
  updated_at timestamptz not null default now(),
  primary key (branch_id, product_id),
  foreign key (store_id, branch_id)
    references public.branches(store_id, id) on delete cascade,
  foreign key (store_id, product_id)
    references public.catalog_products(store_id, id) on delete cascade
);

create table if not exists public.product_barcodes (
  id uuid primary key,
  store_id uuid not null,
  product_id uuid not null,
  barcode text not null check (length(btrim(barcode)) between 1 and 128),
  created_at timestamptz not null default now(),
  unique (store_id, barcode),
  unique (store_id, product_id, barcode),
  foreign key (store_id, product_id)
    references public.catalog_products(store_id, id) on delete cascade
);

create index if not exists idx_catalog_products_store_active
  on public.catalog_products(store_id, is_active, name);
create index if not exists idx_branch_inventory_branch
  on public.branch_inventory(store_id, branch_id, quantity);
create index if not exists idx_product_barcodes_product
  on public.product_barcodes(store_id, product_id);

alter table public.catalog_categories enable row level security;
alter table public.catalog_products enable row level security;
alter table public.branch_product_prices enable row level security;
alter table public.branch_inventory enable row level security;
alter table public.product_barcodes enable row level security;

drop policy if exists catalog_categories_read_member on public.catalog_categories;
create policy catalog_categories_read_member on public.catalog_categories
  for select to authenticated
  using (private.is_store_member(store_id));

drop policy if exists catalog_categories_manage_owner_admin on public.catalog_categories;
create policy catalog_categories_manage_owner_admin on public.catalog_categories
  for all to authenticated
  using (
    private.is_store_member(store_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = catalog_categories.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin')
    )
  )
  with check (
    private.is_store_member(store_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = catalog_categories.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin')
    )
  );

drop policy if exists catalog_products_read_member on public.catalog_products;
create policy catalog_products_read_member on public.catalog_products
  for select to authenticated
  using (private.is_store_member(store_id));

drop policy if exists catalog_products_manage_owner_admin on public.catalog_products;
create policy catalog_products_manage_owner_admin on public.catalog_products
  for all to authenticated
  using (
    private.is_store_member(store_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = catalog_products.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin')
    )
  )
  with check (
    private.is_store_member(store_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = catalog_products.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin')
    )
  );

-- Branch members may read only their assigned branch. Writes are owner/admin/supervisor.
drop policy if exists branch_product_prices_read_access on public.branch_product_prices;
create policy branch_product_prices_read_access on public.branch_product_prices
  for select to authenticated
  using (private.has_branch_access(store_id, branch_id));

drop policy if exists branch_product_prices_manage on public.branch_product_prices;
create policy branch_product_prices_manage on public.branch_product_prices
  for all to authenticated
  using (
    private.has_branch_access(store_id, branch_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = branch_product_prices.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin','supervisor')
    )
  )
  with check (
    private.has_branch_access(store_id, branch_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = branch_product_prices.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin','supervisor')
    )
  );

drop policy if exists branch_inventory_read_access on public.branch_inventory;
create policy branch_inventory_read_access on public.branch_inventory
  for select to authenticated
  using (private.has_branch_access(store_id, branch_id));

drop policy if exists branch_inventory_manage on public.branch_inventory;
create policy branch_inventory_manage on public.branch_inventory
  for all to authenticated
  using (
    private.has_branch_access(store_id, branch_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = branch_inventory.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin','supervisor')
    )
  )
  with check (
    private.has_branch_access(store_id, branch_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = branch_inventory.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin','supervisor')
    )
  );

drop policy if exists product_barcodes_read_member on public.product_barcodes;
create policy product_barcodes_read_member on public.product_barcodes
  for select to authenticated
  using (private.is_store_member(store_id));

drop policy if exists product_barcodes_manage_owner_admin on public.product_barcodes;
create policy product_barcodes_manage_owner_admin on public.product_barcodes
  for all to authenticated
  using (
    private.is_store_member(store_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = product_barcodes.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin')
    )
  )
  with check (
    private.is_store_member(store_id)
    and exists (
      select 1 from public.store_memberships sm
      where sm.store_id = product_barcodes.store_id
        and sm.user_id = (select auth.uid())
        and sm.is_active = true and sm.role in ('owner','admin')
    )
  );

grant select, insert, update, delete on
  public.catalog_categories, public.catalog_products, public.product_barcodes,
  public.branch_product_prices, public.branch_inventory to authenticated;

comment on table public.catalog_products is
  'Shared store-level product master for native catalog; branch prices and stock are stored separately.';
