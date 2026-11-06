-- Kasirku: branch-scoped raw/prepared inventory, production recipes and batch ledger.
alter table public.products
  add constraint products_store_branch_id_unique unique (store_id, branch_id, id);

create table public.inventory_items (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null references public.stores(id) on delete cascade,
 branch_id uuid not null,
 name text not null check (length(trim(name)) between 1 and 120),
 item_type text not null check (item_type in ('raw','prepared')),
 base_unit text not null check (length(trim(base_unit)) between 1 and 24),
 on_hand numeric(14,3) not null default 0 check (on_hand >= 0),
 minimum_stock numeric(14,3) not null default 0 check (minimum_stock >= 0),
 average_unit_cost numeric(14,2) not null default 0 check (average_unit_cost >= 0),
 is_active boolean not null default true,
 created_by uuid references auth.users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 foreign key (store_id,branch_id) references public.branches(store_id,id) on delete cascade,
 unique (store_id,branch_id,id),
 unique (store_id,branch_id,name)
);

create table public.production_recipes (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null references public.stores(id) on delete cascade,
 branch_id uuid not null,
 name text not null check (length(trim(name)) between 1 and 120),
 output_item_id uuid not null,
 yield_quantity numeric(14,3) not null check (yield_quantity > 0),
 notes text not null default '',
 is_active boolean not null default true,
 created_by uuid references auth.users(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 foreign key (store_id,branch_id) references public.branches(store_id,id) on delete cascade,
 foreign key (store_id,branch_id,output_item_id) references public.inventory_items(store_id,branch_id,id),
 unique (store_id,branch_id,id)
);

create table public.production_recipe_items (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null, branch_id uuid not null, recipe_id uuid not null,
 inventory_item_id uuid not null, quantity numeric(14,3) not null check (quantity > 0),
 created_at timestamptz not null default now(),
 foreign key (store_id,branch_id,recipe_id) references public.production_recipes(store_id,branch_id,id) on delete cascade,
 foreign key (store_id,branch_id,inventory_item_id) references public.inventory_items(store_id,branch_id,id),
 unique (recipe_id,inventory_item_id)
);

create table public.production_batches (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null, branch_id uuid not null, recipe_id uuid not null, output_item_id uuid not null,
 planned_output_quantity numeric(14,3) not null check (planned_output_quantity > 0),
 actual_output_quantity numeric(14,3) not null check (actual_output_quantity > 0),
 total_input_cost numeric(14,2) not null default 0 check (total_input_cost >= 0),
 notes text not null default '',
 status text not null default 'completed' check (status in ('completed','reversed')),
 created_by uuid not null references auth.users(id),
 completed_at timestamptz not null default now(), created_at timestamptz not null default now(),
 foreign key (store_id,branch_id,recipe_id) references public.production_recipes(store_id,branch_id,id),
 foreign key (store_id,branch_id,output_item_id) references public.inventory_items(store_id,branch_id,id),
 unique (store_id,branch_id,id)
);

create table public.production_batch_inputs (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null, branch_id uuid not null, batch_id uuid not null, inventory_item_id uuid not null,
 quantity_used numeric(14,3) not null check (quantity_used > 0),
 unit_cost numeric(14,2) not null default 0 check (unit_cost >= 0),
 total_cost numeric(14,2) not null default 0 check (total_cost >= 0),
 created_at timestamptz not null default now(),
 foreign key (store_id,branch_id,batch_id) references public.production_batches(store_id,branch_id,id) on delete cascade,
 foreign key (store_id,branch_id,inventory_item_id) references public.inventory_items(store_id,branch_id,id)
);

create table public.menu_recipe_items (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null, branch_id uuid not null, product_id uuid not null, inventory_item_id uuid not null,
 quantity_per_menu numeric(14,3) not null check (quantity_per_menu > 0),
 created_at timestamptz not null default now(), updated_at timestamptz not null default now(),
 foreign key (store_id,branch_id,product_id) references public.products(store_id,branch_id,id) on delete cascade,
 foreign key (store_id,branch_id,inventory_item_id) references public.inventory_items(store_id,branch_id,id),
 unique (product_id,inventory_item_id)
);

create table public.inventory_movements (
 id uuid primary key default gen_random_uuid(),
 store_id uuid not null, branch_id uuid not null, inventory_item_id uuid not null,
 movement_type text not null check (movement_type in ('opening','purchase','production_input','production_output','menu_sale','waste','adjustment','reversal')),
 quantity_delta numeric(14,3) not null check (quantity_delta <> 0),
 unit_cost numeric(14,2) not null default 0 check (unit_cost >= 0),
 reference_type text, reference_id uuid, idempotency_key text, note text not null default '',
 created_by uuid references auth.users(id), created_at timestamptz not null default now(),
 foreign key (store_id,branch_id,inventory_item_id) references public.inventory_items(store_id,branch_id,id),
 unique (store_id,branch_id,idempotency_key)
);

create index inventory_items_branch_type_idx on public.inventory_items(store_id,branch_id,item_type,is_active);
create index production_recipes_branch_idx on public.production_recipes(store_id,branch_id,is_active);
create index production_batches_branch_date_idx on public.production_batches(store_id,branch_id,completed_at desc);
create index inventory_movements_item_date_idx on public.inventory_movements(store_id,branch_id,inventory_item_id,created_at desc);
create index menu_recipe_items_product_idx on public.menu_recipe_items(store_id,branch_id,product_id);

alter table public.inventory_items enable row level security;
alter table public.production_recipes enable row level security;
alter table public.production_recipe_items enable row level security;
alter table public.production_batches enable row level security;
alter table public.production_batch_inputs enable row level security;
alter table public.menu_recipe_items enable row level security;
alter table public.inventory_movements enable row level security;

create policy inventory_items_read_branch on public.inventory_items for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy inventory_items_manager_insert on public.inventory_items for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy inventory_items_manager_update on public.inventory_items for update to authenticated using (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id)) with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy inventory_items_manager_delete on public.inventory_items for delete to authenticated using (private.has_store_role(store_id,array['owner','admin']) and private.has_branch_access(store_id,branch_id));

