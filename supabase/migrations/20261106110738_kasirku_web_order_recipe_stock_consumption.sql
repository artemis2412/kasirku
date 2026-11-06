-- Idempotent ingredient deduction for paid Web orders, which are locally persisted today.
create table if not exists private.web_inventory_consumption_runs (
 store_id uuid not null,
 branch_id uuid not null,
 order_local_id text not null,
 processed_by uuid not null references auth.users(id),
 processed_at timestamptz not null default now(),
 primary key(store_id,branch_id,order_local_id)
);

create or replace function private.consume_web_order_inventory(
 p_store_id uuid,p_branch_id uuid,p_order_local_id text,p_items jsonb
) returns void
language plpgsql security definer set search_path=''
as $$
declare v_line record; v_on_hand numeric(14,3); v_cost numeric(14,2); v_user uuid := (select auth.uid());
begin
 if v_user is null then raise exception 'Harus login terlebih dahulu'; end if;
 if not private.has_store_role(p_store_id,array['owner','admin','supervisor','cashier'])
  or not private.has_branch_access(p_store_id,p_branch_id) then raise exception 'Tidak memiliki akses transaksi pada cabang ini'; end if;
 if nullif(trim(coalesce(p_order_local_id,'')),'') is null or length(p_order_local_id)>120 then raise exception 'Identitas transaksi Web tidak valid'; end if;
 if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)=0 then raise exception 'Rincian transaksi Web tidak valid'; end if;
 if exists(select 1 from jsonb_array_elements(p_items) x(item)
  where nullif(x.item->>'product_local_id','') is null
   or coalesce((x.item->>'quantity')::numeric,0)<=0
   or not exists(select 1 from public.products p where p.store_id=p_store_id and p.branch_id=p_branch_id
     and p.local_id=x.item->>'product_local_id' and p.is_active=true)) then
  raise exception 'Ada menu transaksi yang belum tersinkron ke katalog cabang';
 end if;

 insert into private.web_inventory_consumption_runs(store_id,branch_id,order_local_id,processed_by)
 values(p_store_id,p_branch_id,p_order_local_id,v_user)
 on conflict(store_id,branch_id,order_local_id) do nothing;
 if not found then return; end if;

 for v_line in
  select m.inventory_item_id,
   sum((x.item->>'quantity')::numeric*m.quantity_per_menu)::numeric(14,3) as quantity_used
  from jsonb_array_elements(p_items) x(item)
  join public.products p on p.store_id=p_store_id and p.branch_id=p_branch_id
   and p.local_id=x.item->>'product_local_id' and p.is_active=true
  join public.menu_recipe_items m on m.product_id=p.id and m.store_id=p_store_id and m.branch_id=p_branch_id
  group by m.inventory_item_id order by m.inventory_item_id
 loop
  select i.on_hand,i.average_unit_cost into v_on_hand,v_cost
  from public.inventory_items i where i.id=v_line.inventory_item_id
   and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true
  for update;
  if not found then raise exception 'Bahan resep menu tidak ditemukan atau tidak aktif'; end if;
  if v_on_hand<v_line.quantity_used then
   raise exception 'Stok bahan tidak mencukupi untuk penjualan. Tersedia %, dibutuhkan %',v_on_hand,v_line.quantity_used;
  end if;
  update public.inventory_items set on_hand=on_hand-v_line.quantity_used,updated_at=now()
   where id=v_line.inventory_item_id and store_id=p_store_id and branch_id=p_branch_id;
  insert into public.inventory_movements(store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,
   reference_type,idempotency_key,note,created_by)
  values(p_store_id,p_branch_id,v_line.inventory_item_id,'menu_sale',-v_line.quantity_used,v_cost,
   'web_order','web:'||p_order_local_id||':'||v_line.inventory_item_id::text,
   'Pemakaian bahan dari penjualan Web',v_user);
 end loop;
end;
$$;
revoke all on function private.consume_web_order_inventory(uuid,uuid,text,jsonb) from public,anon;
grant execute on function private.consume_web_order_inventory(uuid,uuid,text,jsonb) to authenticated;

create or replace function public.consume_web_order_inventory(
 p_store_id uuid,p_branch_id uuid,p_order_local_id text,p_items jsonb
) returns void
language plpgsql security invoker set search_path=''
as $$
begin
 if (select auth.uid()) is null then raise exception 'Harus login terlebih dahulu'; end if;
 perform private.consume_web_order_inventory(p_store_id,p_branch_id,p_order_local_id,p_items);
end;
$$;
revoke all on function public.consume_web_order_inventory(uuid,uuid,text,jsonb) from public,anon;
grant execute on function public.consume_web_order_inventory(uuid,uuid,text,jsonb) to authenticated;
