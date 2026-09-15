package com.matchly.admin;

import com.jayway.jsonpath.JsonPath;
import com.matchly.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Управление аккаунтами: только ADMIN, блокировка сразу отзывает доступ, удаление каскадно. */
class AdminUsersApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Password1";

    @Test
    void regularUser_cannotAccessAdminEndpoints() throws Exception {
        String token = register(uniqueEmail("plain"), PASSWORD);

        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void admin_canSearchUsersByEmail() throws Exception {
        String email = uniqueEmail("search");
        register(email, PASSWORD);
        String admin = adminToken();

        mvc.perform(get("/api/admin/users").param("q", email).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value(email))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void admin_canBlockAndUnblockUser_accessRevokedImmediately() throws Exception {
        String email = uniqueEmail("block");
        String userToken = register(email, PASSWORD);
        long userId = idOf(userToken);
        String admin = adminToken();

        mvc.perform(post("/api/admin/users/{id}/block", userId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        // старый токен больше не работает
        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Account is blocked"));

        // и войти заново нельзя
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Account is blocked"));

        mvc.perform(post("/api/admin/users/{id}/unblock", userId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isOk());
    }

    @Test
    void admin_canDeleteUser() throws Exception {
        String userToken = register(uniqueEmail("del"), PASSWORD);
        long userId = idOf(userToken);
        String admin = adminToken();

        mvc.perform(delete("/api/admin/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(userToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("User no longer exists"));

        mvc.perform(get("/api/admin/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"));
    }

    @Test
    void admin_cannotBlockOrDeleteSelf() throws Exception {
        String admin = adminToken();
        long adminId = idOf(admin);

        mvc.perform(post("/api/admin/users/{id}/block", adminId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Business rule violated"));

        mvc.perform(delete("/api/admin/users/{id}", adminId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void list_pagination_clampsSizeAndHandlesOutOfRangePage() throws Exception {
        String admin = adminToken();

        mvc.perform(get("/api/admin/users").param("size", "1000").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mvc.perform(get("/api/admin/users").param("size", "0").param("page", "-3").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0));
        mvc.perform(get("/api/admin/users").param("page", "9999").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/admin/users").param("q", "   ").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/admin/users/{id}", 999_999).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/users/{id}", "abc").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isBadRequest());
    }

    private long idOf(String token) throws Exception {
        String body = mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
