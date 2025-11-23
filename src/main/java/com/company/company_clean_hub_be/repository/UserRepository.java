package com.company.company_clean_hub_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.company_clean_hub_be.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
