package com.gamechronicle;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
class AuthTest {
 Map<String,String> valid(){return new HashMap<>(Map.of("openid.ns","http://specs.openid.net/auth/2.0","openid.mode","id_res","openid.op_endpoint","https://steamcommunity.com/openid/login","openid.return_to","https://example.com/auth/steam/callback?state=x","openid.claimed_id","https://steamcommunity.com/openid/id/76561198000000000","openid.identity","https://steamcommunity.com/openid/id/76561198000000000","openid.response_nonce","2026-09-13T00:00:00Zrandom","openid.signed","op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle"));}
 String check(Map<String,String> p){return AuthController.validate(p,"https://example.com/auth/steam/callback?state=x",Instant.parse("2026-09-13T00:01:00Z"));}
 @Test void validatesBoundAssertion(){assertEquals("76561198000000000",check(valid()));}
 @Test void rejectsWrongProvider(){var p=valid();p.put("openid.op_endpoint","https://evil.example/");assertThrows(IllegalArgumentException.class,()->check(p));}
 @Test void rejectsUnsignedIdentity(){var p=valid();p.put("openid.signed","return_to");assertThrows(IllegalArgumentException.class,()->check(p));}
 @Test void rejectsMismatchedIdentity(){var p=valid();p.put("openid.identity","https://steamcommunity.com/openid/id/76561198000000001");assertThrows(IllegalArgumentException.class,()->check(p));}
 @Test void rejectsStaleNonce(){var p=valid();p.put("openid.response_nonce","2026-09-12T00:00:00Zrandom");assertThrows(IllegalArgumentException.class,()->check(p));}
}
