package com.gamechronicle;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.*;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
@Configuration
public class Security {
 @Bean SecurityFilterChain chain(HttpSecurity h) throws Exception {
  h.authorizeHttpRequests(a->a.anyRequest().permitAll())
   .csrf(c->c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
   .headers(x->x.contentSecurityPolicy(c->c.policyDirectives("default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' https://cdn.akamai.steamstatic.com https://shared.akamai.steamstatic.com data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self' https://steamcommunity.com")))
   .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable());
  return h.build();
 }
 @Bean OncePerRequestFilter accessFilter(Store store) {return new OncePerRequestFilter(){
  protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException {
   if(req.getRequestURI().startsWith("/api/")) {
    res.setHeader("Cache-Control","no-store");
    if(!req.getRequestURI().equals("/api/v1/csrf")) {
     HttpSession s=req.getSession(false);
     Object id=s==null?null:s.getAttribute("userId");
     Long born=s==null?null:(Long)s.getAttribute("authenticatedAt");
     if(id==null||born==null||Instant.now().getEpochSecond()-born>30L*86400||store.user(java.util.UUID.fromString(id.toString()))==null) {
      if(s!=null)s.invalidate();res.setStatus(401);res.setContentType("application/json");
      res.getWriter().write("{\"error\":{\"code\":\"LOGIN_REQUIRED\",\"message\":\"로그인이 필요합니다.\"}}");return;
     }
    }
   }
   chain.doFilter(req,res);
  }
 };}
}
