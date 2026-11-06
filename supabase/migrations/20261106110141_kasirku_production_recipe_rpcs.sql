-- Atomic stock adjustment, production recipe editing and menu recipe editing RPCs.
create or replace function public.adjust_inventory_stock(
 p_store_id uuid,p_branch_id uuid,p_item_id uuid,p_quantity_delta numeric,
 p_movement_type text,p_unit_cost numeric default 0,p_note text default '',p_idempotency_key text default null
) returns uuid language plpgsql security invoker set search_path='' as $$
declare v_item public.inventory_items%rowtype; v_id uuid; v_avg numeric(14,2);
begin
 if (select auth.uid()) is null then raise exception 'Harus login terlebih dahulu'; end if;
 if not private.has_store_role(p_store_id,array['owner','admin','supervisor']) or not private.has_branch_access(p_store_id,p_branch_id) then raise exception 'Tidak memiliki akses pengelolaan stok pada cabang ini'; end if;
 if p_quantity_delta is null or p_quantity_delta=0 or p_unit_cost is null or p_unit_cost<0 then raise exception 'Jumlah dan biaya stok tidak valid'; end if;
 if p_movement_type not in ('opening','purchase','waste','adjustment') then raise exception 'Jenis pergerakan stok tidak diizinkan'; end if;
 if p_movement_type in ('opening','purchase') and p_quantity_delta<0 then raise exception 'Stok masuk harus bernilai positif'; end if;
 if p_movement_type='waste' and p_quantity_delta>0 then raise exception 'Stok terbuang harus bernilai negatif'; end if;
 if nullif(trim(coalesce(p_idempotency_key,'')),'') is not null then
  select id into v_id from public.inventory_movements where store_id=p_store_id and branch_id=p_branch_id and idempotency_key=p_idempotency_key;
  if found then return v_id; end if;
 end if;
 select * into v_item from public.inventory_items where id=p_item_id and store_id=p_store_id and branch_id=p_branch_id and is_active=true for update;
 if not found then raise exception 'Bahan tidak ditemukan atau tidak aktif'; end if;
 if v_item.on_hand+p_quantity_delta<0 then raise exception 'Stok % tidak mencukupi. Tersedia %, perubahan %',v_item.name,v_item.on_hand,p_quantity_delta; end if;
 v_avg:=v_item.average_unit_cost;
 if p_quantity_delta>0 and p_movement_type in ('opening','purchase') then
  v_avg:=case when v_item.on_hand+p_quantity_delta>0 then round(((v_item.on_hand*v_item.average_unit_cost)+(p_quantity_delta*p_unit_cost))/(v_item.on_hand+p_quantity_delta),2) else p_unit_cost end;
 end if;
 update public.inventory_items set on_hand=on_hand+p_quantity_delta,average_unit_cost=v_avg,updated_at=now() where id=p_item_id and store_id=p_store_id and branch_id=p_branch_id;
 insert into public.inventory_movements(store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,idempotency_key,note,created_by)
 values(p_store_id,p_branch_id,p_item_id,p_movement_type,p_quantity_delta,p_unit_cost,nullif(trim(coalesce(p_idempotency_key,'')),''),coalesce(p_note,''),(select auth.uid())) returning id into v_id;
 return v_id;
end; $$;
revoke all on function public.adjust_inventory_stock(uuid,uuid,uuid,numeric,text,numeric,text,text) from public,anon;
grant execute on function public.adjust_inventory_stock(uuid,uuid,uuid,numeric,text,numeric,text,text) to authenticated;

