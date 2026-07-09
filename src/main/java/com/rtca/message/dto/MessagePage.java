package com.rtca.message.dto;

import java.util.List;

/**
 * Keyset page. Pass nextCursor as "before" (or "after" when syncing forward)
 * to fetch the next page.
 */
public record MessagePage(
        List<MessageResponse> items,
        Long nextCursor,
        boolean hasMore
) {
}
