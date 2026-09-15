package com.matchly.interest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterestRepository extends JpaRepository<Interest, Long> {

    List<Interest> findAllByOrderByCategoryAscNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
