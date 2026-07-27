package com.rtca.message;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** A message one user deleted for themselves only. */
@Entity
@Table(name = "message_hides")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageHide {

    @EmbeddedId
    private Key id;

    @Embeddable
    public record Key(
            @Column(name = "user_id") Long userId,
            @Column(name = "message_id") Long messageId
    ) implements Serializable {
    }
}
