package com.brewmarket.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse create(String email, String nickname) {
        User user = new User(email, nickname);
        User savedUser = userRepository.saveAndFlush(user);

        return UserResponse.from(savedUser);
    }
}
