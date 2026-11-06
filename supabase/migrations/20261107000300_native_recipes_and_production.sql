-- Native ingredient recipes and pre-production workflow.
-- Production consumes raw ingredients first and increases finished catalog stock.
create table if not exists public.catalog_ingredients (
  id uuid primary key,
  store_id uuid not null references public.stores(id) on delete cascade,
  name text not null check (length(btrim(name)) between 1 and 160),
  unit text not null check (length(btrim(unit)) between 1 and 24),
  is_active boolean not null default true,
  updated_at timestamptz not null default now(),
  unique (store_id, id),
  unique (store_id, name)
);

create table if not exists public.branch_ingredient_inventory (
  store_id uuid not null,
  branch_id uuid not null,
  ingredient_id uuid not null,
  quantity numeric(14,3) not null default 0 check (quantity >= 0),
  updated_at timestamptz not null default now(),
  primary key (branch_id, ingredient_id),
  unique (store_id, branch_id, ingredient_id),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade,
  foreign key (store_id, ingredient_id) references public.catalog_ingredients(store_id, id) on delete cascade
);

create table if not exists public.product_recipes (
  id uuid primary key,
  store_id uuid not null,
  product_id uuid not null,
  name text not null check (length(btrim(name)) between 1 and 160),
  output_quantity integer not null default 1 check (output_quantity > 0),
  is_active boolean not null default true,
  updated_at timestamptz not null default now(),
  unique (store_id, id),
  unique (store_id, product_id),
  foreign key (store_id, product_id) references public.catalog_products(store_id, id) on delete cascade
);

create table if not exists public.product_recipe_items (
  id uuid primary key,
  store_id uuid not null,
  recipe_id uuid not null,
  ingredient_id uuid not null,
  quantity numeric(14,3) not null check (quantity > 0),
  created_at timestamptz not null default now(),
  unique (recipe_id, ingredient_id),
  foreign key (store_id, recipe_id) references public.product_recipes(store_id, id) on delete cascade,
  foreign key (store_id, ingredient_id) references public.catalog_ingredients(store_id, id) on delete restrict
);

create table if not exists public.production_batches (
  id uuid primary key,
  store_id uuid not null,
  branch_id uuid not null,
  recipe_id uuid not null,
  product_id uuid not null,
  output_quantity integer not null check (output_quantity > 0),
  produced_by uuid not null references auth.users(id),
  notes text,
  created_at timestamptz not null default now(),
  foreign key (store_id, branch_id) references public.branches(store_id, id) on delete cascade,
  foreign key (store_id, recipe_id) references public.product_recipes(store_id, id),
  foreign key (store_id, product_id) references public.catalog_products(store_id, id)
);

create table if not exists public.production_batch_items (
  id uuid primary key,
  store_id uuid not null,
  branch_id uuid not null,
  batch_id uuid not null references public.production_batches(id) on delete cascade,
  ingredient_id uuid not null,
  quantity_used numeric(14,3) not null check (quantity_used > 0),
  created_at timestamptz not null default now(),
  foreign key (store_id, branch_id, ingredient_id)
    references public.branch_ingredient_inventory(store_id, branch_id, ingredient_id),
  foreign key (store_id, ingredient_id)
    references public.catalog_ingredients(store_id, id)
);

create index if not exists idx_branch_ingredient_stock on public.branch_ingredient_inventory(store_id, branch_id, ingredient_id);
create index if not exists idx_recipe_items_recipe on public.product_recipe_items(store_id, recipe_id);
create index if not exists idx_production_batches_branch on public.production_batches(store_id, branch_id, created_at desc);

alter table public.catalog_ingredients enable row level security;
alter table public.branch_ingredient_inventory enable row level security;
alter table public.product_recipes enable row level security;
alter table public.product_recipe_items enable row level security;
alter table public.production_batches enable row level security;
alter table public.production_batch_items enable row level security;

create policy catalog_ingredients_read on public.catalog_ingredients for select to authenticated
  using (private.is_store_member(store_id));
