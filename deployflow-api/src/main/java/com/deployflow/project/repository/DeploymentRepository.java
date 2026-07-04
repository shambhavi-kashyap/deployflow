package com.deployflow.project.repository;

import com.deployflow.project.entity.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    List<Deployment> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}