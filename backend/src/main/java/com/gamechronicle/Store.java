package com.gamechronicle;
import org.apache.ibatis.annotations.*;
import java.util.*;
import java.time.*;
@Mapper
public interface Store {
 @Select("select engine from chronicle.collector_control where id") String collectorEngine();
 @Select("select coalesce(engine='edge' and last_heartbeat>now()-interval '5 minutes',false) from chronicle.collector_control where id") boolean edgeCollectorHealthy();
 @Select("select * from chronicle.app_user where id=#{id}") Map<String,Object> user(UUID id);
 @Select("select * from chronicle.app_user where id=#{id} for update") Map<String,Object> lockUser(UUID id);
 @Select(affectData=true,value="insert into chronicle.app_user(steam_id) values(#{steamId}) on conflict(steam_id) do update set steam_id=excluded.steam_id returning id")
 UUID login(String steamId);
 @Insert("insert into chronicle.tracking_state(user_id) values(#{id}) on conflict do nothing") void ensureState(UUID id);
 @Select("select payload::text from chronicle.tracking_state where user_id=#{id}") String state(UUID id);
 @Update("update chronicle.tracking_state set payload=cast(#{payload} as jsonb) where user_id=#{id}") void stateSave(UUID id,String payload);
 @Select(affectData=true,value="with due as (select t.user_id from chronicle.tracking_state t join chronicle.app_user u on u.id=t.user_id where u.tracking_enabled and u.status='ACTIVE' and t.next_poll_at<=now() and (t.lease_until is null or t.lease_until<now()) order by t.next_poll_at limit 100 for update of t skip locked) update chronicle.tracking_state t set lease_until=now()+interval '120 seconds',fencing_token=t.fencing_token+1,next_poll_at=now()+interval '60 seconds' from due where t.user_id=due.user_id returning t.user_id,t.fencing_token,(select steam_id from chronicle.app_user where id=t.user_id) steam_id")
 List<Map<String,Object>> claim();
 @Select("select count(*) from chronicle.tracking_state where user_id=#{id} and fencing_token=#{token} and lease_until>now()") int validLease(UUID id,long token);
 @Update("update chronicle.tracking_state set lease_until=null,next_poll_at=now()+(#{delay} * interval '1 second') where user_id=#{id} and fencing_token=#{token}") void release(UUID id,long token,int delay);
 @Update("update chronicle.tracking_state set fencing_token=fencing_token+1,lease_until=null,next_poll_at=now() where user_id=#{id}") void invalidateLease(UUID id);
 @Update("update chronicle.app_user set tracking_enabled=#{enabled},consent_version='1.0',consented_at=now(),version=version+1,sync_requested=#{enabled} where id=#{id}") void tracking(UUID id,boolean enabled);
 @Insert("insert into chronicle.consent_event(user_id,enabled,policy_version) values(#{id},#{enabled},'1.0')") void consent(UUID id,boolean enabled);
 @Update("update chronicle.app_user set display_name=#{name},timezone=#{zone},version=version+1 where id=#{id} and version=#{version}") int settings(UUID id,String name,String zone,long version);
 @Insert("insert into chronicle.play_session(id,user_id,game_id,name,started_at,ended_at,lifecycle,quality,tracked_seconds,payload) values(#{p.id},#{id},#{p.gameId},#{p.name},#{p.startedAt},#{p.endedAt},#{p.lifecycle},#{p.quality},#{seconds},cast(#{payload} as jsonb)) on conflict(id) do update set ended_at=excluded.ended_at,lifecycle=excluded.lifecycle,quality=excluded.quality,tracked_seconds=excluded.tracked_seconds,payload=excluded.payload,version=chronicle.play_session.version+1") void savePlay(UUID id,TrackingEngine.Play p,long seconds,String payload);
 @Delete("delete from chronicle.play_segment where session_id=#{id}") void clearSegments(UUID id);
 @Insert("insert into chronicle.play_segment values(#{id},#{index},#{start},#{end})") void segment(UUID id,int index,Instant start,Instant end);
 @Insert("insert into chronicle.tracking_gap(user_id,start_at,end_at,reason) values(#{id},#{g.start},#{g.end},#{g.reason})") void gap(UUID id,TrackingEngine.Gap g);
 @Select("select payload::text,excluded,version from chronicle.play_session where user_id=#{id} and started_at<#{to} and coalesce(ended_at,(payload->>'lastSeen')::timestamptz)>=#{from} and (cast(#{before} as timestamptz) is null or (started_at,id)<(cast(#{before} as timestamptz),cast(#{beforeId} as uuid))) order by started_at desc,id desc limit #{limit}") List<Map<String,Object>> sessions(UUID id,Instant from,Instant to,Instant before,UUID beforeId,int limit);
 @Select("select payload::text,excluded,version from chronicle.play_session where user_id=#{user} and id=#{id}") Map<String,Object> play(UUID user,UUID id);
 @Update("update chronicle.play_session set excluded=#{excluded},version=version+1 where user_id=#{user} and id=#{id} and version=#{version}") int exclude(UUID user,UUID id,boolean excluded,long version);
 @Select("select g.game_id,g.name,g.baseline_minutes,g.reported_minutes,g.synced_at,coalesce((select sum(s.tracked_seconds) from chronicle.play_session s where s.user_id=g.user_id and s.game_id=g.game_id and not s.excluded),0) tracked_seconds from chronicle.user_game g where g.user_id=#{id} and g.name ilike ('%'||#{q}||'%') order by g.reported_minutes desc,g.game_id limit #{limit} offset #{offset}") List<Map<String,Object>> library(UUID id,String q,int limit,int offset);
 @Select("select s.game_id,s.name,p.start_at,p.end_at from chronicle.play_segment p join chronicle.play_session s on s.id=p.session_id where s.user_id=#{id} and not s.excluded and p.end_at>#{from} and p.start_at<#{to} order by p.start_at") List<Map<String,Object>> segments(UUID id,Instant from,Instant to);
 @Select("select start_at,end_at,reason from chronicle.tracking_gap where user_id=#{id} and end_at>#{from} and start_at<#{to} order by start_at limit 1000") List<Map<String,Object>> gaps(UUID id,Instant from,Instant to);
 @Update("update chronicle.app_user set sync_requested=true where id=#{id} and tracking_enabled and (last_sync_at is null or last_sync_at<now()-interval '10 minutes') and not sync_requested") int requestSync(UUID id);
 @Select("select * from chronicle.app_user where tracking_enabled and status='ACTIVE' and (sync_requested or last_sync_at is null or last_sync_at<now()-interval '24 hours') order by last_sync_at nulls first limit 5") List<Map<String,Object>> syncDue();
 @Select("select reported_minutes from chronicle.user_game where user_id=#{id} and game_id=#{game}") Long minutes(UUID id,String game);
 @Insert("insert into chronicle.user_game(user_id,game_id,name,baseline_minutes,reported_minutes) values(#{id},#{game},#{name},#{minutes},#{minutes}) on conflict(user_id,game_id) do update set name=excluded.name,reported_minutes=excluded.reported_minutes,synced_at=now()") void game(UUID id,String game,String name,long minutes);
 @Insert("insert into chronicle.playtime_checkpoint(user_id,game_id,reported_minutes) values(#{id},#{game},#{minutes})") void checkpoint(UUID id,String game,long minutes);
 @Update("update chronicle.app_user set library_status=#{status},last_sync_at=now(),sync_requested=false where id=#{id}") void syncDone(UUID id,String status);
 @Insert("insert into chronicle.openid_nonce values(#{nonce},now()+interval '10 minutes')") void nonce(String nonce);
 @Delete("delete from chronicle.openid_nonce where expires_at<now()") void cleanNonces();
 @Delete("delete from chronicle.app_user where id=#{id}") void deleteUser(UUID id);
 @Select("select coalesce(sum(calls),0) from chronicle.api_budget where bucket>now()-interval '25 hours'") int calls();
 @Insert("insert into chronicle.api_budget values(date_trunc('hour',now()),1) on conflict(bucket) do update set calls=chronicle.api_budget.calls+1") void call();
 @Delete("delete from chronicle.api_budget where bucket<now()-interval '26 hours'") void cleanBudget();
}
