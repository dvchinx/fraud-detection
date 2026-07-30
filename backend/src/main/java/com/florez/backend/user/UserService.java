package com.florez.backend.user;

import com.florez.backend.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserResponse getById(UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado: " + id));
    }

    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Usuario no encontrado: " + id);
        }
        userRepository.deleteById(id);
    }
}
