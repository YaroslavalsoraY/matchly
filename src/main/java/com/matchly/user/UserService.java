package com.matchly.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Административные операции над аккаунтами. */
public interface UserService {

    User getById(Long id);

    /** Поиск по части email; пустой запрос возвращает всех. */
    Page<User> search(String query, Pageable pageable);

    /** Блокирует аккаунт. Администратор не может заблокировать сам себя. */
    User block(Long id, Long actorId);

    User unblock(Long id);

    /** Удаляет аккаунт вместе со всеми связанными данными. Администратор не может удалить сам себя. */
    void delete(Long id, Long actorId);
}
