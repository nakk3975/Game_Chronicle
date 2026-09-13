package com.gamechronicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
class TrackingEngineTest {
 Instant t(int seconds){return Instant.parse("2026-09-13T00:00:00Z").plusSeconds(seconds);}
 TrackingEngine.Result step(TrackingEngine.State s,int n,TrackingEngine.Kind k,String game){return TrackingEngine.apply(s,new TrackingEngine.Observation(t(n),k,game,game));}
 @Test void normalUsesFirstCandidateAndMidpoints(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.NO_GAME_CANDIDATE,null);step(s,60,TrackingEngine.Kind.PLAYING,"1");step(s,120,TrackingEngine.Kind.PLAYING,"1");step(s,180,TrackingEngine.Kind.NO_GAME_CANDIDATE,null);var r=step(s,240,TrackingEngine.Kind.NO_GAME_CANDIDATE,null);var p=r.changed().get(0);assertEquals(t(30),p.startedAt);assertEquals(t(150),p.endedAt);assertEquals(120,p.seconds());assertNull(s.active);}
 @Test void failureGapIsNeverCounted(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");step(s,60,TrackingEngine.Kind.PLAYING,"1");step(s,120,TrackingEngine.Kind.FETCH_FAILED,null);var r=step(s,180,TrackingEngine.Kind.PLAYING,"1");step(s,240,TrackingEngine.Kind.PLAYING,"1");assertEquals(120,s.active.seconds());assertEquals(1,r.gaps().size());assertEquals("PARTIAL",s.active.quality);}
 @Test void missingThenNoGameDoesNotBridgeGap(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");step(s,60,TrackingEngine.Kind.FETCH_FAILED,null);step(s,120,TrackingEngine.Kind.NO_GAME_CANDIDATE,null);var r=step(s,180,TrackingEngine.Kind.NO_GAME_CANDIDATE,null);assertEquals(0,r.changed().get(0).seconds());assertEquals("INTERRUPTED",r.changed().get(0).lifecycle);}
 @Test void threeFailuresInterruptAtLastSeen(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");step(s,60,TrackingEngine.Kind.FETCH_FAILED,null);step(s,120,TrackingEngine.Kind.FETCH_FAILED,null);var r=step(s,180,TrackingEngine.Kind.FETCH_FAILED,null);assertNull(s.active);assertEquals(t(0),r.changed().get(0).endedAt);}
 @Test void switchingGamesClosesThenOpens(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");var r=step(s,60,TrackingEngine.Kind.PLAYING,"2");assertEquals(2,r.changed().size());assertEquals("FINALIZED",r.changed().get(0).lifecycle);assertEquals("2",s.active.gameId);assertEquals(r.changed().get(0).endedAt,s.active.startedAt);}
 @Test void restartNeverFillsMissingTime(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");var r=step(s,600,TrackingEngine.Kind.PLAYING,"1");assertEquals(2,r.changed().size());assertEquals(0,r.changed().get(0).seconds());assertEquals(t(600),s.active.startedAt);assertEquals(1,r.gaps().size());}
 @Test void oldObservationIgnored(){var s=new TrackingEngine.State();step(s,100,TrackingEngine.Kind.PLAYING,"1");assertTrue(step(s,90,TrackingEngine.Kind.PLAYING,"2").changed().isEmpty());assertEquals("1",s.active.gameId);}
 @Test void pendingRevertedCreatesGap(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");step(s,60,TrackingEngine.Kind.NO_GAME_CANDIDATE,null);var r=step(s,120,TrackingEngine.Kind.PLAYING,"1");assertEquals(0,s.active.seconds());assertEquals(1,r.gaps().size());}
 @Test void pauseClosesAtLastObservation(){var s=new TrackingEngine.State();step(s,0,TrackingEngine.Kind.PLAYING,"1");step(s,60,TrackingEngine.Kind.PLAYING,"1");var r=TrackingEngine.pause(s,t(100));assertEquals(t(60),r.changed().get(0).endedAt);assertEquals("PAUSED",s.status);}
}
