package com.ohpin.experience.repository;

import com.ohpin.experience.entity.Experience;
import com.ohpin.shared.infrastructure.SupabaseGateway;
import com.ohpin.shared.security.Caller;

import java.util.Map;

import org.springframework.stereotype.Repository;

@Repository
public class SupabaseExperienceRepository implements ExperienceRepository {
    private final SupabaseGateway db;

    public SupabaseExperienceRepository(SupabaseGateway db) {
        this.db = db;
    }

    public void submit(Caller c, Experience b) {
        db.rpc(
                c,
                "submit_lecture_experience_response",
                Map.of(
                        "target_code",
                        b.code(),
                        "target_experience",
                        b.experience(),
                        "target_improvement",
                        b.improvement()));
    }
}
