package com.light.reactagent.repository;

import com.light.reactagent.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    void deleteByConversationId(Long conversationId);
}
