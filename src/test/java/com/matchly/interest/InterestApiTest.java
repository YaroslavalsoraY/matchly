package com.matchly.interest;

import com.jayway.jsonpath.JsonPath;
import com.matchly.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static com.matchly.support.ProfileJson.profile;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Справочник интересов: чтение для пользователей, CRUD для администратора. */
class InterestApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Password1";

    @Test
    void list_requiresAuth_andReturnsSeededInterests() throws Exception {
        mvc.perform(get("/api/interests")).andExpect(status().isUnauthorized());

        String token = register(uniqueEmail("ilist"), PASSWORD);
        mvc.perform(get("/api/interests").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(30))))
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].category").value("ACTIVE"))
                .andExpect(jsonPath("$[*].name", hasItem("Путешествия")));
    }

    @Test
    void regularUser_cannotManageInterests() throws Exception {
        String token = register(uniqueEmail("iuser"), PASSWORD);

        mvc.perform(post("/api/admin/interests").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(interest("Шахматы", "HOBBY")))
                .andExpect(status().isForbidden());
    }

    @Test
    void admin_canCreateUpdateAndDeleteInterest() throws Exception {
        String admin = adminToken();
        String name = "Тест-" + uniqueEmail("x").substring(2, 10);

        String body = mvc.perform(post("/api/admin/interests").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(interest(name, "HOBBY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(post("/api/admin/interests").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(interest(name.toUpperCase(), "TECH")))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/admin/interests/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(interest(name + "-2", "TECH")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name + "-2"))
                .andExpect(jsonPath("$.category").value("TECH"));

        mvc.perform(post("/api/admin/interests").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(interest("", "TECH")))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/interests/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/admin/interests/{id}", id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/interests").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$[*].name", not(hasItem(name + "-2"))));
    }

    @Test
    void deletingInterest_removesItFromProfiles() throws Exception {
        String admin = adminToken();
        String body = mvc.perform(post("/api/admin/interests").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(interest("Временный-" + uniqueEmail("t").substring(2, 10), "HOBBY")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long interestId = ((Number) JsonPath.read(body, "$.id")).longValue();

        String user = register(uniqueEmail("idel"), PASSWORD);
        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(profile().interests(1L, interestId).build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.interests", hasSize(2)));

        mvc.perform(delete("/api/admin/interests/{id}", interestId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interests", hasSize(1)));
    }

    private static String interest(String name, String category) {
        return """
                {"name": "%s", "category": "%s"}
                """.formatted(name, category);
    }
}
