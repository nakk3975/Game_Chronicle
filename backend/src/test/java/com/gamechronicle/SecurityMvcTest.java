package com.gamechronicle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ApiController.class) @Import(Security.class)
class SecurityMvcTest {
 @Autowired MockMvc mvc;
 @MockitoBean Store store;
 @MockitoBean TrackingService service;
 @MockitoBean SteamClient steam;
 @Test void anonymousCannotReadMe() throws Exception {mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());}
 @Test void stateChangeRequiresCsrf() throws Exception {mvc.perform(patch("/api/v1/me/tracking").contentType("application/json").content("{\"enabled\":true,\"policyVersion\":\"1.0\"}")).andExpect(status().isForbidden());}
}
