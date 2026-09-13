import postgres from 'postgres';
import { apply, classify, seconds } from './engine.ts';
import type { Observation } from './engine.ts';
const sql = postgres(Deno.env.get('SUPABASE_DB_URL')!, {prepare:false,max:3,idle_timeout:10,connect_timeout:10});
const reply=(status:number,body:unknown)=>Response.json(body,{status,headers:{'Cache-Control':'no-store'}});
const at=()=>new Date(Math.floor(Date.now()/1000)*1000).toISOString();
async function tx<T>(fn:(db:any)=>Promise<T>):Promise<T>{
 return await sql.begin(async db=>{await db`set local role app_runtime`;await db`set local statement_timeout='20s'`;await db`set local lock_timeout='5s'`;return await fn(db);}) as T;
}
class UpstreamError extends Error {delay:number;constructor(delay=60){super('STEAM_UNAVAILABLE');this.delay=delay;}}
async function steam(path:string,params:Record<string,string>){
 const key=Deno.env.get('STEAM_API_KEY');if(!key)throw new Error('STEAM_KEY_MISSING');
 await tx(async db=>{
  await db`select pg_advisory_xact_lock(71420931)`;
  const [b]=await db`select coalesce(sum(calls),0)::int as calls from chronicle.api_budget where bucket>now()-interval '25 hours'`;
  if(b.calls>=90000)throw new UpstreamError(3600);
  await db`insert into chronicle.api_budget values(date_trunc('hour',now()),1) on conflict(bucket) do update set calls=chronicle.api_budget.calls+1`;
 });
 const url=new URL('https://api.steampowered.com/'+path);url.search=new URLSearchParams({...params,key}).toString();
 const r=await fetch(url,{signal:AbortSignal.timeout(10000),redirect:'error'});
 if(!r.ok){const raw=r.headers.get('Retry-After')??'60';const parsed=Number(raw);const delay=Number.isFinite(parsed)?parsed:Math.ceil((Date.parse(raw)-Date.now())/1000);throw new UpstreamError(r.status===403?900:Math.max(60,Math.min(86400,Number.isFinite(delay)?delay:60)));}
 return await r.json();
}
async function acquire(task:'poll'|'sync'){
 const lease=task==='poll'?'poll_lease_until':'sync_lease_until';const token=task==='poll'?'poll_token':'sync_token';
 return await tx(async db=>{
  const rows=await db`update chronicle.collector_control set ${db(lease)}=now()+interval '120 seconds',${db(token)}=${db(token)}+1 where id and engine='edge' and next_attempt_at<=now() and (${db(lease)} is null or ${db(lease)}<now()) returning ${db(token)} as token`;
  return rows.length?String(rows[0].token):null;
 });
}
async function persist(job:any,globalToken:string,o:Observation,delay:number){
 return await tx(async db=>{
  const control=await db`select id from chronicle.collector_control where id and engine='edge' and poll_token=${globalToken}::bigint and poll_lease_until>now() for share`;
  if(!control.length)return false;
  const [u]=await db`select tracking_enabled,status from chronicle.app_user where id=${job.user_id}::uuid for update`;
  if(!u?.tracking_enabled||u.status!=='ACTIVE')return false;
  const [row]=await db`select payload from chronicle.tracking_state where user_id=${job.user_id}::uuid and fencing_token=${job.fencing_token}::bigint and lease_until>now() for update`;
  if(!row)return false;
  const result=apply(row.payload,o);
  for(const p of result.changed){
   await db`insert into chronicle.play_session(id,user_id,game_id,name,started_at,ended_at,lifecycle,quality,tracked_seconds,payload)
    values(${p.id}::uuid,${job.user_id}::uuid,${p.gameId},${p.name},${p.startedAt}::timestamptz,${p.endedAt}::timestamptz,${p.lifecycle},${p.quality},${seconds(p)},${db.json(p)}::jsonb)
    on conflict(id) do update set ended_at=excluded.ended_at,lifecycle=excluded.lifecycle,quality=excluded.quality,tracked_seconds=excluded.tracked_seconds,payload=excluded.payload,version=chronicle.play_session.version+1`;
   await db`delete from chronicle.play_segment where session_id=${p.id}::uuid`;
   await db`insert into chronicle.play_segment(session_id,segment_index,start_at,end_at)
    select ${p.id}::uuid,(ordinality-1)::int,(value->>'start')::timestamptz,(value->>'end')::timestamptz from jsonb_array_elements(${db.json(p.segments)}::jsonb) with ordinality`;
  }
  for(const gap of result.gaps)await db`insert into chronicle.tracking_gap(user_id,start_at,end_at,reason) values(${job.user_id}::uuid,${gap.start}::timestamptz,${gap.end}::timestamptz,${gap.reason})`;
  await db`update chronicle.tracking_state set payload=${db.json(result.state)}::jsonb,lease_until=null,next_poll_at=case when ${delay}::int<=60 then date_trunc('minute',now())+interval '1 minute' else now()+${delay}*interval '1 second' end where user_id=${job.user_id}::uuid and fencing_token=${job.fencing_token}::bigint`;
  return true;
 });
}
async function poll(token:string){
 const jobs:any[]=await tx(async db=>await db`with due as (
  select t.user_id from chronicle.tracking_state t join chronicle.app_user u on u.id=t.user_id
  where u.tracking_enabled and u.status='ACTIVE' and t.next_poll_at<=now() and (t.lease_until is null or t.lease_until<now())
  order by t.next_poll_at limit 100 for update of t skip locked
 ) update chronicle.tracking_state t set lease_until=now()+interval '120 seconds',fencing_token=t.fencing_token+1,next_poll_at=now()+interval '60 seconds'
 from due where t.user_id=due.user_id returning t.user_id,t.fencing_token,(select steam_id from chronicle.app_user where id=t.user_id) steam_id`);
 let failed=false,delay=60;const stamp=at();const players=new Map<string,any>();
 if(jobs.length)try{
  const response=await steam('ISteamUser/GetPlayerSummaries/v2/',{steamids:jobs.map(j=>j.steam_id).join(',')});
  if(!Array.isArray(response?.response?.players))throw new UpstreamError();
  response.response.players.forEach((p:any)=>players.set(String(p.steamid),p));
 }catch(e){failed=true;delay=e instanceof UpstreamError?e.delay:60;}
 if(failed)delay=await tx(async db=>{
  const [c]=await db`update chronicle.collector_control set failures=failures+1,last_error='STEAM_UNAVAILABLE',next_attempt_at=now()+greatest(${delay}::int,least(900,60*power(2,least(failures,4)))::int)*interval '1 second' where id and poll_token=${token}::bigint returning greatest(${delay}::int,least(900,60*power(2,least(failures-1,4)))::int) as delay`;return c?.delay??delay;
 });
 const deadline=Date.now()+65000;let written=0;
 for(const job of jobs){if(Date.now()>deadline)break;try{if(await persist(job,token,failed?{at:stamp,kind:'FETCH_FAILED'}:classify(players.get(job.steam_id),stamp),delay))written++;}catch{console.warn('COLLECTION_WRITE_FAILED');}}
 await tx(async db=>{await db`update chronicle.collector_control set last_heartbeat=now(),last_error=case when ${failed} then 'STEAM_UNAVAILABLE' when ${written}<>${jobs.length} then 'COLLECTION_WRITE_FAILED' else null end,failures=case when ${failed} then failures else 0 end where id and poll_token=${token}::bigint`;});
 return {ok:!failed&&written===jobs.length,observed:written};
}
async function sync(token:string){
 const jobs:any[]=await tx(async db=>await db`select id,steam_id,version from chronicle.app_user where tracking_enabled and status='ACTIVE' and (sync_requested or last_sync_at is null or last_sync_at<now()-interval '24 hours') order by last_sync_at nulls first limit 1`);
 if(!jobs.length)return {ok:true,synced:0};const u=jobs[0];let status='AVAILABLE';let games:any[]=[];
 try{
  const r=await steam('IPlayerService/GetOwnedGames/v1/',{steamid:u.steam_id,include_appinfo:'true',include_played_free_games:'true'});
  if(!Array.isArray(r?.response?.games))status=r?.response?.game_count===0?'AVAILABLE':'UNAVAILABLE';
  else games=r.response.games.filter((g:any)=>Number.isSafeInteger(g.appid)&&g.appid>0&&g.appid<=4294967295&&Number.isSafeInteger(g.playtime_forever)&&g.playtime_forever>=0).map((g:any)=>({game_id:String(g.appid),name:(typeof g.name==='string'?g.name:`Steam 게임 ${g.appid}`).slice(0,300),minutes:g.playtime_forever}));
 }catch{status='FETCH_FAILED';}
 const saved=await tx(async db=>{
  const c=await db`select id from chronicle.collector_control where id and engine='edge' and sync_token=${token}::bigint and sync_lease_until>now() for share`;if(!c.length)return false;
  const [user]=await db`select tracking_enabled,status,version from chronicle.app_user where id=${u.id}::uuid for update`;
  if(!user?.tracking_enabled||user.status!=='ACTIVE'||String(user.version)!==String(u.version))return false;
  if(games.length){const data=db.json(games);
   await db`insert into chronicle.playtime_checkpoint(user_id,game_id,reported_minutes)
    select ${u.id}::uuid,x.game_id,x.minutes from jsonb_to_recordset(${data}::jsonb) x(game_id text,name text,minutes bigint)
    left join chronicle.user_game g on g.user_id=${u.id}::uuid and g.game_id=x.game_id where g.game_id is null or g.reported_minutes<>x.minutes`;
   await db`insert into chronicle.user_game(user_id,game_id,name,baseline_minutes,reported_minutes)
    select ${u.id}::uuid,x.game_id,x.name,x.minutes,x.minutes from jsonb_to_recordset(${data}::jsonb) x(game_id text,name text,minutes bigint)
    on conflict(user_id,game_id) do update set name=excluded.name,reported_minutes=excluded.reported_minutes,synced_at=now()`;
  }
  await db`update chronicle.app_user set library_status=${status},last_sync_at=now(),sync_requested=false where id=${u.id}::uuid`;
  await db`update chronicle.collector_control set last_sync_heartbeat=now() where id and sync_token=${token}::bigint`;return true;
 });
 return {ok:status!=='FETCH_FAILED',synced:saved?1:0};
}
Deno.serve(async(req:Request)=>{
 if(req.method!=='POST')return reply(405,{error:'METHOD_NOT_ALLOWED'});
 const bearer=req.headers.get('authorization')??'';
 if(!/^Bearer [a-f0-9]{64}$/.test(bearer))return reply(401,{error:'UNAUTHORIZED'});
 let task:'poll'|'sync'='poll';let token:string|null=null;
 try{
  // Authentication uses a dedicated Vault secret, never a user JWT or a public API key.
  const [auth]=await sql`select exists(select 1 from vault.decrypted_secrets where name='gc_collector_token' and decrypted_secret=${bearer.slice(7)}) as valid`;
  if(!auth?.valid)return reply(401,{error:'UNAUTHORIZED'});
  const requested=new URL(req.url).searchParams.get('task')??'poll';
  if(requested==='health'){
   const [state]=await tx(async db=>await db`select engine from chronicle.collector_control where id`);
   return reply(200,{ok:true,engine:state.engine,steamConfigured:!!Deno.env.get('STEAM_API_KEY')});
  }
  if(requested==='probe'){
   if(!Deno.env.get('STEAM_API_KEY'))return reply(503,{error:'STEAM_KEY_MISSING'});
   const users:any[]=await tx(async db=>await db`select steam_id from chronicle.app_user where tracking_enabled and status='ACTIVE' limit 1`);
   if(!users.length)return reply(409,{error:'NO_CONSENTED_USER'});
   const result=await steam('ISteamUser/GetPlayerSummaries/v2/',{steamids:users[0].steam_id});
   return reply(Array.isArray(result?.response?.players)?200:502,{ok:Array.isArray(result?.response?.players)});
  }
  if(requested!=='poll'&&requested!=='sync')return reply(400,{error:'INVALID_TASK'});task=requested;
  if(!Deno.env.get('STEAM_API_KEY'))return reply(503,{error:'STEAM_KEY_MISSING'});
  token=await acquire(task);if(token===null)return reply(200,{ok:true,skipped:true});
  return reply(200,task==='poll'?await poll(token):await sync(token));
 }catch{console.error('COLLECTOR_FAILED');return reply(500,{error:'COLLECTOR_FAILED'});}
 finally{
  if(token!==null)try{const lease=task==='poll'?'poll_lease_until':'sync_lease_until';const field=task==='poll'?'poll_token':'sync_token';await tx(async db=>{await db`update chronicle.collector_control set ${db(lease)}=null where id and ${db(field)}=${token}::bigint`;});}catch{console.warn('LEASE_RELEASE_FAILED');}
 }
});
