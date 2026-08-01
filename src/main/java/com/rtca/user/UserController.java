package com.rtca.user;

import com.rtca.auth.AuthUser;
import com.rtca.common.dto.PageResponse;
import com.rtca.common.ids.PublicIds;
import com.rtca.user.dto.UpdateProfileRequest;
import com.rtca.user.dto.UserResponse;
import com.rtca.user.dto.UserSummary;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PublicIds ids;

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser me) {
        return userService.getProfile(me.id());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@AuthenticationPrincipal AuthUser me,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(me.id(), request);
    }

    @GetMapping("/{id}")
    public UserSummary get(@PathVariable UUID id) {
        return userService.getSummary(ids.userId(id));
    }

    @GetMapping("/search")
    public PageResponse<UserSummary> search(@RequestParam("q") String query,
                                            @PageableDefault(size = 20, sort = "username") Pageable pageable) {
        return userService.search(query, pageable);
    }
}
