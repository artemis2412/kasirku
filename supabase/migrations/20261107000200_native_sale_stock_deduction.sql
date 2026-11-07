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

-- Defer deduction until the whole order_items set is present. This correctly
-- aggregates multiple lines for the same product and remains idempotent when
-- sync_order deletes/reinserts the same snapshot on retry.
create or replace function private.process_native_sale_stock_snapshot()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_store_id uuid;
  v_branch_id uuid;
  v_local_id text;
  v_product_store_id uuid;
  v_product_id uuid;
  v_quantity integer;
  v_inserted integer;
begin
  select o.store_id, o.branch_id, o.local_id
    into v_store_id, v_branch_id, v_local_id
  from public.orders o
  where o.id = new.order_id;

  if v_store_id is null or v_branch_id is null or v_local_id is null then
    raise exception 'Order context missing for stock deduction';
  end if;

  -- Validate the complete item snapshot and process one aggregate per product.
  for v_product_id, v_quantity in
    select oi.product_id, sum(oi.quantity)::integer
    from public.order_items oi
    where oi.order_id = new.order_id
      and oi.product_id is not null
    group by oi.product_id
  loop
    select p.store_id into v_product_store_id
    from public.catalog_products p
    where p.id = v_product_id;

    -- Legacy web products are outside the native catalog.
    if v_product_store_id is null then
      continue;
    end if;
    if v_product_store_id <> v_store_id then
      raise exception 'Product belongs to a different store';
    end if;
    if v_quantity <= 0 then
      raise exception 'Invalid native stock deduction quantity';
    end if;

    insert into public.inventory_sale_deductions (
      store_id, branch_id, order_local_id, product_id, quantity
    ) values (
      v_store_id, v_branch_id, v_local_id, v_product_id, v_quantity
    )
    on conflict (branch_id, order_local_id, product_id) do nothing;

    get diagnostics v_inserted = row_count;
    if v_inserted = 1 then
      update public.branch_inventory
         set quantity = quantity - v_quantity,
             updated_at = now()
       where store_id = v_store_id
         and branch_id = v_branch_id
         and product_id = v_product_id
         and quantity >= v_quantity;
      if not found then
        raise exception 'Stok Cloud tidak mencukupi atau belum disinkronkan untuk produk %', v_product_id;
      end if;
    elsif not exists (
      select 1
      from public.inventory_sale_deductions d
      where d.branch_id = v_branch_id
        and d.order_local_id = v_local_id
        and d.product_id = v_product_id
        and d.store_id = v_store_id
        and d.quantity = v_quantity
    ) then
      raise exception 'Isi transaksi berubah setelah stok diproses untuk produk %', v_product_id;
    end if;
  end loop;

  -- Reject retries that remove a previously deducted product from the order.
  if exists (
    select 1
    from public.inventory_sale_deductions d
    where d.branch_id = v_branch_id
      and d.order_local_id = v_local_id
      and (
        d.store_id <> v_store_id
        or not exists (
          select 1
          from public.order_items oi
          where oi.order_id = new.order_id
            and oi.product_id = d.product_id
          group by oi.product_id
          having sum(oi.quantity)::integer = d.quantity
        )
      )
  ) then
    raise exception 'Rincian transaksi berbeda dari transaksi yang stoknya sudah diproses';
  end if;

  return new;
end;
$$;

revoke all on function private.process_native_sale_stock_snapshot() from public, anon, authenticated;

drop trigger if exists trg_deduct_native_catalog_stock on public.order_items;
drop trigger if exists trg_validate_native_sale_deduction_snapshot on public.order_items;
create constraint trigger trg_process_native_sale_stock_snapshot
after insert or update on public.order_items
deferrable initially deferred
for each row execute function private.process_native_sale_stock_snapshot();

comment on table public.inventory_sale_deductions is
  'Idempotency ledger preventing duplicate branch stock deductions when native orders retry sync.';
