package com.rtca.user;

import com.rtca.common.dto.PageResponse;
import com.rtca.common.ids.PublicIds;
import com.rtca.user.dto.ChangeRoleRequest;
import com.rtca.user.dto.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final PublicIds ids;

    @GetMapping
    public PageResponse<UserResponse> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
                                           Pageable pageable) {
        return userService.listAll(pageable);
    }

    @PatchMapping("/{id}/role")
    public UserResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest request) {
        return userService.changeRole(ids.userId(id), request.role());
    }
}
