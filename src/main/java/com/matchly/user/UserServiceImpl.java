package com.matchly.user;

import com.matchly.common.exception.BusinessRuleException;
import com.matchly.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> search(String query, Pageable pageable) {
        if (query == null || query.isBlank()) {
            return userRepository.findAll(pageable);
        }
        return userRepository.findByEmailContainingIgnoreCase(query.trim(), pageable);
    }

    @Override
    @Transactional
    public User block(Long id, Long actorId) {
        if (id.equals(actorId)) {
            throw new BusinessRuleException("You cannot block your own account");
        }
        User user = getById(id);
        user.block();
        log.info("User id={} blocked by admin id={}", id, actorId);
        return user;
    }

    @Override
    @Transactional
    public User unblock(Long id) {
        User user = getById(id);
        user.unblock();
        log.info("User id={} unblocked", id);
        return user;
    }

    @Override
    @Transactional
    public void delete(Long id, Long actorId) {
        if (id.equals(actorId)) {
            throw new BusinessRuleException("You cannot delete your own account here");
        }
        User user = getById(id);
        userRepository.delete(user);
        log.info("User id={} deleted by admin id={}", id, actorId);
    }
}