create policy production_recipes_read_branch on public.production_recipes for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy production_recipes_manager_insert on public.production_recipes for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy production_recipes_manager_update on public.production_recipes for update to authenticated using (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id)) with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy production_recipes_manager_delete on public.production_recipes for delete to authenticated using (private.has_store_role(store_id,array['owner','admin']) and private.has_branch_access(store_id,branch_id));

create policy production_recipe_items_read_branch on public.production_recipe_items for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy production_recipe_items_manager_insert on public.production_recipe_items for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy production_recipe_items_manager_update on public.production_recipe_items for update to authenticated using (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id)) with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy production_recipe_items_manager_delete on public.production_recipe_items for delete to authenticated using (private.has_store_role(store_id,array['owner','admin']) and private.has_branch_access(store_id,branch_id));

create policy production_batches_read_branch on public.production_batches for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy production_batches_manager_insert on public.production_batches for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id) and created_by=(select auth.uid()));
create policy production_batch_inputs_read_branch on public.production_batch_inputs for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy production_batch_inputs_manager_insert on public.production_batch_inputs for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));

create policy menu_recipe_items_read_branch on public.menu_recipe_items for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy menu_recipe_items_manager_insert on public.menu_recipe_items for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy menu_recipe_items_manager_update on public.menu_recipe_items for update to authenticated using (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id)) with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id));
create policy menu_recipe_items_manager_delete on public.menu_recipe_items for delete to authenticated using (private.has_store_role(store_id,array['owner','admin']) and private.has_branch_access(store_id,branch_id));

create policy inventory_movements_read_branch on public.inventory_movements for select to authenticated using (private.has_branch_access(store_id,branch_id));
create policy inventory_movements_manager_insert on public.inventory_movements for insert to authenticated with check (private.has_store_role(store_id,array['owner','admin','supervisor']) and private.has_branch_access(store_id,branch_id) and (created_by is null or created_by=(select auth.uid())));

grant select,insert,update,delete on public.inventory_items,public.production_recipes,public.production_recipe_items,public.production_batches,public.production_batch_inputs,public.menu_recipe_items,public.inventory_movements to authenticated;

create or replace function public.complete_production_batch(
 p_store_id uuid,p_branch_id uuid,p_recipe_id uuid,p_output_quantity numeric,
 p_actual_inputs jsonb default '[]'::jsonb,p_notes text default ''
) returns uuid language plpgsql security invoker set search_path='' as $function$
declare
 v_recipe public.production_recipes%rowtype;
 v_output public.inventory_items%rowtype;
 v_input public.inventory_items%rowtype;
 v_line record;
 v_actual numeric(14,3);
 v_ratio numeric;
 v_total_cost numeric(14,2):=0;
 v_batch_id uuid;
