package com.gamechronicle;
import java.time.*;
import java.util.*;
public final class Analytics {
 public record Slice(String gameId,String name,Instant start,Instant end){}
 public static Map<String,Object> compute(List<Slice> rows,Instant from,Instant to,ZoneId zone){
  Map<String,Long> days=new TreeMap<>(),games=new LinkedHashMap<>(),weekdays=new TreeMap<>(),hours=new TreeMap<>();
  long total=0;
  // P0 single-active invariant prevents cross-game overlap. Each segment is half-open.
  for(var r:rows){
   Instant a=r.start().isBefore(from)?from:r.start(),b=r.end().isAfter(to)?to:r.end();
   if(!b.isAfter(a))continue;
   long sec=Duration.between(a,b).getSeconds();total+=sec;games.merge(r.gameId(),sec,Long::sum);
   while(a.isBefore(b)){
    ZonedDateTime local=a.atZone(zone);
    Instant boundary=local.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant();
    Instant midnight=local.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant();
    if(midnight.isBefore(boundary))boundary=midnight;
    Instant end=boundary.isBefore(b)?boundary:b;
    long n=Duration.between(a,end).getSeconds();
    days.merge(local.toLocalDate().toString(),n,Long::sum);
    weekdays.merge(local.getDayOfWeek().toString(),n,Long::sum);hours.merge(Integer.toString(local.getHour()),n,Long::sum);a=end;
   }
  }
  return Map.of("seconds",total,"days",days,"games",games,"weekdays",weekdays,"hours",hours,"timezone",zone.toString(),"isApproximate",true,"aggregateVersion",1);
 }
}
