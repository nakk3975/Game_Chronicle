package com.gamechronicle;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import java.time.*;
import java.util.*;
import org.slf4j.*;
@Component
public class Collector {
 private static final Logger log=LoggerFactory.getLogger(Collector.class);
 private final Store db;private final SteamClient steam;private final TrackingService service;private final boolean enabled;
 private Instant next=Instant.EPOCH;private int failures=0;
 public Collector(Store db,SteamClient steam,TrackingService service,@Value("${app.tracking-enabled}")boolean enabled){this.db=db;this.steam=steam;this.service=service;this.enabled=enabled;}
 @Scheduled(fixedDelay=10000) public void poll(){
  if(!enabled||!steam.configured()||Instant.now().isBefore(next))return;
  var jobs=db.claim();if(jobs.isEmpty())return;Instant at=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  Map<String,com.fasterxml.jackson.databind.JsonNode> players=new HashMap<>();boolean failed=false;int delay=60;
  try {
   String ids=jobs.stream().map(j->j.get("steam_id").toString()).collect(java.util.stream.Collectors.joining(","));
   var result=steam.get("ISteamUser/GetPlayerSummaries/v2/","steamids="+ids).path("response").path("players");
   if(!result.isArray())throw new IllegalStateException();
   result.forEach(p->players.put(p.path("steamid").asText(),p));failures=0;
  }catch(Exception e){
   failed=true;failures++;delay=Math.min(900,60*(1<<Math.min(failures-1,4)))+new Random().nextInt(10);
   if(e instanceof SteamClient.UpstreamFailure f)delay=f.status==403?900:Math.max(delay,f.retrySeconds);
   next=Instant.now().plusSeconds(delay);log.warn("Steam collection unavailable; next attempt in {} seconds",delay);
  }
  for(var j:jobs)try{
   UUID id=(UUID)j.get("user_id");long token=((Number)j.get("fencing_token")).longValue();
   var o=failed?new TrackingEngine.Observation(at,TrackingEngine.Kind.FETCH_FAILED,null,null):SteamClient.classify(players.get(j.get("steam_id").toString()),at);
   service.apply(id,token,o,delay);
  }catch(Exception e){log.warn("Collection write failed; lease will expire");}
 }
 @Scheduled(fixedDelay=60000) public void sync(){
  if(!enabled||!steam.configured()||Instant.now().isBefore(next))return;
  for(var u:db.syncDue())try{
   service.library((UUID)u.get("id"),((Number)u.get("version")).longValue(),steam.get("IPlayerService/GetOwnedGames/v1/","steamid="+u.get("steam_id")+"&include_appinfo=true&include_played_free_games=true"));
  }catch(Exception e){service.libraryFailed((UUID)u.get("id"),((Number)u.get("version")).longValue());log.warn("Steam library synchronization unavailable");}
 }
 @Scheduled(fixedDelay=3600000) public void clean(){db.cleanNonces();db.cleanBudget();}
}
