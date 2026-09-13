package com.gamechronicle;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
@Component
public class SteamClient {
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final ObjectMapper json;private final Store store;private final String key;
 public SteamClient(ObjectMapper json,Store store,@Value("${app.steam-key}")String key){this.json=json;this.store=store;this.key=key;}
 public boolean configured(){return !key.isBlank();}
 public JsonNode get(String path,String query)throws Exception {
  if(!configured()||store.calls()>=90000)throw new IllegalStateException("STEAM_UNAVAILABLE");
  store.call();
  HttpRequest r=HttpRequest.newBuilder(URI.create("https://api.steampowered.com/"+path+"?key="+URLEncoder.encode(key,java.nio.charset.StandardCharsets.UTF_8)+"&"+query)).timeout(Duration.ofSeconds(10)).GET().build();
  var response=http.send(r,HttpResponse.BodyHandlers.ofString());
  if(response.statusCode()!=200)throw new UpstreamFailure(response.statusCode(),response.headers().firstValue("Retry-After").orElse("60"));
  return json.readTree(response.body());
 }
 public String verifyOpenId(String body)throws Exception {
  var req=HttpRequest.newBuilder(URI.create("https://steamcommunity.com/openid/login")).timeout(Duration.ofSeconds(10))
   .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
  var r=http.send(req,HttpResponse.BodyHandlers.ofString());if(r.statusCode()!=200)throw new IllegalStateException();return r.body();
 }
 public static class UpstreamFailure extends RuntimeException {
  public final int status;public final int retrySeconds;
  UpstreamFailure(int s,String retry){status=s;int n=60;try{n=Integer.parseInt(retry);}catch(Exception ignored){try{n=(int)Duration.between(Instant.now(),java.time.ZonedDateTime.parse(retry,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds();}catch(Exception invalid){}}retrySeconds=Math.max(60,Math.min(86400,n));}
 }
 public static TrackingEngine.Observation classify(JsonNode p,Instant now) {
  if(p==null||!p.isObject()||p.path("communityvisibilitystate").asInt()!=3)
   return new TrackingEngine.Observation(now,TrackingEngine.Kind.UNOBSERVABLE,null,null);
  if(p.has("gameid")) {
   String g=p.path("gameid").asText();
   if(!g.matches("[1-9][0-9]{0,9}")||Long.parseLong(g)>4294967295L)
    return new TrackingEngine.Observation(now,TrackingEngine.Kind.UNOBSERVABLE,null,null);
   return new TrackingEngine.Observation(now,TrackingEngine.Kind.PLAYING,g,p.path("gameextrainfo").asText("Steam 게임 "+g));
  }
  // Public profile is only evidence, never proof that the user actually stopped playing.
  return new TrackingEngine.Observation(now,TrackingEngine.Kind.NO_GAME_CANDIDATE,null,null);
 }
}
