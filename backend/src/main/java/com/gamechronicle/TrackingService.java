package com.gamechronicle;
import com.fasterxml.jackson.databind.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class TrackingService {
 private final Store db;private final ObjectMapper json;
 public TrackingService(Store db,ObjectMapper json){this.db=db;this.json=json;}
 public TrackingEngine.State read(UUID id){try{return json.readValue(db.state(id),TrackingEngine.State.class);}catch(Exception e){throw new IllegalStateException("Invalid persisted tracking state",e);}}
 private String encode(Object o){try{return json.writeValueAsString(o);}catch(Exception e){throw new IllegalStateException(e);}}
 void persist(UUID id,TrackingEngine.Result r){
  for(var p:r.changed()){
   db.savePlay(id,p,p.seconds(),encode(p));db.clearSegments(p.id);
   for(int i=0;i<p.segments.size();i++){var g=p.segments.get(i);db.segment(p.id,i,g.start,g.end);}
  }
  for(var g:r.gaps())db.gap(id,g);
  db.stateSave(id,encode(r.state()));
 }
 @Transactional public void apply(UUID id,long token,TrackingEngine.Observation o,int delay){
  var u=db.lockUser(id);if(u==null||!Boolean.TRUE.equals(u.get("tracking_enabled"))||!"ACTIVE".equals(u.get("status"))||db.validLease(id,token)==0)return;
  persist(id,TrackingEngine.apply(read(id),o));db.release(id,token,delay);
 }
 @Transactional public void tracking(UUID id,boolean enabled){
  var user=db.lockUser(id);if(user==null)throw new IllegalArgumentException();
  if(Boolean.valueOf(enabled).equals(user.get("tracking_enabled")))return;
  db.invalidateLease(id);
  if(!enabled)persist(id,TrackingEngine.pause(read(id),Instant.now()));
  else {var s=read(id);s.status="IDLE";s.lastSuccess=null;s.lastAttempt=null;s.failures=0;db.stateSave(id,encode(s));}
  db.tracking(id,enabled);db.consent(id,enabled);
 }
 @Transactional public void library(UUID id,long expectedVersion,JsonNode response){
  var u=db.lockUser(id);if(u==null||!Boolean.TRUE.equals(u.get("tracking_enabled"))||((Number)u.get("version")).longValue()!=expectedVersion)return;
  JsonNode games=response.path("response").path("games");
  if(!games.isArray()){db.syncDone(id,"UNAVAILABLE");return;}
  for(var g:games){
   if(!g.path("appid").isIntegralNumber()||!g.path("playtime_forever").isIntegralNumber()||!g.path("appid").canConvertToLong()||!g.path("playtime_forever").canConvertToLong())continue;
   long app=g.path("appid").asLong(),min=g.path("playtime_forever").asLong();if(app<=0||app>4294967295L||min<0)continue;
   String game=Long.toString(app);Long previous=db.minutes(id,game);
   db.game(id,game,g.path("name").asText("Steam 게임 "+game),min);
   if(previous==null||previous!=min)db.checkpoint(id,game,min);
  }
  db.syncDone(id,"AVAILABLE");
 }
 @Transactional public void delete(UUID id){db.lockUser(id);db.deleteUser(id);}
 @Transactional public void libraryFailed(UUID id,long expectedVersion){
  var u=db.lockUser(id);
  if(u!=null&&Boolean.TRUE.equals(u.get("tracking_enabled"))&&((Number)u.get("version")).longValue()==expectedVersion)db.syncDone(id,"FETCH_FAILED");
 }
}
