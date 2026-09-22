package com.ohpin.experience.service;

import com.ohpin.experience.entity.Experience;
import com.ohpin.experience.repository.ExperienceRepository;
import com.ohpin.shared.security.Caller;
import org.springframework.stereotype.Service;

@Service
public class ExperienceService {
    private final ExperienceRepository repository;

    public ExperienceService(ExperienceRepository repository) {
        this.repository = repository;
    }

    public void submit(Caller c, Experience response) {
        repository.submit(c, response);
    }
}
