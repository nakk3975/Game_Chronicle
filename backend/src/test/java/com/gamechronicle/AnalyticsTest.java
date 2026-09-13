package com.gamechronicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
class AnalyticsTest {
 @Test void splitsAtSeoulMidnight(){var a=Instant.parse("2026-09-13T14:30:00Z");var b=a.plusSeconds(3600);var r=Analytics.compute(List.of(new Analytics.Slice("1","A",a,b)),a,b,ZoneId.of("Asia/Seoul"));assertEquals(Map.of("2026-09-13",1800L,"2026-09-14",1800L),r.get("days"));}
 @Test void clipsCrossingRange(){var a=Instant.parse("2026-09-13T14:30:00Z");var r=Analytics.compute(List.of(new Analytics.Slice("1","A",a,a.plusSeconds(7200))),a.plusSeconds(1800),a.plusSeconds(3600),ZoneId.of("UTC"));assertEquals(1800L,r.get("seconds"));}
 @Test void dstFallbackCountsRepeatedHour(){var a=Instant.parse("2026-11-01T05:00:00Z");var r=Analytics.compute(List.of(new Analytics.Slice("1","A",a,a.plusSeconds(7200))),a,a.plusSeconds(7200),ZoneId.of("America/New_York"));assertEquals(Map.of("1",7200L),r.get("hours"));}
 @Test void csvEscapesFormulaAndQuotes(){assertEquals("\"'=SUM(1,2)\"",ApiController.csv("=SUM(1,2)"));assertEquals("\"a\"\"b\"",ApiController.csv("a\"b"));}
}
