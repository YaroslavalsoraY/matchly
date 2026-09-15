package com.matchly.recommendation;

import com.matchly.support.AbstractApiTest;
import com.matchly.support.ProfileJson;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static com.matchly.support.ProfileJson.profile;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Рекомендации через API. Чтобы анкеты других тестов не попадали в выборку,
 * участники этого теста живут в возрастном диапазоне 60-65 лет, недоступном остальным.
 */
class RecommendationApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Password1";
    private static final String BORN_63 = "1963-04-01";
    private static final String BORN_62 = "1964-04-01";

    private static ProfileJson senior(String name) {
        return profile().displayName(name).birthDate(BORN_62).ageRange(60, 65).city("Sochi").contact("@" + name);
    }

    @Test
    void recommendations_filterCandidates_andRankByStrategy() throws Exception {
        String viewer = register(uniqueEmail("viewer"), PASSWORD);
        createProfile(viewer, senior("Viewer").birthDate(BORN_63).gender("FEMALE").lookingFor("MALE").interests(1L, 2L, 3L).build());

        String best = register(uniqueEmail("best"), PASSWORD);
        long bestId = createProfile(best, senior("Best").gender("MALE").lookingFor("FEMALE").interests(1L, 2L, 3L, 4L).build());
        String weak = register(uniqueEmail("weak"), PASSWORD);
        long weakId = createProfile(weak, senior("Weak").gender("MALE").lookingFor("FEMALE").city("Kazan").interests(20L).build());
        String wrongPreference = register(uniqueEmail("wrongpref"), PASSWORD);
        createProfile(wrongPreference, senior("WrongPref").gender("MALE").lookingFor("MALE").interests(1L).build());
        String wrongGender = register(uniqueEmail("wronggender"), PASSWORD);
        createProfile(wrongGender, senior("WrongGender").gender("FEMALE").lookingFor("MALE").interests(1L).build());
        String hidden = register(uniqueEmail("hidden"), PASSWORD);
        createProfile(hidden, senior("Hidden").gender("MALE").lookingFor("FEMALE").visible(false).interests(1L).build());
        String skipped = register(uniqueEmail("skipped"), PASSWORD);
        long skippedId = createProfile(skipped, senior("Skipped").gender("MALE").lookingFor("FEMALE").interests(1L, 2L).build());
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(viewer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\": %d, \"type\": \"SKIP\"}".formatted(skippedId)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/recommendations").param("strategy", "CONTENT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("CONTENT"))
                .andExpect(jsonPath("$.strategyTitle").value("По интересам"))
                .andExpect(jsonPath("$.candidatesConsidered").value(2))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].profile.id").value(bestId))
                .andExpect(jsonPath("$.items[0].profile.displayName").value("Best"))
                .andExpect(jsonPath("$.items[0].profile.contact").doesNotExist())
                .andExpect(jsonPath("$.items[0].score").value(org.hamcrest.Matchers.greaterThan(0.8)))
                .andExpect(jsonPath("$.items[0].reasons", hasItem(containsString("Общие интересы"))))
                .andExpect(jsonPath("$.items[0].reasons", hasItem("Тот же город")))
                .andExpect(jsonPath("$.items[1].profile.id").value(weakId))
                .andExpect(jsonPath("$.items[*].profile.displayName", not(hasItem("Skipped"))))
                .andExpect(jsonPath("$.items[*].profile.displayName", not(hasItem("Hidden"))));

        mvc.perform(get("/api/recommendations").param("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("HYBRID"))
                .andExpect(jsonPath("$.items", hasSize(1)));

        // популярность: Weak получает лайк от Skipped и обгоняет Best
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(skipped))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\": %d, \"type\": \"LIKE\"}".formatted(weakId)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recommendations").param("strategy", "POPULARITY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(jsonPath("$.items[0].profile.id").value(weakId))
                .andExpect(jsonPath("$.items[0].reasons", hasItem("1 лайк от других пользователей")))
                .andExpect(jsonPath("$.items[1].score").value(0.0));

        // гибрид: Best лайкнул зрителя -> появляется причина про интерес
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(best))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetProfileId\": %d, \"type\": \"LIKE\"}".formatted(
                                ((Number) com.jayway.jsonpath.JsonPath.read(
                                        mvc.perform(get("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                                                .andReturn().getResponse().getContentAsString(), "$.id")).longValue())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recommendations").param("strategy", "HYBRID")
                        .header(HttpHeaders.AUTHORIZATION, bearer(viewer)))
                .andExpect(jsonPath("$.items[0].profile.id").value(bestId))
                .andExpect(jsonPath("$.items[0].reasons", hasItem("Проявил(а) интерес к вам")));
    }

    @Test
    void recommendations_withoutProfile_returns404() throws Exception {
        String token = register(uniqueEmail("noprofile"), PASSWORD);
        mvc.perform(get("/api/recommendations").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownStrategy_orBadLimit_returns400_andLimitIsClamped() throws Exception {
        String token = register(uniqueEmail("badstrategy"), PASSWORD);
        createProfile(token, senior("Bad").build());
        mvc.perform(get("/api/recommendations").param("strategy", "MAGIC").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        mvc.perform(get("/api/recommendations").param("limit", "abc").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
        // отрицательный и огромный limit не ломают запрос: значение приводится к диапазону 1..50
        mvc.perform(get("/api/recommendations").param("limit", "-5").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/recommendations").param("limit", "100000").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void strategies_listAll_andAdminCanChangeDefault() throws Exception {
        String token = register(uniqueEmail("strat"), PASSWORD);
        String admin = adminToken();

        mvc.perform(get("/api/recommendations/strategies").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.type == 'HYBRID')].isDefault").value(true))
                .andExpect(jsonPath("$[?(@.type == 'CONTENT')].title").value("По интересам"));

        mvc.perform(put("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"defaultStrategy\": \"CONTENT\"}"))
                .andExpect(status().isForbidden());

        try {
            mvc.perform(put("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"defaultStrategy\": \"CONTENT\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.defaultStrategy").value("CONTENT"));
            mvc.perform(get("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                    .andExpect(jsonPath("$.defaultStrategy").value("CONTENT"));
            mvc.perform(get("/api/recommendations/strategies").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(jsonPath("$[?(@.type == 'CONTENT')].isDefault").value(true))
                    .andExpect(jsonPath("$[?(@.type == 'HYBRID')].isDefault").value(false));
        } finally {
            // настройка глобальная, возвращаем значение, чтобы не влиять на другие тесты
            mvc.perform(put("/api/admin/settings").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"defaultStrategy\": \"HYBRID\"}"))
                    .andExpect(status().isOk());
        }
    }
}
