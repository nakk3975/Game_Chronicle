-- First set TRACKING_ENABLED=true in Render and wait for it to be live, then run:
begin;
select cron.alter_job(jobid,active:=false) from cron.job where jobname in ('gc-edge-poll','gc-edge-library');
select id from chronicle.collector_control where id for update;
select id from chronicle.app_user order by id for update;
update chronicle.collector_control set engine='render',poll_token=poll_token+1,sync_token=sync_token+1,poll_lease_until=null,sync_lease_until=null where id;
update chronicle.tracking_state set fencing_token=fencing_token+1,lease_until=null,next_poll_at=now();
commit;
