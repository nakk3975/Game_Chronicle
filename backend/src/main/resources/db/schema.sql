create schema if not exists chronicle;
revoke all on schema chronicle from public, anon, authenticated;
do $$ begin
 if not exists(select 1 from pg_roles where rolname='app_runtime') then create role app_runtime nologin; end if;
end $$;
create table chronicle.app_user (
 id uuid primary key default gen_random_uuid(), steam_id varchar(20) not null unique check(steam_id ~ '^[0-9]{17,20}$'),
 display_name varchar(100) not null default '플레이어', timezone varchar(64) not null default 'Asia/Seoul',
 tracking_enabled boolean not null default false, consent_version varchar(30), consented_at timestamptz,
 status varchar(16) not null default 'ACTIVE' check(status in ('ACTIVE','DELETING')),
 version bigint not null default 0, created_at timestamptz not null default now(),
 last_sync_at timestamptz, sync_requested boolean not null default false,
 library_status varchar(30) not null default 'UNKNOWN', diagnostics_at timestamptz
);
create table chronicle.tracking_state (
 user_id uuid primary key references chronicle.app_user on delete cascade,
 payload jsonb not null default '{"status":"IDLE"}',
 next_poll_at timestamptz not null default now(), lease_until timestamptz,
 fencing_token bigint not null default 0
);
create table chronicle.play_session (
 id uuid primary key, user_id uuid not null references chronicle.app_user on delete cascade,
 game_id varchar(20) not null, name varchar(300) not null,
 started_at timestamptz not null, ended_at timestamptz,
 lifecycle varchar(20) not null check(lifecycle in ('ACTIVE','PENDING_END','FINALIZED','INTERRUPTED')),
 quality varchar(20) not null, tracked_seconds bigint not null check(tracked_seconds>=0),
 excluded boolean not null default false, version bigint not null default 0,
 payload jsonb not null, check(ended_at is null or ended_at>=started_at)
);
create unique index uq_active_session on chronicle.play_session(user_id) where lifecycle in ('ACTIVE','PENDING_END');
create index ix_session_owner_date on chronicle.play_session(user_id,started_at desc,id);
create table chronicle.play_segment (
 session_id uuid not null references chronicle.play_session on delete cascade,
 segment_index integer not null, start_at timestamptz not null, end_at timestamptz not null,
 primary key(session_id,segment_index), check(end_at>=start_at)
);
create index ix_segment_range on chronicle.play_segment(start_at,end_at);
create table chronicle.tracking_gap (
 id uuid primary key default gen_random_uuid(), user_id uuid not null references chronicle.app_user on delete cascade,
 start_at timestamptz not null,end_at timestamptz not null,reason varchar(40) not null,check(end_at>=start_at)
);
create index ix_gap_owner_date on chronicle.tracking_gap(user_id,start_at);
create table chronicle.user_game (
 user_id uuid not null references chronicle.app_user on delete cascade,game_id varchar(20) not null,
 name varchar(300) not null,baseline_minutes bigint not null check(baseline_minutes>=0),
 reported_minutes bigint not null check(reported_minutes>=0),synced_at timestamptz not null default now(),
 primary key(user_id,game_id)
);
create table chronicle.playtime_checkpoint (
 id uuid primary key default gen_random_uuid(),user_id uuid not null references chronicle.app_user on delete cascade,
 game_id varchar(20) not null,reported_minutes bigint not null check(reported_minutes>=0),observed_at timestamptz not null default now()
);
create index ix_checkpoint_owner_game on chronicle.playtime_checkpoint(user_id,game_id,observed_at desc);
create table chronicle.consent_event (
 id uuid primary key default gen_random_uuid(),user_id uuid not null references chronicle.app_user on delete cascade,
 enabled boolean not null,policy_version varchar(30) not null,occurred_at timestamptz not null default now()
);
create table chronicle.openid_nonce (nonce varchar(255) primary key,expires_at timestamptz not null);
create table chronicle.api_budget (bucket timestamptz primary key,calls integer not null check(calls>=0));
create table chronicle.spring_session (
 primary_id char(36) not null primary key,session_id char(36) not null,
 creation_time bigint not null,last_access_time bigint not null,max_inactive_interval integer not null,
 expiry_time bigint not null,principal_name varchar(100)
);
create unique index spring_session_ix1 on chronicle.spring_session(session_id);
create index spring_session_ix2 on chronicle.spring_session(expiry_time);
create index spring_session_ix3 on chronicle.spring_session(principal_name);
create table chronicle.spring_session_attributes (
 session_primary_id char(36) not null references chronicle.spring_session(primary_id) on delete cascade,
 attribute_name varchar(200) not null,attribute_bytes bytea not null,
 primary key(session_primary_id,attribute_name)
);
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
grant usage on schema chronicle to app_runtime;
grant select,insert,update,delete on all tables in schema chronicle to app_runtime;
do $$ declare t record; begin
 for t in select tablename from pg_tables where schemaname='chronicle' loop
  execute format('alter table chronicle.%I enable row level security',t.tablename);
  execute format('create policy backend_only on chronicle.%I to app_runtime using (true) with check (true)',t.tablename);
 end loop;
end $$;
revoke all on all tables in schema chronicle from public,anon,authenticated;
create index ix_consent_owner on chronicle.consent_event(user_id);
create index ix_tracking_due on chronicle.tracking_state(next_poll_at);
