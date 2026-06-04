package com.sensorhub.api.service;

import com.sensorhub.api.domain.SensorEnvironment;
import com.sensorhub.api.repository.EnvironmentRepository;
import com.sensorhub.api.repository.UserRepository;
import com.sensorhub.api.web.dto.EnvironmentDtos.CreateEnvironmentRequest;
import com.sensorhub.api.web.dto.EnvironmentDtos.UpdateEnvironmentRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environments;
    private final UserRepository users;

    public EnvironmentService(EnvironmentRepository environments, UserRepository users) {
        this.environments = environments;
        this.users = users;
    }

    @Transactional
    public SensorEnvironment create(CreateEnvironmentRequest request) {
        if (!users.existsById(request.userUuid())) {
            throw new ResourceNotFoundException("user not found");
        }
        SensorEnvironment environment = new SensorEnvironment();
        environment.setUserUuid(request.userUuid());
        environment.setName(request.name());
        return environments.save(environment);
    }

    @Transactional(readOnly = true)
    public List<SensorEnvironment> list() {
        return environments.findAll();
    }

    @Transactional(readOnly = true)
    public SensorEnvironment get(UUID uuid) {
        return environments.findById(uuid)
                .orElseThrow(() -> new ResourceNotFoundException("environment not found"));
    }

    @Transactional
    public SensorEnvironment update(UUID uuid, UpdateEnvironmentRequest request) {
        SensorEnvironment environment = get(uuid);
        environment.setName(request.name());
        return environment;
    }

    @Transactional
    public void delete(UUID uuid) {
        if (!environments.existsById(uuid)) {
            throw new ResourceNotFoundException("environment not found");
        }
        environments.deleteById(uuid);
    }
}
