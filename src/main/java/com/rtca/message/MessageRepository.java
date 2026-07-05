package com.rtca.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    Optional<Message> findBySenderIdAndClientMessageId(Long senderId, String clientMessageId);
}
