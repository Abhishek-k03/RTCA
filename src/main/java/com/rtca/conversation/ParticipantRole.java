package com.rtca.conversation;

public enum ParticipantRole {
    OWNER,
    ADMIN,
    MEMBER;

    public boolean canManageMembers() {
        return this == OWNER || this == ADMIN;
    }
}
