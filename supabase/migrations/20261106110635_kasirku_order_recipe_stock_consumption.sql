-- Process inventory consumption once per cloud order. Kept in the private schema.
create table if not exists private.inventory_consumption_runs (
  order_id uuid primary key references public.orders(id) on delete cascade,
  store_id uuid not null,
  branch_id uuid not null,
  processed_by uuid not null references auth.users(id),
  processed_at timestamptz not null default now()
);

create or replace function private.consume_order_inventory(
  p_order_id uuid,p_store_id uuid,p_branch_id uuid
) returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  v_order public.orders%rowtype;
  v_line record;
  v_on_hand numeric(14,3);
  v_unit_cost numeric(14,2);
  v_user uuid := (select auth.uid());
begin
  if v_user is null then raise exception 'Harus login terlebih dahulu'; end if;
  if not private.has_store_role(p_store_id,array['owner','admin','supervisor','cashier'])
     or not private.has_branch_access(p_store_id,p_branch_id) then
    raise exception 'Tidak memiliki akses transaksi pada cabang ini';
  end if;

  select * into v_order from public.orders
  where id=p_order_id and store_id=p_store_id and branch_id=p_branch_id
  for update;
  if not found then raise exception 'Transaksi untuk pengurangan stok tidak ditemukan'; end if;

  insert into private.inventory_consumption_runs(order_id,store_id,branch_id,processed_by)
  values(p_order_id,p_store_id,p_branch_id,v_user)
  on conflict(order_id) do nothing;
  if not found then return; end if;

  for v_line in
    select m.inventory_item_id,
           sum(oi.quantity * m.quantity_per_menu)::numeric(14,3) as quantity_used
    from public.order_items oi
    join public.menu_recipe_items m
      on m.product_id=oi.product_id
     and m.store_id=p_store_id
     and m.branch_id=p_branch_id
    where oi.order_id=p_order_id and oi.product_id is not null
    group by m.inventory_item_id
    order by m.inventory_item_id
  loop
    select i.on_hand,i.average_unit_cost into v_on_hand,v_unit_cost
    from public.inventory_items i
    where i.id=v_line.inventory_item_id
      and i.store_id=p_store_id
      and i.branch_id=p_branch_id
      and i.is_active=true
    for update;
    if not found then raise exception 'Bahan resep menu tidak ditemukan atau tidak aktif'; end if;
    if v_on_hand < v_line.quantity_used then
      raise exception 'Stok bahan tidak mencukupi untuk transaksi. Tersedia %, dibutuhkan %',
        v_on_hand,v_line.quantity_used;
    end if;

    update public.inventory_items
    set on_hand=on_hand-v_line.quantity_used,updated_at=now()
    where id=v_line.inventory_item_id and store_id=p_store_id and branch_id=p_branch_id;

    insert into public.inventory_movements(
      store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,
      reference_type,reference_id,idempotency_key,note,created_by
    ) values (
      p_store_id,p_branch_id,v_line.inventory_item_id,'menu_sale',-v_line.quantity_used,
      v_unit_cost,'order',p_order_id,
      'order:'||p_order_id::text||':'||v_line.inventory_item_id::text,
      'Pemakaian bahan dari penjualan',v_user
    );
  end loop;
end;
$function$;

revoke all on function private.consume_order_inventory(uuid,uuid,uuid) from public,anon;
grant execute on function private.consume_order_inventory(uuid,uuid,uuid) to authenticated;

-- Idempotent, atomic upload of one local sale and its line items.
-- Runs as the caller so existing orders/order_items RLS policies remain enforced.
create or replace function public.sync_order(p_order jsonb, p_items jsonb)
returns uuid
language plpgsql
security invoker
set search_path = ''
as $$
declare
  v_order_id uuid;
  v_created_by uuid;
