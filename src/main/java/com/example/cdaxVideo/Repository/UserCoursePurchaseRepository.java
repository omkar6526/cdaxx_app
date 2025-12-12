package com.example.cdaxVideo.Repository;


import com.example.cdaxVideo.Entity.UserCoursePurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserCoursePurchaseRepository extends JpaRepository<UserCoursePurchase, Long> {

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    List<UserCoursePurchase> findByUserId(Long userId);
}
