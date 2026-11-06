-- Deduct native catalog stock exactly once when a native sale is synced.
-- Legacy web products are not in catalog_products and remain unaffected.
create table if not exists public.inventory_sale_deductions (
  store_id uuid not null,
  branch_id uuid not null,
  order_local_id text not null,
  product_id uuid not null,
  quantity integer not null check (quantity > 0),
  created_at timestamptz not null default now(),
  primary key (branch_id, order_local_id, product_id),
  foreign key (store_id, branch_id)
    references public.branches(store_id, id) on delete cascade,
  foreign key (store_id, product_id)
    references public.catalog_products(store_id, id) on delete cascade
);

alter table public.inventory_sale_deductions enable row level security;
revoke all on public.inventory_sale_deductions from public, anon, authenticated;

create or replace function private.deduct_native_catalog_stock_after_order_item()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_store_id uuid;
  v_branch_id uuid;
  v_local_id text;
  v_inserted integer;
  v_product_store_id uuid;
begin
  select o.store_id, o.branch_id, o.local_id
    into v_store_id, v_branch_id, v_local_id
  from public.orders o
  where o.id = new.order_id;

  if v_store_id is null or v_branch_id is null or v_local_id is null then
    raise exception 'Order context missing for stock deduction';
  end if;

  -- The native catalog is separate from the legacy web products table.
  -- Ignore legacy products; native product IDs must exist in catalog_products.
  if new.product_id is null then
    return new;
  end if;

  select p.store_id into v_product_store_id
  from public.catalog_products p
  where p.id = new.product_id;

  if v_product_store_id is null then
    return new;
  end if;

  if v_product_store_id <> v_store_id then
    raise exception 'Product belongs to a different store';
  end if;

  if new.quantity <= 0 or new.quantity <> trunc(new.quantity) then
    raise exception 'Invalid native stock deduction quantity';
  end if;

  insert into public.inventory_sale_deductions (
    store_id, branch_id, order_local_id, product_id, quantity
  ) values (
    v_store_id, v_branch_id, v_local_id, new.product_id, new.quantity::integer
  )
  on conflict (branch_id, order_local_id, product_id) do nothing;

  get diagnostics v_inserted = row_count;
  if v_inserted = 0 then
    -- A retry must carry the exact same quantity for each product.
    -- Refuse a changed payload rather than silently leaving stock inconsistent.
    if not exists (
      select 1
      from public.inventory_sale_deductions d
      where d.branch_id = v_branch_id
        and d.order_local_id = v_local_id
        and d.product_id = new.product_id
        and d.quantity = new.quantity::integer
    ) then
      raise exception 'Isi transaksi berubah setelah stok diproses untuk produk %', new.product_name;
    end if;
    return new;
  end if;

  update public.branch_inventory
     set quantity = quantity - new.quantity::integer,
         updated_at = now()
   where store_id = v_store_id
     and branch_id = v_branch_id
     and product_id = new.product_id
     and quantity >= new.quantity::integer;

  if not found then
    raise exception 'Stok Cloud tidak mencukupi atau belum disinkronkan untuk produk %', new.product_name;
  end if;

  return new;
end;
$$;

revoke all on function private.deduct_native_catalog_stock_after_order_item() from public, anon, authenticated;

drop trigger if exists trg_deduct_native_catalog_stock on public.order_items;
create trigger trg_deduct_native_catalog_stock
after insert on public.order_items
for each row execute function private.deduct_native_catalog_stock_after_order_item();

comment on table public.inventory_sale_deductions is
  'Idempotency ledger preventing duplicate branch stock deductions when native orders retry sync.';
