package com.matchly.admin;

import com.matchly.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static com.matchly.support.ProfileJson.profile;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminStatsApiTest extends AbstractApiTest {

    @Test
    void stats_requireAdmin_andReturnCounters() throws Exception {
        String user = register(uniqueEmail("stats"), "Password1");
        createProfile(user, profile().interests(1L, 2L).build());

        mvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/admin/stats").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.activeUsers").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.newUsersLast7Days").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.profiles").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.visibleProfiles").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.interests").value(greaterThanOrEqualTo(30)))
                .andExpect(jsonPath("$.likes").isNumber())
                .andExpect(jsonPath("$.matches").isNumber())
                .andExpect(jsonPath("$.topInterests").isArray())
                .andExpect(jsonPath("$.topInterests[0].name").isString())
                .andExpect(jsonPath("$.topInterests[0].profiles").value(greaterThanOrEqualTo(1)));
    }
}
