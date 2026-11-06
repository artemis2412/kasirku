-- Cover branch-scoped foreign keys for inventory and recipe relations.
create index if not exists inventory_items_created_by_idx on public.inventory_items(created_by);
create index if not exists inventory_movements_created_by_idx on public.inventory_movements(created_by);
create index if not exists menu_recipe_items_inventory_fk_idx on public.menu_recipe_items(store_id,branch_id,inventory_item_id);
create index if not exists production_batch_inputs_batch_fk_idx on public.production_batch_inputs(store_id,branch_id,batch_id);
create index if not exists production_batch_inputs_item_fk_idx on public.production_batch_inputs(store_id,branch_id,inventory_item_id);
create index if not exists production_batches_created_by_idx on public.production_batches(created_by);
create index if not exists production_batches_output_item_fk_idx on public.production_batches(store_id,branch_id,output_item_id);
create index if not exists production_batches_recipe_fk_idx on public.production_batches(store_id,branch_id,recipe_id);
create index if not exists production_recipe_items_item_fk_idx on public.production_recipe_items(store_id,branch_id,inventory_item_id);
create index if not exists production_recipe_items_recipe_fk_idx on public.production_recipe_items(store_id,branch_id,recipe_id);
create index if not exists production_recipes_created_by_idx on public.production_recipes(created_by);
create index if not exists production_recipes_output_item_fk_idx on public.production_recipes(store_id,branch_id,output_item_id);
