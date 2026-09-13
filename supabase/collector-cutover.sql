-- Run only after authenticated health returns steamConfigured:true and the Render cutover-aware build is live.
begin;
select id from chronicle.collector_control where id for update;
-- Finish any existing writer before changing ownership; subsequent Render writers check engine.
select id from chronicle.app_user order by id for update;
update chronicle.collector_control set engine='edge',poll_lease_until=null,sync_lease_until=null,poll_token=poll_token+1,sync_token=sync_token+1,next_attempt_at=now(),failures=0,last_error=null where id;
update chronicle.tracking_state set fencing_token=fencing_token+1,lease_until=null,next_poll_at=now();
select cron.alter_job(jobid,active:=true) from cron.job where jobname in ('gc-edge-poll','gc-edge-library');
commit;
-- After successful periodic observations, set TRACKING_ENABLED=false in Render.
