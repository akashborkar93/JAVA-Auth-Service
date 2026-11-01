package com.example.auth.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    private static final String PASSWORD = "a-long-test-password";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerLoginProfileLogoutFlow() throws Exception {
        String username = uniqueName("user");

        mvc.perform(json(post("/api/auth/register"), username, PASSWORD).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("assword"))));

        MockHttpSession session = new MockHttpSession();
        mvc.perform(json(post("/api/auth/login"), username, PASSWORD).with(csrf()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username));

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.createdAt").exists());

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        // The old session is invalidated, so the profile is no longer reachable with it.
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void profileWithoutSessionIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(json(post("/api/auth/login"), "anyone", PASSWORD))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointReturnsHeaderNameAndToken() throws Exception {
        mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        String username = uniqueName("dup");

        mvc.perform(json(post("/api/auth/register"), username, PASSWORD).with(csrf()))
                .andExpect(status().isCreated());
        mvc.perform(json(post("/api/auth/register"), username, PASSWORD).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    void wrongPasswordReturnsGenericUnauthorizedAndNoSession() throws Exception {
        mvc.perform(json(post("/api/auth/login"), "nobody-here", PASSWORD).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(json(post("/api/auth/register"), "a b", "short").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() throws Exception {
        mvc.perform(json(post("/api/auth/register"), "longpass", "x".repeat(73)).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void landingPageIsServedWithStrictContentSecurityPolicy() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Secure Auth Platform")));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/style.css")).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String username, String password)
            throws Exception {
        return builder.contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password)));
    }

    private static String uniqueName(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }
}
