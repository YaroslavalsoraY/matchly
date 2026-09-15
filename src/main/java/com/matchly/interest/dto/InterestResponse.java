package com.matchly.interest.dto;

import com.matchly.interest.Interest;
import com.matchly.interest.InterestCategory;

/** Элемент справочника интересов. */
public record InterestResponse(Long id, String name, InterestCategory category) {

    public static InterestResponse from(Interest interest) {
        return new InterestResponse(interest.getId(), interest.getName(), interest.getCategory());
    }
}
