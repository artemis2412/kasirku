-- Shared branch-scoped table state for native and web clients.
-- Table IDs are client-generated UUID strings so offline-created tables can sync unchanged.

create table if not exists public.store_tables (
  id text not null,
  store_id uuid not null,
  branch_id uuid not null,
  name text not null,
  capacity integer not null default 4 check (capacity between 1 and 999),
  is_active boolean not null default true,
  occupied_order_local_id text,
  occupied_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (branch_id, id),
  unique (branch_id, name),
  foreign key (store_id, branch_id)
    references public.branches(store_id, id) on delete cascade,
  check (
    (occupied_order_local_id is null and occupied_at is null)
    or
    (occupied_order_local_id is not null and occupied_at is not null)
  )
);

create index if not exists idx_store_tables_branch_active
  on public.store_tables(store_id, branch_id, is_active, name);

alter table public.store_tables enable row level security;

drop policy if exists "store_tables_branch_read" on public.store_tables;
create policy "store_tables_branch_read"
on public.store_tables for select to authenticated
using (private.has_branch_access(store_id, branch_id));

drop policy if exists "store_tables_manage_owner_admin_supervisor" on public.store_tables;
create policy "store_tables_manage_owner_admin_supervisor"
on public.store_tables for all to authenticated
using (
  private.has_branch_access(store_id, branch_id)
  and exists (
    select 1 from public.store_memberships sm
    where sm.store_id = store_tables.store_id
      and sm.user_id = (select auth.uid())
      and sm.is_active = true
      and sm.role in ('owner','admin','supervisor')
  )
)
with check (
  private.has_branch_access(store_id, branch_id)
  and exists (
    select 1 from public.store_memberships sm
    where sm.store_id = store_tables.store_id
      and sm.user_id = (select auth.uid())
      and sm.is_active = true
      and sm.role in ('owner','admin','supervisor')
  )
);

grant select, insert, delete on public.store_tables to authenticated;
grant update (occupied_order_local_id, occupied_at, updated_at) on public.store_tables to authenticated;

drop policy if exists "store_tables_branch_occupancy_update" on public.store_tables;
create policy "store_tables_branch_occupancy_update"
on public.store_tables for update to authenticated
using (private.has_branch_access(store_id, branch_id))
with check (private.has_branch_access(store_id, branch_id));

-- Claim is a single conditional UPDATE: concurrent devices cannot claim the same table.
-- Cashiers can claim/release only within branches they already have access to.
create or replace function public.claim_store_table(
  target_store_id uuid,
  target_branch_id uuid,
  target_table_id text,
  target_order_local_id text
)
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  changed integer;
begin
  if auth.uid() is null
     or target_table_id is null or btrim(target_table_id) = ''
     or target_order_local_id is null or btrim(target_order_local_id) = '' then
    raise exception 'Invalid table claim request';
  end if;

  if not private.has_branch_access(target_store_id, target_branch_id) then
    raise exception 'Branch access denied';
  end if;

  update public.store_tables
     set occupied_order_local_id = target_order_local_id,
         occupied_at = now(),
         updated_at = now()
   where store_id = target_store_id
     and branch_id = target_branch_id
     and id = target_table_id
     and is_active = true
     and occupied_order_local_id is null;

  get diagnostics changed = row_count;
  return changed = 1;
end;
$$;

create or replace function public.release_store_table(
  target_store_id uuid,
  target_branch_id uuid,
  target_table_id text,
  target_order_local_id text
)
returns boolean
language plpgsql
security invoker
set search_path = ''
as $$
declare
  changed integer;
begin
  if auth.uid() is null
     or target_table_id is null or btrim(target_table_id) = ''
     or target_order_local_id is null or btrim(target_order_local_id) = '' then
    raise exception 'Invalid table release request';
  end if;

  if not private.has_branch_access(target_store_id, target_branch_id) then
    raise exception 'Branch access denied';
  end if;

  update public.store_tables
     set occupied_order_local_id = null,
         occupied_at = null,
         updated_at = now()
   where store_id = target_store_id
     and branch_id = target_branch_id
     and id = target_table_id
     and occupied_order_local_id = target_order_local_id;

  get diagnostics changed = row_count;
  return changed = 1;
end;
$$;

revoke all on function public.claim_store_table(uuid, uuid, text, text) from public, anon;
revoke all on function public.release_store_table(uuid, uuid, text, text) from public, anon;
grant execute on function public.claim_store_table(uuid, uuid, text, text) to authenticated;
grant execute on function public.release_store_table(uuid, uuid, text, text) to authenticated;

comment on table public.store_tables is
  'Shared dine-in table master and occupancy state, scoped by store and branch.';
