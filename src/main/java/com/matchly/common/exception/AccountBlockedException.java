package com.matchly.common.exception;

import org.springframework.http.HttpStatus;

/** Учётная запись заблокирована администратором (403). */
public class AccountBlockedException extends MatchlyException {

    public AccountBlockedException() {
        super(HttpStatus.FORBIDDEN, "Forbidden", "Account is blocked");
    }
}