begin
  if auth.uid() is null then
    raise exception 'Harus login terlebih dahulu';
  end if;

  if jsonb_typeof(p_order) is distinct from 'object'
     or jsonb_typeof(p_items) is distinct from 'array' then
    raise exception 'Payload transaksi tidak valid';
  end if;

  if jsonb_array_length(p_items) = 0 then
    raise exception 'Rincian transaksi tidak boleh kosong';
  end if;

  if nullif(p_order->>'store_id', '') is null
     or nullif(p_order->>'branch_id', '') is null
     or nullif(p_order->>'local_id', '') is null then
    raise exception 'Identitas toko, cabang, atau transaksi tidak lengkap';
  end if;

  -- Cashiers may upload only their own sales. Owner/admin may recover
  -- queued sales created by another cashier on a shared store device.
  v_created_by := nullif(p_order->>'created_by', '')::uuid;
  if v_created_by is null then
    raise exception 'Identitas kasir transaksi tidak tersedia';
  end if;

  if v_created_by is distinct from auth.uid() and not exists (
    select 1
    from public.store_memberships as sm
    where sm.store_id = (p_order->>'store_id')::uuid
      and sm.user_id = auth.uid()
      and sm.is_active = true
      and sm.role in ('owner', 'admin')
  ) then
    raise exception 'Hanya owner/admin yang dapat menyinkronkan transaksi kasir lain';
  end if;

  -- The recorded cashier must be an active member of this store and,
  -- unless owner/admin, assigned to the exact branch that owns the sale.
  if not exists (
    select 1
    from public.store_memberships as creator
    where creator.store_id = (p_order->>'store_id')::uuid
      and creator.user_id = v_created_by
      and creator.is_active = true
      and (
        creator.role in ('owner', 'admin')
        or exists (
          select 1
          from public.branch_memberships as bm
          where bm.store_id = creator.store_id
            and bm.branch_id = (p_order->>'branch_id')::uuid
            and bm.user_id = creator.user_id
        )
      )
  ) then
    raise exception 'Kasir transaksi tidak memiliki akses aktif ke cabang ini';
  end if;

  if coalesce((p_order->>'subtotal')::numeric, -1) < 0
     or coalesce((p_order->>'discount_amount')::numeric, -1) < 0
     or coalesce((p_order->>'total')::numeric, -1) <= 0
     or (p_order->>'total')::numeric <>
        (p_order->>'subtotal')::numeric - coalesce((p_order->>'discount_amount')::numeric, 0)
     or coalesce((p_order->>'paid_amount')::numeric, -1) < (p_order->>'total')::numeric
     or coalesce((p_order->>'change_amount')::numeric, -1) <>
        (p_order->>'paid_amount')::numeric - (p_order->>'total')::numeric then
    raise exception 'Nominal transaksi tidak valid';
  end if;

  if nullif(p_order->>'order_number', '') is null
     or p_order->>'payment_method' is null
     or p_order->>'payment_method' not in ('CASH', 'QRIS', 'TRANSFER') then
    raise exception 'Nomor atau metode pembayaran transaksi tidak valid';
  end if;

  if p_order->>'payment_method' <> 'CASH'
     and (
       (p_order->>'paid_amount')::numeric <> (p_order->>'total')::numeric
       or (p_order->>'change_amount')::numeric <> 0
     ) then
    raise exception 'Pembayaran non-tunai harus sama dengan total transaksi';
  end if;

  if exists (
    select 1
    from jsonb_array_elements(p_items) as entries(item)
    where jsonb_typeof(item) <> 'object'
       or coalesce((item->>'quantity')::numeric, 0) <= 0
       or coalesce((item->>'unit_price')::numeric, -1) <= 0
       or coalesce((item->>'line_total')::numeric, -1) <= 0
       or nullif(item->>'product_name', '') is null
       or nullif(item->>'product_local_id', '') is null
       or (item->>'line_total')::numeric <>
          (item->>'quantity')::numeric * (item->>'unit_price')::numeric
  ) then
    raise exception 'Rincian transaksi tidak valid';
  end if;

  if (
    select coalesce(sum((item->>'line_total')::numeric), 0)
    from jsonb_array_elements(p_items) as entries(item)
  ) <> (p_order->>'subtotal')::numeric then
    raise exception 'Subtotal tidak cocok dengan rincian transaksi';
  end if;

  insert into public.orders (
    store_id, branch_id, local_id, order_number, order_date, order_time,
    created_at, created_by, order_type, subtotal, discount_amount, total,
    order_status, payment_status, payment_method, paid_amount, change_amount
  ) values (
    (p_order->>'store_id')::uuid,
    (p_order->>'branch_id')::uuid,
    p_order->>'local_id',
    p_order->>'order_number',
    (p_order->>'order_date')::date,
    p_order->>'order_time',
    (p_order->>'created_at')::timestamptz,
    (p_order->>'created_by')::uuid,
    coalesce(p_order->>'order_type', 'Takeaway'),
    (p_order->>'subtotal')::numeric,
    coalesce((p_order->>'discount_amount')::numeric, 0),
    (p_order->>'total')::numeric,
    coalesce(p_order->>'order_status', 'Selesai'),
    coalesce(p_order->>'payment_status', 'Lunas'),
    p_order->>'payment_method',
    (p_order->>'paid_amount')::numeric,
    (p_order->>'change_amount')::numeric
  )
  on conflict (branch_id, local_id) do update set
    store_id = excluded.store_id,
    order_number = excluded.order_number,
    order_date = excluded.order_date,
    order_time = excluded.order_time,
    created_at = excluded.created_at,
    created_by = excluded.created_by,
    order_type = excluded.order_type,
    subtotal = excluded.subtotal,
    discount_amount = excluded.discount_amount,
    total = excluded.total,
    order_status = excluded.order_status,
    payment_status = excluded.payment_status,
    payment_method = excluded.payment_method,
    paid_amount = excluded.paid_amount,
    change_amount = excluded.change_amount,
    updated_at = now()
  where public.orders.store_id = excluded.store_id
  returning id into v_order_id;

  if v_order_id is null then
    raise exception 'Transaksi lokal sudah terdaftar pada toko berbeda';
  end if;

  delete from public.order_items where order_id = v_order_id;

  insert into public.order_items (
    order_id, product_id, product_local_id, product_name,
    unit_price, quantity, line_total
  )
  select
    v_order_id,
    nullif(item->>'product_id', '')::uuid,
    item->>'product_local_id',
    item->>'product_name',
    (item->>'unit_price')::numeric,
    (item->>'quantity')::numeric,
    (item->>'line_total')::numeric
  from jsonb_array_elements(p_items) as entries(item);

  perform private.consume_order_inventory(
    v_order_id,
    (p_order->>'store_id')::uuid,
    (p_order->>'branch_id')::uuid
  );

  return v_order_id;
end;
$$;

revoke all on function public.sync_order(jsonb, jsonb) from public, anon;
grant execute on function public.sync_order(jsonb, jsonb) to authenticated;
