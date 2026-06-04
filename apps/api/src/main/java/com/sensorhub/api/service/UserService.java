package com.sensorhub.api.service;

import com.sensorhub.api.domain.AppUser;
import com.sensorhub.api.repository.UserRepository;
import com.sensorhub.api.web.dto.UserDtos.CreateUserRequest;
import com.sensorhub.api.web.dto.UserDtos.UpdateUserRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional
    public AppUser create(CreateUserRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ConflictException("email already exists");
        }
        AppUser user = new AppUser();
        user.setName(request.name());
        user.setEmail(request.email());
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        return users.findAll();
    }

    @Transactional(readOnly = true)
    public AppUser get(UUID uuid) {
        return users.findById(uuid).orElseThrow(() -> new ResourceNotFoundException("user not found"));
    }

    @Transactional
    public AppUser update(UUID uuid, UpdateUserRequest request) {
        AppUser user = get(uuid);
        users.findByEmail(request.email())
                .filter(existing -> !existing.getUuid().equals(uuid))
                .ifPresent(existing -> {
                    throw new ConflictException("email already exists");
                });
        user.setName(request.name());
        user.setEmail(request.email());
        return user;
    }

    @Transactional
    public void delete(UUID uuid) {
        if (!users.existsById(uuid)) {
            throw new ResourceNotFoundException("user not found");
        }
        users.deleteById(uuid);
    }
}