create or replace function public.save_production_recipe(
 p_store_id uuid,p_branch_id uuid,p_recipe_id uuid,p_name text,p_output_item_id uuid,
 p_yield_quantity numeric,p_notes text,p_items jsonb
) returns uuid language plpgsql security invoker set search_path='' as $$
declare v_id uuid; v_output public.inventory_items%rowtype;
begin
 if (select auth.uid()) is null then raise exception 'Harus login terlebih dahulu'; end if;
 if not private.has_store_role(p_store_id,array['owner','admin','supervisor']) or not private.has_branch_access(p_store_id,p_branch_id) then raise exception 'Tidak memiliki akses pengelolaan resep pada cabang ini'; end if;
 if length(trim(coalesce(p_name,''))) not between 1 and 120 or p_yield_quantity is null or p_yield_quantity<=0 then raise exception 'Nama resep dan hasil standar wajib diisi dengan benar'; end if;
 if p_items is null or jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)=0 then raise exception 'Resep harus memiliki minimal satu bahan'; end if;
 if exists(select 1 from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity numeric) where x.inventory_item_id is null or x.quantity is null or x.quantity<=0)
 or (select count(*) from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity numeric))<>(select count(distinct x.inventory_item_id) from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity numeric)) then raise exception 'Daftar bahan resep tidak valid atau mengandung duplikat'; end if;
 select * into v_output from public.inventory_items where id=p_output_item_id and store_id=p_store_id and branch_id=p_branch_id and is_active=true and item_type='prepared';
 if not found then raise exception 'Hasil resep harus berupa bahan siap pakai aktif'; end if;
 if exists(select 1 from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity numeric) where not exists(select 1 from public.inventory_items i where i.id=x.inventory_item_id and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true and i.id<>p_output_item_id)) then raise exception 'Ada bahan resep yang tidak aktif atau bukan milik cabang ini'; end if;
 if p_recipe_id is null then
  insert into public.production_recipes(store_id,branch_id,name,output_item_id,yield_quantity,notes,created_by) values(p_store_id,p_branch_id,trim(p_name),p_output_item_id,p_yield_quantity,coalesce(p_notes,''),(select auth.uid())) returning id into v_id;
 else
  update public.production_recipes set name=trim(p_name),output_item_id=p_output_item_id,yield_quantity=p_yield_quantity,notes=coalesce(p_notes,''),updated_at=now() where id=p_recipe_id and store_id=p_store_id and branch_id=p_branch_id returning id into v_id;
  if v_id is null then raise exception 'Resep yang akan diedit tidak ditemukan'; end if;
  delete from public.production_recipe_items where recipe_id=v_id and store_id=p_store_id and branch_id=p_branch_id;
 end if;
 insert into public.production_recipe_items(store_id,branch_id,recipe_id,inventory_item_id,quantity)
 select p_store_id,p_branch_id,v_id,x.inventory_item_id,x.quantity from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity numeric);
 return v_id;
end; $$;
revoke all on function public.save_production_recipe(uuid,uuid,uuid,text,uuid,numeric,text,jsonb) from public,anon;
grant execute on function public.save_production_recipe(uuid,uuid,uuid,text,uuid,numeric,text,jsonb) to authenticated;

create or replace function public.replace_menu_recipe(p_store_id uuid,p_branch_id uuid,p_product_id uuid,p_items jsonb)
returns integer language plpgsql security invoker set search_path='' as $$
declare v_count integer;
begin
 if (select auth.uid()) is null then raise exception 'Harus login terlebih dahulu'; end if;
 if not private.has_store_role(p_store_id,array['owner','admin','supervisor']) or not private.has_branch_access(p_store_id,p_branch_id) then raise exception 'Tidak memiliki akses pengelolaan resep menu pada cabang ini'; end if;
 if not exists(select 1 from public.products p where p.id=p_product_id and p.store_id=p_store_id and p.branch_id=p_branch_id and p.is_active=true) then raise exception 'Menu tidak ditemukan atau tidak aktif'; end if;
 if p_items is null or jsonb_typeof(p_items)<>'array' then raise exception 'Komposisi menu tidak valid'; end if;
 if exists(select 1 from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity_per_menu numeric) where x.inventory_item_id is null or x.quantity_per_menu is null or x.quantity_per_menu<=0 or not exists(select 1 from public.inventory_items i where i.id=x.inventory_item_id and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true))
 or (select count(*) from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity_per_menu numeric))<>(select count(distinct x.inventory_item_id) from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity_per_menu numeric)) then raise exception 'Ada bahan menu yang tidak valid atau duplikat'; end if;
 delete from public.menu_recipe_items where store_id=p_store_id and branch_id=p_branch_id and product_id=p_product_id;
 insert into public.menu_recipe_items(store_id,branch_id,product_id,inventory_item_id,quantity_per_menu)
 select p_store_id,p_branch_id,p_product_id,x.inventory_item_id,x.quantity_per_menu from jsonb_to_recordset(p_items) x(inventory_item_id uuid,quantity_per_menu numeric);
 get diagnostics v_count=row_count;
 return v_count;
end; $$;
revoke all on function public.replace_menu_recipe(uuid,uuid,uuid,jsonb) from public,anon;
grant execute on function public.replace_menu_recipe(uuid,uuid,uuid,jsonb) to authenticated;

create or replace function public.complete_production_batch(
 p_store_id uuid,p_branch_id uuid,p_recipe_id uuid,p_output_quantity numeric,
 p_actual_inputs jsonb default '[]'::jsonb,p_notes text default ''
) returns uuid language plpgsql security invoker set search_path='' as $$
declare v_recipe public.production_recipes%rowtype; v_output public.inventory_items%rowtype; v_input public.inventory_items%rowtype;
 v_line record; v_actual numeric(14,3); v_ratio numeric; v_total_cost numeric(14,2):=0; v_batch_id uuid;
