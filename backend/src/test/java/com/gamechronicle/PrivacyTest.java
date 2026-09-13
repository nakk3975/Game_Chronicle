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
}
