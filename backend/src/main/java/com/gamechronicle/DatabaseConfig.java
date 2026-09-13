package com.gamechronicle;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration(proxyBeanMethods = false)
public class DatabaseConfig {
 @Bean
 @ConfigurationProperties("spring.datasource.hikari")
 HikariDataSource dataSource(DataSourceProperties properties) {
  ConnectionSettings settings = parse(properties.getUrl(), properties.getUsername(), properties.getPassword());
  HikariDataSource source = new HikariDataSource();
  source.setJdbcUrl(settings.url);
  source.setUsername(settings.username);
  source.setPassword(settings.password);
  return source;
 }

 // Deliberately not a record: its generated toString would expose the password.
 static final class ConnectionSettings {
  final String url, username, password;
  ConnectionSettings(String url, String username, String password) {
   this.url = url; this.username = username; this.password = password;
  }
 }

 static ConnectionSettings parse(String value, String username, String password) {
  if (missing(value)) throw new IllegalStateException("DB_URL is missing. Set database environment variables in Render > Environment.");
  String url = value.strip();
  try {
   if (url.startsWith("postgres://") || url.startsWith("postgresql://")) {
    URI uri = URI.create(url);
    if (uri.getHost() == null || uri.getRawPath() == null || uri.getRawPath().length() < 2 || uri.getFragment() != null)
     throw new IllegalArgumentException();
    if (uri.getRawUserInfo() != null) {
     String[] credentials = uri.getRawUserInfo().split(":", 2);
     if (missing(username)) username = decode(credentials[0]);
     if (missing(password) && credentials.length == 2) password = decode(credentials[1]);
    }
    url = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
      + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
   }
   if (!url.startsWith("jdbc:postgresql://") || url.contains("${")) throw new IllegalArgumentException();
   // Keep credentials out of JDBC URLs, which connection libraries may log.
   String lower = url.toLowerCase(java.util.Locale.ROOT);
   if (lower.matches(".*[?&](user|password)=.*") || url.substring("jdbc:postgresql://".length()).split("/", 2)[0].contains("@"))
    throw new IllegalArgumentException();
  } catch (IllegalArgumentException e) {
   // Never include the input URI or parser exception: either can contain secrets.
   throw new IllegalStateException("DB_URL is invalid. Use jdbc:postgresql://HOST:PORT/postgres?sslmode=require with separate DB_USERNAME and DB_PASSWORD, or a PostgreSQL connection URI.");
  }
  if (missing(username)) throw new IllegalStateException("DB_USERNAME is missing. Set the database login in Render > Environment.");
  if (missing(password)) throw new IllegalStateException("DB_PASSWORD is missing. Set the database password in Render > Environment.");
  return new ConnectionSettings(url, username.strip(), password);
 }
 private static boolean missing(String value) { return value == null || value.isBlank() || value.startsWith("${"); }
 private static String decode(String value) { return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8); }
}
