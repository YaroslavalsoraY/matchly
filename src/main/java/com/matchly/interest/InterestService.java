package com.matchly.interest;

import com.matchly.interest.dto.InterestRequest;
import com.matchly.interest.dto.InterestResponse;

import java.util.List;

/** Справочник интересов: чтение для всех, изменение для администратора. */
public interface InterestService {

    List<InterestResponse> findAll();

    InterestResponse create(InterestRequest request);

    InterestResponse update(Long id, InterestRequest request);

    /** Удаляет интерес; из анкет он пропадает автоматически (каскад в базе). */
    void delete(Long id);
}
