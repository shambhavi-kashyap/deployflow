package com.deployflow.project.repository;

import com.deployflow.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByGithubRepoUrl(String githubRepoUrl);
}