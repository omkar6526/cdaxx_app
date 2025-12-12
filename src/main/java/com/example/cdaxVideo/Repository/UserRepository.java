package com.example.cdaxVideo.Repository;

import com.example.cdaxVideo.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByMobile(String mobile); // ✅ Added for mobile lookup

    boolean existsByEmail(String email);

    boolean existsByMobile(String mobile);
}