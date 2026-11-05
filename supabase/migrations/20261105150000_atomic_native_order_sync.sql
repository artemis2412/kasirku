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
begin
  if auth.uid() is null then
    raise exception 'Harus login terlebih dahulu';
  end if;

  if jsonb_typeof(p_order) <> 'object' or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) = 0 then
    raise exception 'Payload transaksi tidak valid';
  end if;

  -- Do not allow an authenticated branch member to attribute a sale to another user.
  if nullif(p_order->>'created_by', '')::uuid is distinct from auth.uid() then
    raise exception 'Kasir transaksi tidak sesuai akun yang login';
  end if;

  if nullif(p_order->>'store_id', '') is null
     or nullif(p_order->>'branch_id', '') is null
     or nullif(p_order->>'local_id', '') is null then
    raise exception 'Identitas toko, cabang, atau transaksi tidak lengkap';
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

  if exists (
    select 1
    from jsonb_array_elements(p_items) as item
    where jsonb_typeof(item) <> 'object'
       or coalesce((item->>'quantity')::numeric, 0) <= 0
       or coalesce((item->>'unit_price')::numeric, -1) < 0
       or coalesce((item->>'line_total')::numeric, -1) < 0
       or (item->>'line_total')::numeric <>
          (item->>'quantity')::numeric * (item->>'unit_price')::numeric
  ) then
    raise exception 'Rincian transaksi tidak valid';
  end if;

  if (
    select coalesce(sum((item->>'line_total')::numeric), 0)
    from jsonb_array_elements(p_items) as item
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
  from jsonb_array_elements(p_items) as item;

  return v_order_id;
end;
$$;

revoke all on function public.sync_order(jsonb, jsonb) from public, anon;
grant execute on function public.sync_order(jsonb, jsonb) to authenticated;