create policy catalog_ingredients_manage on public.catalog_ingredients for all to authenticated
  using (private.is_store_member(store_id) and exists (select 1 from public.store_memberships sm where sm.store_id = catalog_ingredients.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin')))
  with check (private.is_store_member(store_id) and exists (select 1 from public.store_memberships sm where sm.store_id = catalog_ingredients.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin')));

create policy branch_ingredient_inventory_read on public.branch_ingredient_inventory for select to authenticated
  using (private.has_branch_access(store_id, branch_id));
create policy branch_ingredient_inventory_manage on public.branch_ingredient_inventory for all to authenticated
  using (private.has_branch_access(store_id, branch_id) and exists (select 1 from public.store_memberships sm where sm.store_id = branch_ingredient_inventory.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin','supervisor')))
  with check (private.has_branch_access(store_id, branch_id) and exists (select 1 from public.store_memberships sm where sm.store_id = branch_ingredient_inventory.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin','supervisor')));

create policy product_recipes_read on public.product_recipes for select to authenticated using (private.is_store_member(store_id));
create policy product_recipes_manage on public.product_recipes for all to authenticated
  using (private.is_store_member(store_id) and exists (select 1 from public.store_memberships sm where sm.store_id = product_recipes.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin')))
  with check (private.is_store_member(store_id) and exists (select 1 from public.store_memberships sm where sm.store_id = product_recipes.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin')));

create policy product_recipe_items_read on public.product_recipe_items for select to authenticated using (private.is_store_member(store_id));
create policy product_recipe_items_manage on public.product_recipe_items for all to authenticated
  using (private.is_store_member(store_id) and exists (select 1 from public.store_memberships sm where sm.store_id = product_recipe_items.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin')))
  with check (private.is_store_member(store_id) and exists (select 1 from public.store_memberships sm where sm.store_id = product_recipe_items.store_id and sm.user_id = (select auth.uid()) and sm.is_active and sm.role in ('owner','admin')));

create policy production_batches_read on public.production_batches for select to authenticated using (private.has_branch_access(store_id, branch_id));
create policy production_batch_items_read on public.production_batch_items for select to authenticated using (private.has_branch_access(store_id, branch_id));

grant select, insert, update, delete on public.catalog_ingredients, public.branch_ingredient_inventory, public.product_recipes, public.product_recipe_items to authenticated;
grant select on public.production_batches, public.production_batch_items to authenticated;

-- The only write path for production is this atomic RPC. It validates role, recipe,
-- branch, ingredient availability, then consumes ingredients and adds finished stock.
create or replace function public.complete_native_production(
  p_store_id uuid, p_branch_id uuid, p_batch_id uuid, p_recipe_id uuid,
  p_output_quantity integer, p_notes text default null
) returns uuid
language plpgsql security definer set search_path = ''
as $$
declare
  v_product_id uuid;
  v_output_per_recipe integer;
  v_ingredient record;
  v_required numeric(14,3);
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then raise exception 'Harus login terlebih dahulu'; end if;
  if p_output_quantity <= 0 then raise exception 'Jumlah produksi harus lebih dari nol'; end if;
  if not private.has_branch_access(p_store_id, p_branch_id) then raise exception 'Tidak memiliki akses ke cabang ini'; end if;
  if not exists (select 1 from public.store_memberships sm where sm.store_id = p_store_id and sm.user_id = v_user_id and sm.is_active and sm.role in ('owner','admin','supervisor')) then
    raise exception 'Hanya Owner, Admin, atau Supervisor yang dapat melakukan produksi';
  end if;
  select r.product_id, r.output_quantity into v_product_id, v_output_per_recipe
  from public.product_recipes r where r.id = p_recipe_id and r.store_id = p_store_id and r.is_active;
  if v_product_id is null then raise exception 'Resep tidak ditemukan atau tidak aktif'; end if;
  if not exists (select 1 from public.catalog_products p where p.id = v_product_id and p.store_id = p_store_id and p.is_active) then
    raise exception 'Produk hasil produksi tidak aktif';
  end if;
  if exists (select 1 from public.production_batches b where b.id = p_batch_id) then raise exception 'ID produksi sudah digunakan'; end if;
  for v_ingredient in
    select ri.ingredient_id, ri.quantity
    from public.product_recipe_items ri
    where ri.store_id = p_store_id and ri.recipe_id = p_recipe_id
    order by ri.ingredient_id
  loop
    v_required := v_ingredient.quantity * (p_output_quantity::numeric / v_output_per_recipe);
    update public.branch_ingredient_inventory bi
       set quantity = bi.quantity - v_required, updated_at = now()
     where bi.store_id = p_store_id and bi.branch_id = p_branch_id
       and bi.ingredient_id = v_ingredient.ingredient_id and bi.quantity >= v_required;
    if not found then raise exception 'Stok bahan baku tidak cukup: %', v_ingredient.ingredient_id; end if;
    insert into public.production_batch_items (id, store_id, branch_id, batch_id, ingredient_id, quantity_used)
    values (gen_random_uuid(), p_store_id, p_branch_id, p_batch_id, v_ingredient.ingredient_id, v_required);
  end loop;
  if not exists (select 1 from public.product_recipe_items ri where ri.store_id = p_store_id and ri.recipe_id = p_recipe_id) then
    raise exception 'Resep belum memiliki bahan baku';
  end if;
  insert into public.production_batches (id, store_id, branch_id, recipe_id, product_id, output_quantity, produced_by, notes)
  values (p_batch_id, p_store_id, p_branch_id, p_recipe_id, v_product_id, p_output_quantity, v_user_id, nullif(btrim(p_notes), ''));
  insert into public.branch_inventory (store_id, branch_id, product_id, quantity, updated_at)
  values (p_store_id, p_branch_id, v_product_id, p_output_quantity, now())
  on conflict (branch_id, product_id) do update
    set quantity = public.branch_inventory.quantity + excluded.quantity, updated_at = now();
  return p_batch_id;
end;
$$;

revoke all on function public.complete_native_production(uuid, uuid, uuid, uuid, integer, text) from public, anon;
grant execute on function public.complete_native_production(uuid, uuid, uuid, uuid, integer, text) to authenticated;