begin
 if (select auth.uid()) is null then raise exception 'Harus login terlebih dahulu'; end if;
 if not private.has_store_role(p_store_id,array['owner','admin','supervisor']) or not private.has_branch_access(p_store_id,p_branch_id) then raise exception 'Tidak memiliki akses produksi pada cabang ini'; end if;
 if p_output_quantity is null or p_output_quantity<=0 then raise exception 'Jumlah hasil produksi harus lebih dari nol'; end if;
 if p_actual_inputs is null or jsonb_typeof(p_actual_inputs)<>'array' then raise exception 'Data pemakaian bahan tidak valid'; end if;
 select * into v_recipe from public.production_recipes r where r.id=p_recipe_id and r.store_id=p_store_id and r.branch_id=p_branch_id and r.is_active=true;
 if not found then raise exception 'Resep produksi tidak ditemukan atau tidak aktif'; end if;
 v_ratio:=p_output_quantity/v_recipe.yield_quantity;
 if exists(select 1 from jsonb_to_recordset(p_actual_inputs) x(inventory_item_id uuid,quantity numeric) where x.inventory_item_id is null or x.quantity is null or x.quantity<=0 or not exists(select 1 from public.production_recipe_items ri where ri.recipe_id=p_recipe_id and ri.store_id=p_store_id and ri.branch_id=p_branch_id and ri.inventory_item_id=x.inventory_item_id))
 or (select count(*) from jsonb_to_recordset(p_actual_inputs) x(inventory_item_id uuid,quantity numeric))<>(select count(distinct x.inventory_item_id) from jsonb_to_recordset(p_actual_inputs) x(inventory_item_id uuid,quantity numeric)) then raise exception 'Ada pemakaian bahan yang tidak sesuai resep atau duplikat'; end if;
 select * into v_output from public.inventory_items i where i.id=v_recipe.output_item_id and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true for update;
 if not found or v_output.item_type<>'prepared' then raise exception 'Barang hasil produksi harus berupa bahan siap pakai aktif'; end if;
 insert into public.production_batches(store_id,branch_id,recipe_id,output_item_id,planned_output_quantity,actual_output_quantity,notes,created_by) values(p_store_id,p_branch_id,p_recipe_id,v_recipe.output_item_id,v_recipe.yield_quantity*v_ratio,p_output_quantity,coalesce(p_notes,''),(select auth.uid())) returning id into v_batch_id;
 for v_line in select ri.inventory_item_id,ri.quantity from public.production_recipe_items ri where ri.recipe_id=p_recipe_id and ri.store_id=p_store_id and ri.branch_id=p_branch_id order by ri.inventory_item_id loop
  select * into v_input from public.inventory_items i where i.id=v_line.inventory_item_id and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true for update;
  if not found then raise exception 'Bahan resep tidak ditemukan atau tidak aktif'; end if;
  select coalesce(x.quantity,v_line.quantity*v_ratio) into v_actual from (select 1) seed left join jsonb_to_recordset(p_actual_inputs) x(inventory_item_id uuid,quantity numeric) on x.inventory_item_id=v_line.inventory_item_id;
  if v_actual<=0 or v_input.on_hand<v_actual then raise exception 'Stok bahan % tidak mencukupi. Tersedia %, dibutuhkan %',v_input.name,v_input.on_hand,v_actual; end if;
  update public.inventory_items set on_hand=on_hand-v_actual,updated_at=now() where id=v_input.id and store_id=p_store_id and branch_id=p_branch_id;
  insert into public.production_batch_inputs(store_id,branch_id,batch_id,inventory_item_id,quantity_used,unit_cost,total_cost) values(p_store_id,p_branch_id,v_batch_id,v_input.id,v_actual,v_input.average_unit_cost,round(v_actual*v_input.average_unit_cost,2));
  insert into public.inventory_movements(store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,reference_type,reference_id,created_by) values(p_store_id,p_branch_id,v_input.id,'production_input',-v_actual,v_input.average_unit_cost,'production_batch',v_batch_id,(select auth.uid()));
  v_total_cost:=v_total_cost+round(v_actual*v_input.average_unit_cost,2);
 end loop;
 if not exists(select 1 from public.production_batch_inputs bi where bi.batch_id=v_batch_id and bi.store_id=p_store_id and bi.branch_id=p_branch_id) then raise exception 'Resep belum memiliki bahan baku'; end if;
 update public.inventory_items set average_unit_cost=case when on_hand+p_output_quantity>0 then round(((on_hand*average_unit_cost)+v_total_cost)/(on_hand+p_output_quantity),2) else 0 end,on_hand=on_hand+p_output_quantity,updated_at=now() where id=v_output.id and store_id=p_store_id and branch_id=p_branch_id;
 update public.production_batches set total_input_cost=v_total_cost where id=v_batch_id and store_id=p_store_id and branch_id=p_branch_id;
 insert into public.inventory_movements(store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,reference_type,reference_id,created_by) values(p_store_id,p_branch_id,v_output.id,'production_output',p_output_quantity,round(v_total_cost/p_output_quantity,2),'production_batch',v_batch_id,(select auth.uid()));
 return v_batch_id;
end; $$;
