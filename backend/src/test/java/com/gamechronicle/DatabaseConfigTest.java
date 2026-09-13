package com.gamechronicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DatabaseConfigTest {
 @Test void jdbcRemainsUnchanged(){var c=DatabaseConfig.parse("jdbc:postgresql://db.example:5432/postgres?sslmode=require","runtime","secret");assertEquals("jdbc:postgresql://db.example:5432/postgres?sslmode=require",c.url);assertEquals("runtime",c.username);}
 @Test void uriCredentialsAreDecodedAndRemovedFromUrl(){var c=DatabaseConfig.parse("postgresql://runtime:p%40ss+word@db.example:5432/postgres?sslmode=require",null,null);assertEquals("p@ss+word",c.password);assertEquals("runtime",c.username);assertEquals("jdbc:postgresql://db.example:5432/postgres?sslmode=require",c.url);assertFalse(c.toString().contains("p@ss"));}
 @Test void separateCredentialsOverrideUri(){var c=DatabaseConfig.parse("postgres://old:old@db.example/postgres","new","new-secret");assertEquals("new",c.username);assertEquals("new-secret",c.password);}
 @Test void missingSettingsHaveActionableErrors(){for(String value:new String[]{"","${DB_URL}"})assertTrue(assertThrows(IllegalStateException.class,()->DatabaseConfig.parse(value,null,null)).getMessage().contains("DB_URL is missing"));assertTrue(assertThrows(IllegalStateException.class,()->DatabaseConfig.parse("jdbc:postgresql://db.example/postgres","runtime",null)).getMessage().contains("DB_PASSWORD is missing"));}
 @Test void invalidUriDoesNotLeakCredentials(){var e=assertThrows(IllegalStateException.class,()->DatabaseConfig.parse("postgres://runtime:TOP SECRET@db.example/postgres",null,null));assertFalse(e.toString().contains("TOP SECRET"));assertNull(e.getCause());}
 @Test void credentialsInJdbcUrlAreRejected(){assertThrows(IllegalStateException.class,()->DatabaseConfig.parse("jdbc:postgresql://db.example/postgres?password=secret","runtime","secret"));}
}
