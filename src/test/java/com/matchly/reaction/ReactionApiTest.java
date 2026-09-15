package com.matchly.reaction;

import com.jayway.jsonpath.JsonPath;
import com.matchly.support.AbstractApiTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static com.matchly.support.ProfileJson.profile;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Лайк, пропуск, взаимный лайк = матч, разрыв матча. */
class ReactionApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Password1";

    @Test
    void likeSkipMatchUnmatch_fullFlow() throws Exception {
        String anna = register(uniqueEmail("anna"), PASSWORD);
        String bob = register(uniqueEmail("bob"), PASSWORD);
        long annaId = createProfile(anna, profile().displayName("Anna").contact("@anna").build());
        long bobId = createProfile(bob, profile().displayName("Bob").gender("MALE").lookingFor("FEMALE").contact("@bob").build());

        // Боб лайкает Анну: пока не взаимно
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(annaId, "LIKE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LIKE"))
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.match").isEmpty());
        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(anna)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Анна отвечает лайком: матч, контакт Боба раскрыт
        String body = mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(bobId, "LIKE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.match.profile.displayName").value("Bob"))
                .andExpect(jsonPath("$.match.profile.id").value(bobId))
                .andExpect(jsonPath("$.match.contact").value("@bob"))
                .andExpect(jsonPath("$.match.matchedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        long matchId = ((Number) JsonPath.read(body, "$.match.id")).longValue();

        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(matchId))
                .andExpect(jsonPath("$[0].profile.displayName").value("Anna"))
                .andExpect(jsonPath("$[0].contact").value("@anna"));
        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(anna)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].contact").value("@bob"));

        // повторный лайк идемпотентен: матч тот же
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(bobId, "LIKE")))
                .andExpect(jsonPath("$.match.id").value(matchId));

        // пропуск после матча разрывает его
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(bobId, "SKIP")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false));
        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(jsonPath("$", hasSize(0)));

        // передумала: лайк снова создаёт матч (у Боба лайк остался)
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(bobId, "LIKE")))
                .andExpect(jsonPath("$.matched").value(true));
        body = mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        long newMatchId = ((Number) JsonPath.read(body, "$[0].id")).longValue();

        // Боб разрывает матч
        mvc.perform(delete("/api/matches/{id}", newMatchId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(anna)))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(delete("/api/matches/{id}", newMatchId).header(HttpHeaders.AUTHORIZATION, bearer(anna)))
                .andExpect(status().isNotFound());

        // после разрыва лайк Боба стал пропуском, поэтому лайк Анны матч не создаёт
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(bobId, "LIKE")))
                .andExpect(jsonPath("$.matched").value(false));
    }

    @Test
    void react_withoutOwnProfile_returns404() throws Exception {
        String lonely = register(uniqueEmail("lonely"), PASSWORD);
        String other = register(uniqueEmail("other"), PASSWORD);
        long otherId = createProfile(other, profile().build());

        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(lonely))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(otherId, "LIKE")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Profile not found"));
    }

    @Test
    void react_toSelf_returns422() throws Exception {
        String token = register(uniqueEmail("self"), PASSWORD);
        long myId = createProfile(token, profile().build());

        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(myId, "LIKE")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void react_toUnknownOrHiddenProfile_returns404() throws Exception {
        String token = register(uniqueEmail("seeker"), PASSWORD);
        createProfile(token, profile().build());
        String hidden = register(uniqueEmail("hidden"), PASSWORD);
        long hiddenId = createProfile(hidden, profile().visible(false).build());

        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(999_999L, "LIKE")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(hiddenId, "LIKE")))
                .andExpect(status().isNotFound());
    }

    @Test
    void react_invalidBody_returns400() throws Exception {
        String token = register(uniqueEmail("badbody"), PASSWORD);

        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"targetProfileId\": 1, \"type\": \"LOVE\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\": \"LIKE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetProfileId"));
    }

    @Test
    void unmatch_foreignMatch_returns404() throws Exception {
        String anna = register(uniqueEmail("anna2"), PASSWORD);
        String bob = register(uniqueEmail("bob2"), PASSWORD);
        String carol = register(uniqueEmail("carol"), PASSWORD);
        long annaId = createProfile(anna, profile().build());
        long bobId = createProfile(bob, profile().gender("MALE").lookingFor("FEMALE").build());
        createProfile(carol, profile().build());

        mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(bob))
                .contentType(MediaType.APPLICATION_JSON).content(reaction(annaId, "LIKE")));
        String body = mvc.perform(post("/api/reactions").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(reaction(bobId, "LIKE")))
                .andExpect(jsonPath("$.matched").value(true))
                .andReturn().getResponse().getContentAsString();
        long matchId = ((Number) JsonPath.read(body, "$.match.id")).longValue();

        mvc.perform(delete("/api/matches/{id}", matchId).header(HttpHeaders.AUTHORIZATION, bearer(carol)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(anna)))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private static String reaction(long targetProfileId, String type) {
        return """
                {"targetProfileId": %d, "type": "%s"}
                """.formatted(targetProfileId, type);
    }
}
