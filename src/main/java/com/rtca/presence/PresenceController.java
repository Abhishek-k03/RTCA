package com.rtca.presence;

import com.rtca.common.ids.PublicIds;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/api/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final PublicIds ids;

    @GetMapping
    public Collection<PresenceStatus> get(@RequestParam @Size(min = 1, max = 200) Set<UUID> userIds) {
        return presenceService.statuses(ids.userIds(userIds)).values();
    }
}
