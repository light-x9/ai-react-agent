package com.light.reactagent.repository;

import com.light.reactagent.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 当前用户的会话列表：置顶优先，然后按更新时间倒序
     */
    @Query("SELECT c FROM Conversation c WHERE c.userId = :userId ORDER BY c.pinned DESC, c.updatedAt DESC")
    List<Conversation> listByUser(String userId);
}
