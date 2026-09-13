package com.gamechronicle;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.io.*;
@RestController @RequestMapping("/api/v1")
public class ApiController {
 private final Store db;private final TrackingService service;private final ObjectMapper json;private final SteamClient steam;private final boolean collectorEnabled;
 public ApiController(Store db,TrackingService service,ObjectMapper json,SteamClient steam,@Value("${app.tracking-enabled}")boolean enabled){this.db=db;this.service=service;this.json=json;this.steam=steam;this.collectorEnabled=enabled;}
 private boolean collectorAvailable(){return "edge".equals(db.collectorEngine())?db.edgeCollectorHealthy():collectorEnabled&&steam.configured();}
 static UUID uid(HttpSession s){return UUID.fromString((String)s.getAttribute("userId"));}
 static void bad(boolean condition,String message){if(condition)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 private ZoneId zone(UUID id){return ZoneId.of(db.user(id).get("timezone").toString());}
 private Instant[] range(UUID id,LocalDate from,LocalDate to){bad(!to.isAfter(from)||java.time.temporal.ChronoUnit.DAYS.between(from,to)>366,"조회 기간은 최대 366일입니다.");ZoneId z=zone(id);return new Instant[]{from.atStartOfDay(z).toInstant(),to.atStartOfDay(z).toInstant()};}
 private Map<String,Object> camel(Map<String,Object> in){Map<String,Object> out=new LinkedHashMap<>();in.forEach((k,v)->{String[] a=k.split("_");StringBuilder n=new StringBuilder(a[0]);for(int i=1;i<a.length;i++)n.append(Character.toUpperCase(a[i].charAt(0))).append(a[i].substring(1));out.put(n.toString(),v);});return out;}
 @GetMapping("/me") public Map<String,Object> me(HttpSession s){var u=camel(db.user(uid(s)));u.put("tracking",service.read(uid(s)));u.put("collectorEnabled",collectorAvailable());return u;}
 @GetMapping("/tracking/status") public TrackingEngine.State status(HttpSession s){return service.read(uid(s));}
 public record Tracking(@NotNull Boolean enabled,@NotBlank String policyVersion){}
 @PatchMapping("/me/tracking") public Map<String,Boolean> tracking(HttpSession s,@Valid @RequestBody Tracking r){bad(!r.policyVersion.equals("1.0"),"동의 내용을 다시 확인해 주세요.");service.tracking(uid(s),r.enabled);return Map.of("success",true);}
 public record Settings(@NotBlank @Size(max=100)String displayName,@NotBlank String timezone,@Min(0)long expectedVersion){}
 @PatchMapping("/me/settings") public Map<String,Boolean> settings(HttpSession s,@Valid @RequestBody Settings r){
  bad(!ZoneId.getAvailableZoneIds().contains(r.timezone),"지원하지 않는 시간대입니다.");
  if(db.settings(uid(s),r.displayName,r.timezone,r.expectedVersion)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"설정을 새로고침해 주세요.");return Map.of("success",true);
 }
 @PostMapping({"/me/steam-sync","/me/steam-diagnostics"}) public ResponseEntity<?> sync(HttpSession s){
  bad(!Boolean.TRUE.equals(db.user(uid(s)).get("tracking_enabled")),"추적 동의를 먼저 활성화해 주세요.");
  if(!collectorAvailable())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"수집기가 아직 설정되지 않았습니다.");
  if(db.requestSync(uid(s))==0)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"이미 처리 중이거나 재요청 대기시간입니다.");return ResponseEntity.accepted().body(Map.of("status","QUEUED"));
 }
 @GetMapping("/library") public List<Map<String,Object>> library(HttpSession s,@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="100")int limit,@RequestParam(defaultValue="0")int offset){bad(limit<1||limit>100||offset<0||q.length()>100,"잘못된 조회 조건입니다.");return db.library(uid(s),q,limit,offset).stream().map(this::camel).toList();}
 TrackingEngine.Play decode(Map<String,Object> row){try{var p=json.readValue(row.get("payload").toString(),TrackingEngine.Play.class);p.excluded=Boolean.TRUE.equals(row.get("excluded"));p.version=((Number)row.get("version")).longValue();return p;}catch(Exception e){throw new IllegalStateException(e);}}
 @GetMapping("/sessions") public Map<String,Object> sessions(HttpSession s,@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(required=false)String cursor,@RequestParam(defaultValue="50")int limit){
  bad(limit<1||limit>100,"잘못된 조회 건수입니다.");var r=range(uid(s),from,to);Instant before=null;UUID beforeId=null;
  if(cursor!=null){
   bad(cursor.length()>200,"잘못된 페이지 커서입니다.");
   try {String[] c=new String(Base64.getUrlDecoder().decode(cursor),java.nio.charset.StandardCharsets.UTF_8).split("\\|",-1);if(c.length!=2)throw new IllegalArgumentException();before=Instant.parse(c[0]);beforeId=UUID.fromString(c[1]);}
   catch(IllegalArgumentException|DateTimeException e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"잘못된 페이지 커서입니다.");}
  }
  var rows=db.sessions(uid(s),r[0],r[1],before,beforeId,limit+1);boolean more=rows.size()>limit;
  var data=rows.stream().limit(limit).map(this::decode).toList();String next="";
  if(more){var p=data.get(data.size()-1);next=Base64.getUrlEncoder().withoutPadding().encodeToString((p.startedAt+"|"+p.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  return Map.of("data",data,"nextCursor",next,"gaps",db.gaps(uid(s),r[0],r[1]).stream().map(this::camel).toList());
 }
 @GetMapping("/sessions/{id}") public TrackingEngine.Play detail(HttpSession s,@PathVariable UUID id){var row=db.play(uid(s),id);if(row==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND);return decode(row);}
 public record Exclusion(@NotNull Boolean excluded,@Min(0)long expectedVersion){}
 @PatchMapping("/sessions/{id}/exclusion") public Map<String,Boolean> exclude(HttpSession s,@PathVariable UUID id,@Valid @RequestBody Exclusion r){
  detail(s,id);if(db.exclude(uid(s),id,r.excluded,r.expectedVersion)!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"기록이 변경되었습니다. 새로고침해 주세요.");return Map.of("success",true);
 }
 static Instant instant(Object v){if(v instanceof java.sql.Timestamp t)return t.toInstant();if(v instanceof OffsetDateTime t)return t.toInstant();return Instant.parse(v.toString());}
 Map<String,Object> aggregate(UUID id,Instant from,Instant to){return Analytics.compute(db.segments(id,from,to).stream().map(r->new Analytics.Slice(r.get("game_id").toString(),r.get("name").toString(),instant(r.get("start_at")),instant(r.get("end_at")))).toList(),from,to,zone(id));}
 @GetMapping({"/analytics/summary","/analytics/distribution"}) public Map<String,Object> summary(HttpSession s,@RequestParam LocalDate from,@RequestParam LocalDate to){var r=range(uid(s),from,to);return aggregate(uid(s),r[0],r[1]);}
 @GetMapping("/analytics/heatmap") public Map<String,Object> heatmap(HttpSession s,@RequestParam int year){bad(year<2000||year>2100,"잘못된 연도입니다.");var r=range(uid(s),LocalDate.of(year,1,1),LocalDate.of(year+1,1,1));return aggregate(uid(s),r[0],r[1]);}
 @GetMapping("/exports/sessions") public void export(HttpSession s,@RequestParam(defaultValue="json")String format,HttpServletResponse response)throws Exception {
  bad(!Set.of("csv","json").contains(format),"지원하지 않는 형식입니다.");UUID id=uid(s);
  response.setContentType(format.equals("csv")?"text/csv;charset=UTF-8":"application/json;charset=UTF-8");
  response.setHeader("Content-Disposition","attachment; filename=game-chronicle."+format);
  var out=response.getWriter();if(format.equals("json"))out.write("[");else out.write("\uFEFFid,game,startedAt,endedAt,observedSeconds,quality,excluded\n");
  Instant before=null;UUID beforeId=null;boolean first=true;
  while(true){var rows=db.sessions(id,Instant.parse("2000-01-01T00:00:00Z"),Instant.now().plusSeconds(1),before,beforeId,100);if(rows.isEmpty())break;
   for(var row:rows){var p=decode(row);if(format.equals("json")){if(!first)out.write(",");out.write(json.writeValueAsString(p));}else out.write(p.id+","+csv(p.name)+","+p.startedAt+","+(p.endedAt==null?"":p.endedAt)+","+p.seconds()+","+p.quality+","+p.excluded+"\n");first=false;before=p.startedAt;beforeId=p.id;}
   out.flush();if(rows.size()<100)break;
  }
  if(format.equals("json"))out.write("]");
 }
 static String csv(String text){if(!text.stripLeading().isEmpty()&&"=+@-".indexOf(text.stripLeading().charAt(0))>=0)text="'"+text;return "\""+text.replace("\"","\"\"")+"\"";}
 public record Deletion(@NotBlank String confirmation){}
 @DeleteMapping("/me") public Map<String,Boolean> delete(HttpSession s,@Valid @RequestBody Deletion r){bad(!r.confirmation.equals("DELETE"),"삭제 확인 문구가 필요합니다.");
  if(Instant.now().getEpochSecond()-(Long)s.getAttribute("authenticatedAt")>600)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"삭제하려면 Steam으로 다시 로그인해 주세요.");
  service.delete(uid(s));s.invalidate();return Map.of("deleted",true);
 }
}
