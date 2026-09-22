package com.ohpin.experience.repository;

import com.ohpin.experience.entity.Experience;
import com.ohpin.shared.security.Caller;

public interface ExperienceRepository {
    void submit(Caller c, Experience response);
}
