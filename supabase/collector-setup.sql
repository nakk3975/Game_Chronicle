-- Apply once to an existing Game Chronicle database. No credentials in source.
create extension if not exists pg_cron;
create extension if not exists pg_net with schema extensions;
create table chronicle.collector_control (
 id boolean primary key default true check(id),
 engine text not null default 'render' check(engine in ('render','edge')),
 last_heartbeat timestamptz,
 last_sync_heartbeat timestamptz,
 last_error text,
 poll_lease_until timestamptz,
 sync_lease_until timestamptz,
 poll_token bigint not null default 0,
 sync_token bigint not null default 0,
 next_attempt_at timestamptz not null default now(),
 failures integer not null default 0
);
insert into chronicle.collector_control(id) values(true);
alter table chronicle.collector_control enable row level security;
create policy backend_only on chronicle.collector_control to app_runtime using(true) with check(true);
grant select,update on chronicle.collector_control to app_runtime;
revoke all on chronicle.collector_control from public,anon,authenticated;
-- Edge uses its platform-provided DB connection, then drops to app_runtime in every business transaction.
grant app_runtime to postgres;
-- A random, dedicated invocation credential stays in Vault and is never returned.
do $$ begin
 if not exists(select 1 from vault.secrets where name='gc_collector_token') then
  perform vault.create_secret(encode(extensions.gen_random_bytes(32),'hex'),'gc_collector_token');
 end if;
end $$;
