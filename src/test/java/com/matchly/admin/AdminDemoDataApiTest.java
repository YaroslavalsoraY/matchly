package com.matchly.admin;

import com.matchly.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ручной посев демо-данных: только для администратора, повторный вызов ничего не добавляет. */
class AdminDemoDataApiTest extends AbstractApiTest {

    @Test
    void demoData_requiresAdmin_seedsOnce_andIsIdempotent() throws Exception {
        String user = register(uniqueEmail("seeduser"), "Password1");
        mvc.perform(post("/api/admin/demo-data").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());

        String admin = adminToken();
        mvc.perform(post("/api/admin/demo-data").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEEDED"))
                .andExpect(jsonPath("$.profiles").value(8))
                .andExpect(jsonPath("$.likes").isNumber());

        mvc.perform(post("/api/admin/demo-data").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ALREADY_PRESENT"))
                .andExpect(jsonPath("$.profiles").value(0));

        // демо-пользователь может войти
        mvc.perform(post("/api/auth/login").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(credentials("demo1@matchly.local", "demo1234")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").param("q", "demo").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.totalElements").value(8));
    }
}
