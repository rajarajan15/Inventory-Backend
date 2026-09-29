package com.example.inventory;

import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Error responses are consistent and user-readable; sessions, password policy and CSRF/XSS hardening behave.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndErrorHandlingTest {

    private static final String ORG = "/api/orgs/sec-org";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        Organization org = organizationRepository.findBySlug("sec-org")
                .orElseGet(() -> organizationRepository.save(new Organization("Sec Org", "sec-org", null, null)));
        if (!userRepository.existsByEmail("admin@sec.test")) {
            userRepository.save(new User("Sec Admin", "admin@sec.test", passwordEncoder.encode("Admin@1234"), Role.ADMIN, UserStatus.ACTIVE, org));
        }
    }

    private JsonNode login() throws Exception {
        String body = mockMvc.perform(post(ORG + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "admin@sec.test", "password", "Admin@1234"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private String bearer(JsonNode session) {
        return "Bearer " + session.get("accessToken").asText();
    }

    @Test
    void weakPasswordOnRegister_returnsFieldErrorListingTheRules() throws Exception {
        mockMvc.perform(post(ORG + "/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Weak", "email", "weak@sec.test", "password", "password"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.validationErrors.password").value(containsString("an uppercase letter")))
                .andExpect(jsonPath("$.validationErrors.password").value(containsString("a number")));
    }

    @Test
    void secondLogin_doesNotSignOutFirstSession() throws Exception {
        JsonNode first = login();
        JsonNode second = login();

        for (JsonNode session : new JsonNode[]{first, second}) {
            mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("refreshToken", session.get("refreshToken").asText()))))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void refreshErrors_neverEchoTheToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"stolen-token-value\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(not(containsString("stolen-token-value"))));
    }

    @Test
    void accessTokenInCookie_isIgnored() throws Exception {
        String token = login().get("accessToken").asText();
        mockMvc.perform(get(ORG + "/products").cookie(new Cookie("access_token", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Please sign in to continue."));
    }

    @Test
    void malformedJson_returns400WithReadableMessage() throws Exception {
        mockMvc.perform(post(ORG + "/categories").header("Authorization", bearer(login()))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body is missing or is not valid JSON."));
    }

    @Test
    void invalidEnumQueryParameter_listsAllowedValues() throws Exception {
        mockMvc.perform(get(ORG + "/users?status=SLEEPING").header("Authorization", bearer(login())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("PENDING, ACTIVE, REJECTED, DISABLED")));
    }

    @Test
    void invalidEnumInBody_isAFieldError() throws Exception {
        mockMvc.perform(post(ORG + "/users/invite").header("Authorization", bearer(login()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@sec.test\",\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.role").value(containsString("ADMIN")));
    }

    @Test
    void nonNumericId_returns400() throws Exception {
        mockMvc.perform(get(ORG + "/products/abc").header("Authorization", bearer(login())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'abc' is not a valid value for id."));
    }

    @Test
    void csvImport_rejectsMissingAndNonCsvFiles() throws Exception {
        String auth = bearer(login());
        mockMvc.perform(multipart(ORG + "/organization/import-csv").header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please choose a file to upload."));
        mockMvc.perform(multipart(ORG + "/organization/import-csv")
                        .file(new MockMultipartFile("file", "stock.xlsx", "application/octet-stream", new byte[]{1, 2, 3}))
                        .header("Authorization", auth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Please upload a .csv file")));
    }

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(patch(ORG + "/categories").header("Authorization", bearer(login())))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")));
    }
}
