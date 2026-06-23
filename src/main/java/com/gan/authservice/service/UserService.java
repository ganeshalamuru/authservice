package com.gan.authservice.service;

import com.gan.authservice.model.security.User;
import com.gan.authservice.repository.UserRepository;
import com.gan.authservice.service.dto.UserResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        List<User> allUsers = userRepository.findAll();
        return allUsers.stream().map(UserResponse::createResponse).toList();
    }

}
