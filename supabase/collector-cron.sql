-- Register paused jobs; activate only after the Edge health check passes and Render runs the cutover-aware build.
select cron.schedule('gc-edge-poll','* * * * *', $job$
 select net.http_post(
  url:='https://mpfvubjdrerkqvgevgrw.supabase.co/functions/v1/game-chronicle-collector?task=poll',
  headers:=jsonb_build_object('Content-Type','application/json','Authorization','Bearer '||(select decrypted_secret from vault.decrypted_secrets where name='gc_collector_token')),
  body:='{}'::jsonb,timeout_milliseconds:=110000);
$job$);
select cron.schedule('gc-edge-library','* * * * *', $job$
 select net.http_post(
  url:='https://mpfvubjdrerkqvgevgrw.supabase.co/functions/v1/game-chronicle-collector?task=sync',
  headers:=jsonb_build_object('Content-Type','application/json','Authorization','Bearer '||(select decrypted_secret from vault.decrypted_secrets where name='gc_collector_token')),
  body:='{}'::jsonb,timeout_milliseconds:=110000);
$job$);
select cron.alter_job(jobid,active:=false) from cron.job where jobname in ('gc-edge-poll','gc-edge-library');
select cron.schedule('gc-maintenance','17 * * * *', $job$
 delete from chronicle.openid_nonce where expires_at<now();
 delete from chronicle.api_budget where bucket<now()-interval '26 hours';
 delete from chronicle.spring_session where expiry_time<(extract(epoch from now())*1000)::bigint;
 delete from cron.job_run_details where end_time<now()-interval '3 days' and jobid in (select jobid from cron.job where jobname in ('gc-edge-poll','gc-edge-library','gc-maintenance'));
$job$);
