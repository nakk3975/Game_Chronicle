package com.gamechronicle;

import java.time.*;
import java.util.*;

/** Pure state machine. Never extends time through failed or missing observations. */
public final class TrackingEngine {
 public enum Kind { PLAYING, NO_GAME_CANDIDATE, UNOBSERVABLE, FETCH_FAILED }
 public record Observation(Instant at, Kind kind, String gameId, String name) {}
 public static class Segment {
  public Instant start, end;
  public Segment() {}
  public Segment(Instant s, Instant e) { start=s; end=e; }
 }
 public static class Play {
  public UUID id=UUID.randomUUID();
  public String gameId, name, lifecycle="ACTIVE", quality="ESTIMATED", closeReason;
  public Instant firstSeen, lastSeen, startedAt, endedAt;
  public Instant startLower, startUpper, endLower, endUpper;
  public List<Segment> segments=new ArrayList<>();
  public boolean excluded=false;
  public long version=0;
  public long seconds() { return segments.stream().mapToLong(s->Duration.between(s.start,s.end).getSeconds()).sum(); }
 }
 public static class State {
  public String status="IDLE";
  public Instant lastAttempt,lastSuccess,lastPlaying,firstNoGame,gapStart;
  public int failures=0,noGameCount=0;
  public boolean endBoundaryKnown=false;
  public Play active;
 }
 public record Gap(Instant start, Instant end, String reason) {}
 public record Result(State state,List<Play> changed,List<Gap> gaps) {}
 private static Instant mid(Instant a,Instant b) {return a.plusSeconds(Duration.between(a,b).getSeconds()/2);}
 private static void close(State s,String reason,Instant end,Instant upper,List<Play> out) {
  Play p=s.active; if(p==null)return;
  p.endedAt=end; p.endLower=p.lastSeen; p.endUpper=upper;
  p.closeReason=reason; p.lifecycle=upper==null?"INTERRUPTED":"FINALIZED";
  if(upper==null)p.quality="PARTIAL";
  if(end.isAfter(p.lastSeen)&&!p.segments.isEmpty())p.segments.get(p.segments.size()-1).end=end;
  p.version++; out.add(p);s.active=null;s.firstNoGame=null;s.noGameCount=0;
 }
 public static Result apply(State s,Observation o) {
  List<Play> out=new ArrayList<>();List<Gap> gaps=new ArrayList<>();
  if(s.lastAttempt!=null&&!o.at().isAfter(s.lastAttempt))return new Result(s,out,gaps);
  boolean stale=s.lastAttempt!=null&&Duration.between(s.lastAttempt,o.at()).getSeconds()>180;
  if(stale) {
   if(s.gapStart==null)s.gapStart=s.lastSuccess==null?s.lastAttempt:s.lastSuccess;
   close(s,"STALE",s.lastPlaying,null,out);
  }
  s.lastAttempt=o.at();
  if(o.kind()==Kind.FETCH_FAILED||o.kind()==Kind.UNOBSERVABLE) {
   s.failures++;s.noGameCount=0;s.firstNoGame=null;
   if(s.gapStart==null)s.gapStart=s.lastSuccess==null?o.at():s.lastSuccess;
   if(s.active!=null)s.active.quality="PARTIAL";
   if(s.failures>=3||(s.lastSuccess!=null&&Duration.between(s.lastSuccess,o.at()).getSeconds()>180))
    close(s,"UNOBSERVABLE",s.lastPlaying,null,out);
   s.status="UNOBSERVABLE";return new Result(s,out,gaps);
  }
  boolean hadGap=s.gapStart!=null;
  if(hadGap) {gaps.add(new Gap(s.gapStart,o.at(),"OBSERVATION_GAP"));s.gapStart=null;}
  s.failures=0;
  if(o.kind()==Kind.PLAYING) {
   if(s.active!=null&&!s.active.gameId.equals(o.gameId())) {
    Instant boundary=s.firstNoGame==null?o.at():s.firstNoGame;
    boolean known=!hadGap&&(s.firstNoGame==null||s.endBoundaryKnown);
    close(s,"GAME_CHANGED",known?mid(s.lastPlaying,boundary):s.lastPlaying,known?boundary:null,out);
   }
   if(s.active==null) {
    Play p=new Play();p.gameId=o.gameId();p.name=o.name();p.firstSeen=o.at();p.lastSeen=o.at();
    p.startLower=!hadGap?s.lastSuccess:null;p.startUpper=o.at();
    p.startedAt=p.startLower==null?o.at():mid(p.startLower,o.at());
    if(p.startLower==null)p.quality="PARTIAL";
    p.segments.add(new Segment(p.startedAt,o.at()));s.active=p;
   } else {
    Play p=s.active;
    if(hadGap||s.firstNoGame!=null) {
     if(s.firstNoGame!=null)gaps.add(new Gap(p.lastSeen,o.at(),"END_CANDIDATE_REVERTED"));
     p.segments.add(new Segment(o.at(),o.at()));p.quality="PARTIAL";
    } else p.segments.get(p.segments.size()-1).end=o.at();
    p.lastSeen=o.at();p.lifecycle="ACTIVE";
   }
   s.active.version++;out.add(s.active);s.lastPlaying=o.at();s.firstNoGame=null;s.noGameCount=0;s.status="PLAYING";
  } else {
   if(s.active!=null) {
    if(s.firstNoGame==null){s.firstNoGame=o.at();s.endBoundaryKnown=!hadGap;s.noGameCount=1;s.active.lifecycle="PENDING_END";out.add(s.active);}
    else if(++s.noGameCount>=2) {
     close(s,"NO_GAME_CONFIRMED",!s.endBoundaryKnown?s.lastPlaying:mid(s.lastPlaying,s.firstNoGame),!s.endBoundaryKnown?null:s.firstNoGame,out);
    }
   }
   s.status=s.active==null?"IDLE":"PENDING_END";
  }
  s.lastSuccess=o.at();return new Result(s,out,gaps);
 }
 public static Result pause(State s,Instant now) {
  List<Play> out=new ArrayList<>();close(s,"USER_PAUSED",s.lastPlaying,null,out);
  s.status="PAUSED";s.gapStart=null;s.lastSuccess=null;s.lastAttempt=now;
  return new Result(s,out,List.of());
 }
}
