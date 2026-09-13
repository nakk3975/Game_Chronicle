package com.gamechronicle;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
@RestController
public class AuthController {
 private static final String OP="https://steamcommunity.com/openid/login",NS="http://specs.openid.net/auth/2.0";
 private final Store store;private final SteamClient steam;private final String base;
 public AuthController(Store store,SteamClient steam,@Value("${app.base-url}")String base){this.store=store;this.steam=steam;this.base=base.replaceAll("/$","");}
 static String form(Map<String,String> p){return p.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));}
 @GetMapping("/api/v1/csrf") public Map<String,String> csrf(CsrfToken t){return Map.of("token",t.getToken(),"headerName",t.getHeaderName());}
 @GetMapping("/auth/steam/start") public void start(HttpServletRequest req,HttpServletResponse res)throws Exception {
  String state=UUID.randomUUID().toString();HttpSession s=req.getSession();
  String callback=base+"/auth/steam/callback?state="+state;
  s.setAttribute("openidReturn",callback);s.setAttribute("openidTime",Instant.now().getEpochSecond());
  var p=new LinkedHashMap<String,String>();p.put("openid.ns",NS);p.put("openid.mode","checkid_setup");
  p.put("openid.return_to",callback);p.put("openid.realm",base+"/");
  p.put("openid.identity",NS+"/identifier_select");p.put("openid.claimed_id",NS+"/identifier_select");
  res.sendRedirect(OP+"?"+form(p));
 }
 static String validate(Map<String,String> p,String expected,Instant now) {
  if(!NS.equals(p.get("openid.ns"))||!"id_res".equals(p.get("openid.mode"))||!OP.equals(p.get("openid.op_endpoint"))||!Objects.equals(expected,p.get("openid.return_to")))throw new IllegalArgumentException();
  String id=p.getOrDefault("openid.claimed_id","");
  if(!id.matches("https://steamcommunity.com/openid/id/[0-9]{17,20}")||!id.equals(p.get("openid.identity")))throw new IllegalArgumentException();
  Set<String> signed=new HashSet<>(Arrays.asList(p.getOrDefault("openid.signed","").split(",")));
  if(!signed.containsAll(Set.of("op_endpoint","claimed_id","identity","return_to","response_nonce","assoc_handle")))throw new IllegalArgumentException();
  String nonce=p.getOrDefault("openid.response_nonce","");if(nonce.length()<21||nonce.length()>255)throw new IllegalArgumentException();
  Instant issued=Instant.parse(nonce.substring(0,20));
  if(issued.isBefore(now.minusSeconds(300))||issued.isAfter(now.plusSeconds(60)))throw new IllegalArgumentException();
  return id.substring(id.lastIndexOf('/')+1);
 }
 @GetMapping("/auth/steam/callback") public void callback(HttpServletRequest req,HttpServletResponse res,@RequestParam Map<String,String> params)throws Exception {
  HttpSession s=req.getSession(false);
  try {
   if(s==null)throw new IllegalArgumentException();
   String expected=(String)s.getAttribute("openidReturn");Long created=(Long)s.getAttribute("openidTime");
   s.removeAttribute("openidReturn");s.removeAttribute("openidTime");
   if(expected==null||created==null||Instant.now().getEpochSecond()-created>300)throw new IllegalArgumentException();
   if(!expected.equals(base+"/auth/steam/callback?state="+params.get("state")))throw new IllegalArgumentException();
   if(req.getParameterMap().values().stream().anyMatch(v->v.length!=1))throw new IllegalArgumentException();
   String steamId=validate(params,expected,Instant.now());
   Map<String,String> p=new LinkedHashMap<>();params.forEach((k,v)->{if(k.startsWith("openid."))p.put(k,v);});p.put("openid.mode","check_authentication");
   if(!Arrays.asList(steam.verifyOpenId(form(p)).split("\r?\n")).contains("is_valid:true"))throw new IllegalArgumentException();
   store.nonce(params.get("openid.response_nonce"));UUID user=store.login(steamId);store.ensureState(user);
   req.changeSessionId();s.setAttribute("userId",user.toString());s.setAttribute("authenticatedAt",Instant.now().getEpochSecond());
   res.sendRedirect("/dashboard");
  } catch(Exception e){res.sendRedirect("/?auth=failed");}
 }
 @PostMapping("/api/v1/auth/logout") public Map<String,Boolean> logout(HttpServletRequest req){req.getSession().invalidate();return Map.of("success",true);}
}
