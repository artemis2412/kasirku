-- KASIRKU: secure first-store bootstrap.
-- Run after the core schema and access policies.
-- This RPC is intentionally SECURITY DEFINER: it creates the first store and owner
-- membership atomically, while deriving the user exclusively from auth.uid().

create or replace function public.create_my_store(
  p_store_name text,
  p_branch_name text default 'Cabang Utama',
  p_display_name text default ''
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user uuid := (select auth.uid());
  v_store uuid;
  v_branch uuid;
begin
  if v_user is null then
    raise exception 'Harus login terlebih dahulu';
  end if;

  if length(trim(coalesce(p_store_name,''))) < 2 or length(trim(p_store_name)) > 100 then
    raise exception 'Nama toko harus 2 sampai 100 karakter';
  end if;

  if length(trim(coalesce(p_branch_name,''))) < 2 or length(trim(p_branch_name)) > 100 then
    raise exception 'Nama cabang harus 2 sampai 100 karakter';
  end if;

  if exists (
    select 1 from public.store_memberships sm
    where sm.user_id = v_user and sm.is_active = true
  ) then
    raise exception 'Akun ini sudah terhubung dengan toko';
  end if;

  insert into public.profiles(id, display_name)
  values (v_user, coalesce(nullif(trim(p_display_name),''), 'Owner'))
  on conflict (id) do update
    set display_name = excluded.display_name, updated_at = now();

  insert into public.stores(name, owner_user_id)
  values (trim(p_store_name), v_user)
  returning id into v_store;

  insert into public.branches(store_id, name)
  values (v_store, trim(p_branch_name))
  returning id into v_branch;

  insert into public.store_memberships(store_id, user_id, role, is_active)
  values (v_store, v_user, 'owner', true);

  insert into public.branch_memberships(store_id, branch_id, user_id)
  values (v_store, v_branch, v_user);

  return jsonb_build_object(
    'store_id', v_store,
    'branch_id', v_branch,
    'role', 'owner'
  );
end;
$$;

revoke all on function public.create_my_store(text, text, text) from public, anon;
grant execute on function public.create_my_store(text, text, text) to authenticated;
