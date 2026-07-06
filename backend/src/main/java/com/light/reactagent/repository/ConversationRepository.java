package com.light.reactagent.repository;

import com.light.reactagent.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserIdOrderByUpdatedAtDesc(String userId);
}
