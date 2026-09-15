package com.matchly.interest;

import com.matchly.common.exception.ConflictException;
import com.matchly.common.exception.NotFoundException;
import com.matchly.interest.dto.InterestRequest;
import com.matchly.interest.dto.InterestResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterestServiceImpl implements InterestService {

    private final InterestRepository interestRepository;

    @Override
    @Transactional(readOnly = true)
    public List<InterestResponse> findAll() {
        return interestRepository.findAllByOrderByCategoryAscNameAsc().stream().map(InterestResponse::from).toList();
    }

    @Override
    @Transactional
    public InterestResponse create(InterestRequest request) {
        ensureNameIsFree(request.name(), null);
        Interest interest = interestRepository.save(new Interest(request.name(), request.category()));
        log.info("Interest created: id={}, name={}", interest.getId(), interest.getName());
        return InterestResponse.from(interest);
    }

    @Override
    @Transactional
    public InterestResponse update(Long id, InterestRequest request) {
        Interest interest = getById(id);
        ensureNameIsFree(request.name(), interest);
        interest.rename(request.name(), request.category());
        log.info("Interest updated: id={}, name={}", id, interest.getName());
        return InterestResponse.from(interest);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Interest interest = getById(id);
        interestRepository.delete(interest);
        log.info("Interest deleted: id={}, name={}", id, interest.getName());
    }

    private Interest getById(Long id) {
        return interestRepository.findById(id).orElseThrow(() -> new NotFoundException("Interest", id));
    }

    private void ensureNameIsFree(String name, Interest current) {
        boolean sameName = current != null && current.getName().equalsIgnoreCase(name.trim());
        if (!sameName && interestRepository.existsByNameIgnoreCase(name.trim())) {
            throw new ConflictException("Interest with this name already exists");
        }
    }
}
