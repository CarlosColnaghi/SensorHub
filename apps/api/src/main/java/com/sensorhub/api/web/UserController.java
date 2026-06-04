package com.sensorhub.api.web;

import com.sensorhub.api.service.UserService;
import com.sensorhub.api.web.dto.UserDtos.CreateUserRequest;
import com.sensorhub.api.web.dto.UserDtos.UpdateUserRequest;
import com.sensorhub.api.web.dto.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = UserResponse.from(users.create(request));
        return ResponseEntity.created(URI.create("/api/v1/users/" + response.uuid())).body(response);
    }

    @GetMapping
    public List<UserResponse> list() {
        return users.list().stream().map(UserResponse::from).toList();
    }

    @GetMapping("/{uuid}")
    public UserResponse get(@PathVariable UUID uuid) {
        return UserResponse.from(users.get(uuid));
    }

    @PutMapping("/{uuid}")
    public UserResponse update(@PathVariable UUID uuid, @Valid @RequestBody UpdateUserRequest request) {
        return UserResponse.from(users.update(uuid, request));
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<Void> delete(@PathVariable UUID uuid) {
        users.delete(uuid);
        return ResponseEntity.noContent().build();
    }
}