begin
 if (select auth.uid()) is null then raise exception 'Harus login terlebih dahulu'; end if;
 if not private.has_store_role(p_store_id,array['owner','admin','supervisor'])
    or not private.has_branch_access(p_store_id,p_branch_id) then
   raise exception 'Tidak memiliki akses produksi pada cabang ini';
 end if;
 if p_output_quantity is null or p_output_quantity<=0 then raise exception 'Jumlah hasil produksi harus lebih dari nol'; end if;
 if p_actual_inputs is null or jsonb_typeof(p_actual_inputs)<>'array' then raise exception 'Data pemakaian bahan tidak valid'; end if;

 select * into v_recipe from public.production_recipes r
 where r.id=p_recipe_id and r.store_id=p_store_id and r.branch_id=p_branch_id and r.is_active=true;
 if not found then raise exception 'Resep produksi tidak ditemukan atau tidak aktif'; end if;
 v_ratio:=p_output_quantity/v_recipe.yield_quantity;

 if exists (
  select 1 from jsonb_to_recordset(p_actual_inputs) as x(inventory_item_id uuid,quantity numeric)
  where x.inventory_item_id is null or x.quantity is null or x.quantity<=0
   or not exists(select 1 from public.production_recipe_items ri
    where ri.recipe_id=p_recipe_id and ri.store_id=p_store_id and ri.branch_id=p_branch_id
     and ri.inventory_item_id=x.inventory_item_id)
 ) then raise exception 'Ada pemakaian bahan yang tidak sesuai resep'; end if;

 select * into v_output from public.inventory_items i
 where i.id=v_recipe.output_item_id and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true
 for update;
 if not found or v_output.item_type<>'prepared' then raise exception 'Barang hasil produksi harus berupa bahan siap pakai aktif'; end if;

 insert into public.production_batches(store_id,branch_id,recipe_id,output_item_id,planned_output_quantity,actual_output_quantity,notes,created_by)
 values(p_store_id,p_branch_id,p_recipe_id,v_recipe.output_item_id,v_recipe.yield_quantity*v_ratio,p_output_quantity,coalesce(p_notes,''),(select auth.uid()))
 returning id into v_batch_id;

 for v_line in select ri.inventory_item_id,ri.quantity from public.production_recipe_items ri
  where ri.recipe_id=p_recipe_id and ri.store_id=p_store_id and ri.branch_id=p_branch_id
  order by ri.inventory_item_id
 loop
  select * into v_input from public.inventory_items i
  where i.id=v_line.inventory_item_id and i.store_id=p_store_id and i.branch_id=p_branch_id and i.is_active=true
  for update;
  if not found then raise exception 'Bahan resep tidak ditemukan atau tidak aktif'; end if;

  select coalesce(x.quantity,v_line.quantity*v_ratio) into v_actual
  from (select 1) seed
  left join jsonb_to_recordset(p_actual_inputs) as x(inventory_item_id uuid,quantity numeric)
   on x.inventory_item_id=v_line.inventory_item_id;

  if v_actual<=0 or v_input.on_hand<v_actual then
   raise exception 'Stok bahan % tidak mencukupi. Tersedia %, dibutuhkan %',v_input.name,v_input.on_hand,v_actual;
  end if;

  update public.inventory_items set on_hand=on_hand-v_actual,updated_at=now()
   where id=v_input.id and store_id=p_store_id and branch_id=p_branch_id;
  insert into public.production_batch_inputs(store_id,branch_id,batch_id,inventory_item_id,quantity_used,unit_cost,total_cost)
   values(p_store_id,p_branch_id,v_batch_id,v_input.id,v_actual,v_input.average_unit_cost,round(v_actual*v_input.average_unit_cost,2));
  insert into public.inventory_movements(store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,reference_type,reference_id,created_by)
   values(p_store_id,p_branch_id,v_input.id,'production_input',-v_actual,v_input.average_unit_cost,'production_batch',v_batch_id,(select auth.uid()));
  v_total_cost:=v_total_cost+round(v_actual*v_input.average_unit_cost,2);
 end loop;

 if not exists(select 1 from public.production_batch_inputs bi where bi.batch_id=v_batch_id and bi.store_id=p_store_id and bi.branch_id=p_branch_id) then
  raise exception 'Resep belum memiliki bahan baku';
 end if;

 update public.inventory_items set
  average_unit_cost=case when on_hand+p_output_quantity>0
   then round(((on_hand*average_unit_cost)+v_total_cost)/(on_hand+p_output_quantity),2) else 0 end,
  on_hand=on_hand+p_output_quantity,updated_at=now()
 where id=v_output.id and store_id=p_store_id and branch_id=p_branch_id;

 update public.production_batches set total_input_cost=v_total_cost
 where id=v_batch_id and store_id=p_store_id and branch_id=p_branch_id;

 insert into public.inventory_movements(store_id,branch_id,inventory_item_id,movement_type,quantity_delta,unit_cost,reference_type,reference_id,created_by)
 values(p_store_id,p_branch_id,v_output.id,'production_output',p_output_quantity,
  case when p_output_quantity>0 then round(v_total_cost/p_output_quantity,2) else 0 end,
  'production_batch',v_batch_id,(select auth.uid()));
 return v_batch_id;
end;
$function$;

revoke all on function public.complete_production_batch(uuid,uuid,uuid,numeric,jsonb,text) from public,anon;
grant execute on function public.complete_production_batch(uuid,uuid,uuid,numeric,jsonb,text) to authenticated;
