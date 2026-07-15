package com.light.reactagent.repository;

import com.light.reactagent.entity.PersonaProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户画像 Repository。
 * <p>
 * 主键是 userId，查询全部按 findById(userId) 走。
 */
@Repository
public interface PersonaProfileRepository extends JpaRepository<PersonaProfile, Long> {

    /**
     * 按 userId 查询画像（主键查询的语义别名，代码更清晰）
     */
    default Optional<PersonaProfile> findByUserId(Long userId) {
        return findById(userId);
    }
}
