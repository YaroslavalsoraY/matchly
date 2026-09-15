package com.matchly.profile;

import com.jayway.jsonpath.JsonPath;
import com.matchly.support.AbstractApiTest;
import com.matchly.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.Period;

import static com.matchly.support.ProfileJson.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Онбординг, редактирование, карточки чужих анкет и фото. */
class ProfileApiTest extends AbstractApiTest {

    private static final String PASSWORD = "Password1";

    @Test
    void me_withoutProfile_returns404() throws Exception {
        String token = register(uniqueEmail("noprof"), PASSWORD);

        mvc.perform(get("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Profile not found"));
    }

    @Test
    void createProfile_returns201_withComputedAgeAndInterests() throws Exception {
        String token = register(uniqueEmail("create"), PASSWORD);
        int expectedAge = Period.between(LocalDate.of(1995, 5, 10), LocalDate.now()).getYears();

        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profile().displayName("  Anna ").city("  moscow ").build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.displayName").value("Anna"))
                .andExpect(jsonPath("$.city").value("Moscow"))
                .andExpect(jsonPath("$.age").value(expectedAge))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.contact").value("@anna"))
                .andExpect(jsonPath("$.interests", hasSize(3)))
                .andExpect(jsonPath("$.interests[*].name", hasItem("Спорт")))
                .andExpect(jsonPath("$.photoUrl").isEmpty());

        mvc.perform(get("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anna"));
    }

    @Test
    void updateProfile_returns200_andReplacesInterests() throws Exception {
        String token = register(uniqueEmail("update"), PASSWORD);
        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(profile().build()))
                .andExpect(status().isCreated());

        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profile().displayName("Anya").interests(5L, 6L).ageRange(25, 40).visible(false).build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anya"))
                .andExpect(jsonPath("$.ageMin").value(25))
                .andExpect(jsonPath("$.visible").value(false))
                .andExpect(jsonPath("$.interests", hasSize(2)));
    }

    @Test
    void invalidProfile_returns400WithFieldErrors() throws Exception {
        String token = register(uniqueEmail("invalid"), PASSWORD);

        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(profile().displayName("").ageRange(40, 30).build()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors[*].field", hasItem("displayName")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("ageRangeValid")));
    }

    @Test
    void underageProfile_returns422() throws Exception {
        String token = register(uniqueEmail("young"), PASSWORD);
        String birthDate = LocalDate.now().minusYears(17).toString();

        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(profile().birthDate(birthDate).build()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("at least 18")));
    }

    @Test
    void unknownInterest_returns422() throws Exception {
        String token = register(uniqueEmail("badint"), PASSWORD);

        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(profile().interests(1L, 999_999L).build()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("interests do not exist")));
    }

    @Test
    void card_ofAnotherUser_hidesContact_andHiddenProfileLooksMissing() throws Exception {
        String anna = register(uniqueEmail("anna"), PASSWORD);
        String bob = register(uniqueEmail("bob"), PASSWORD);
        long annaProfileId = createProfile(anna, profile().build());

        mvc.perform(get("/api/profiles/{id}", annaProfileId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anna"))
                .andExpect(jsonPath("$.interests", hasSize(3)))
                .andExpect(jsonPath("$.contact").doesNotExist())
                .andExpect(jsonPath("$.ageMin").doesNotExist());

        mvc.perform(put("/api/profiles/me").header(HttpHeaders.AUTHORIZATION, bearer(anna))
                        .contentType(MediaType.APPLICATION_JSON).content(profile().visible(false).build()))
                .andExpect(status().isOk());

        mvc.perform(get("/api/profiles/{id}", annaProfileId).header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(status().isNotFound());
    }

    @Test
    void photo_uploadDownloadReplaceDelete() throws Exception {
        String token = register(uniqueEmail("photo"), PASSWORD);
        long profileId = createProfile(token, profile().build());
        byte[] png = TestImages.png();

        String body = mvc.perform(multipart(HttpMethod.PUT, "/api/profiles/me/photo")
                        .file(new MockMultipartFile("file", "me.png", "image/png", png))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl", containsString("/api/profiles/" + profileId + "/photo?v=")))
                .andReturn().getResponse().getContentAsString();
        String photoUrl = JsonPath.read(body, "$.photoUrl");

        byte[] downloaded = mvc.perform(get(photoUrl).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(png);

        // замена: тип определяется по содержимому, а не по заявленному Content-Type
        mvc.perform(multipart(HttpMethod.PUT, "/api/profiles/me/photo")
                        .file(new MockMultipartFile("file", "me.bin", "application/octet-stream", TestImages.jpeg()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/profiles/{id}/photo", profileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));

        mvc.perform(multipart(HttpMethod.PUT, "/api/profiles/me/photo")
                        .file(new MockMultipartFile("file", "evil.png", "image/png", "not an image at all".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("JPEG, PNG or WebP")));

        mvc.perform(delete("/api/profiles/me/photo").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoUrl").isEmpty());
        mvc.perform(get("/api/profiles/{id}/photo", profileId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void photo_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/profiles/1/photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void deletingUser_removesProfileAndPhoto() throws Exception {
        String token = register(uniqueEmail("cascade"), PASSWORD);
        long profileId = createProfile(token, profile().build());
        mvc.perform(multipart(HttpMethod.PUT, "/api/profiles/me/photo")
                        .file(new MockMultipartFile("file", "me.png", "image/png", TestImages.png()))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        String meBody = mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long userId = ((Number) JsonPath.read(meBody, "$.id")).longValue();
        String admin = adminToken();

        mvc.perform(delete("/api/admin/users/{id}", userId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/profiles/{id}", profileId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/profiles/{id}/photo", profileId).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
