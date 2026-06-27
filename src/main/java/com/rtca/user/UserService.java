package com.rtca.user;

import com.rtca.common.dto.PageResponse;
import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.NotFoundException;
import com.rtca.user.dto.UpdateProfileRequest;
import com.rtca.user.dto.UserResponse;
import com.rtca.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(Long id) {
        return UserResponse.from(getById(id));
    }

    @Transactional
    public UserResponse updateProfile(Long id, UpdateProfileRequest request) {
        User user = getById(id);
        user.setDisplayName(request.displayName().trim());
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserSummary getSummary(Long id) {
        return UserSummary.from(getById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserSummary> search(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim().replace("%", "").replace("_", "\\_");
        if (q.isEmpty()) {
            throw new BadRequestException("Search query must not be empty");
        }
        return PageResponse.of(userRepository.search(q, pageable), UserSummary::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listAll(Pageable pageable) {
        return PageResponse.of(userRepository.findAll(pageable), UserResponse::from);
    }

    @Transactional
    public UserResponse changeRole(Long id, Role role) {
        User user = getById(id);
        user.setRole(role);
        return UserResponse.from(user);
    }
}
