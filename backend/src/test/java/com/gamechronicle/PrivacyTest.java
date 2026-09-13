package com.gamechronicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.time.*;
class PrivacyTest {
 @Test void lateCollectorCannotWriteAfterPause(){Store db=mock(Store.class);UUID id=UUID.randomUUID();when(db.lockUser(id)).thenReturn(Map.of("tracking_enabled",false,"status","ACTIVE"));new TrackingService(db,new ObjectMapper()).apply(id,1,new TrackingEngine.Observation(Instant.now(),TrackingEngine.Kind.PLAYING,"1","A"),60);verify(db,never()).stateSave(any(),any());}
 @Test void expiredLeaseCannotWrite(){Store db=mock(Store.class);UUID id=UUID.randomUUID();when(db.lockUser(id)).thenReturn(Map.of("tracking_enabled",true,"status","ACTIVE"));when(db.validLease(id,1)).thenReturn(0);new TrackingService(db,new ObjectMapper()).apply(id,1,new TrackingEngine.Observation(Instant.now(),TrackingEngine.Kind.PLAYING,"1","A"),60);verify(db,never()).stateSave(any(),any());}
 @Test void anotherUsersSessionIs404(){Store db=mock(Store.class);var c=new ApiController(db,mock(TrackingService.class),new ObjectMapper(),mock(SteamClient.class),false);var s=new org.springframework.mock.web.MockHttpSession();UUID owner=UUID.randomUUID(),other=UUID.randomUUID();s.setAttribute("userId",owner.toString());when(db.play(owner,other)).thenReturn(null);assertThrows(org.springframework.web.server.ResponseStatusException.class,()->c.detail(s,other));verify(db).play(owner,other);}
 @Test void lateLibraryFailureCannotOverwritePausedOrNewerConsent(){
  Store db=mock(Store.class);UUID id=UUID.randomUUID();var service=new TrackingService(db,new ObjectMapper());
  when(db.lockUser(id)).thenReturn(Map.of("tracking_enabled",false,"version",2L));service.libraryFailed(id,1);
  when(db.lockUser(id)).thenReturn(Map.of("tracking_enabled",true,"version",3L));service.libraryFailed(id,1);
  verify(db,never()).syncDone(any(),any());
 }
 @Test void malformedCursorIsBadRequest(){
  Store db=mock(Store.class);UUID id=UUID.randomUUID();when(db.user(id)).thenReturn(Map.of("timezone","UTC"));
  var c=new ApiController(db,mock(TrackingService.class),new ObjectMapper(),mock(SteamClient.class),false);
  var s=new org.springframework.mock.web.MockHttpSession();s.setAttribute("userId",id.toString());
  for(String raw:List.of("missing_separator","bad-date|"+UUID.randomUUID(),"x|y|z")){
   String cursor=Base64.getUrlEncoder().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
   var e=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->c.sessions(s,LocalDate.of(2026,1,1),LocalDate.of(2026,2,1),cursor,50));
   assertEquals(400,e.getStatusCode().value());
  }
 }
 @Test void renderCannotWriteAfterEdgeCutover(){
  Store db=mock(Store.class);UUID id=UUID.randomUUID();when(db.collectorEngine()).thenReturn("edge");
  when(db.lockUser(id)).thenReturn(Map.of("tracking_enabled",true,"status","ACTIVE","version",1L));
  var service=new TrackingService(db,new ObjectMapper());
  service.apply(id,1,new TrackingEngine.Observation(Instant.now(),TrackingEngine.Kind.PLAYING,"1","A"),60);
  service.library(id,1,new ObjectMapper().createObjectNode());service.libraryFailed(id,1);
  verify(db,never()).stateSave(any(),any());verify(db,never()).syncDone(any(),any());
 }
}
