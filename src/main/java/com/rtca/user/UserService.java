package com.rtca.user;

import com.rtca.common.dto.PageResponse;
import com.rtca.common.exception.NotFoundException;
import com.rtca.user.dto.UserResponse;
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
