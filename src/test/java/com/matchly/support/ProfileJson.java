package com.matchly.support;

import java.util.List;
import java.util.stream.Collectors;

/** Конструктор JSON-тела анкеты с разумными значениями по умолчанию. */
public final class ProfileJson {

    private String displayName = "Anna";
    private String birthDate = "1995-05-10";
    private String gender = "FEMALE";
    private String lookingFor = "MALE";
    private int ageMin = 20;
    private int ageMax = 35;
    private String city = "Moscow";
    private String bio = "Люблю кофе и горы";
    private String contact = "@anna";
    private List<Long> interestIds = List.of(1L, 2L, 3L);
    private Boolean visible = null;

    public static ProfileJson profile() {
        return new ProfileJson();
    }

    public ProfileJson displayName(String v) { this.displayName = v; return this; }
    public ProfileJson birthDate(String v) { this.birthDate = v; return this; }
    public ProfileJson gender(String v) { this.gender = v; return this; }
    public ProfileJson lookingFor(String v) { this.lookingFor = v; return this; }
    public ProfileJson ageRange(int min, int max) { this.ageMin = min; this.ageMax = max; return this; }
    public ProfileJson city(String v) { this.city = v; return this; }
    public ProfileJson bio(String v) { this.bio = v; return this; }
    public ProfileJson contact(String v) { this.contact = v; return this; }
    public ProfileJson interests(Long... ids) { this.interestIds = List.of(ids); return this; }
    public ProfileJson visible(Boolean v) { this.visible = v; return this; }

    public String build() {
        String ids = interestIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String visiblePart = visible == null ? "" : ", \"visible\": " + visible;
        return """
                {
                  "displayName": "%s",
                  "birthDate": "%s",
                  "gender": "%s",
                  "lookingFor": "%s",
                  "ageMin": %d,
                  "ageMax": %d,
                  "city": "%s",
                  "bio": "%s",
                  "contact": "%s",
                  "interestIds": [%s]%s
                }
                """.formatted(displayName, birthDate, gender, lookingFor, ageMin, ageMax, city, bio, contact, ids, visiblePart);
    }
}
