package com.matchly.common.exception;

import org.springframework.http.HttpStatus;

/** Нарушено бизнес-правило: запрос синтаксически верен, но недопустим по смыслу (422). */
public class BusinessRuleException extends MatchlyException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Business rule violated", message);
    }
}
