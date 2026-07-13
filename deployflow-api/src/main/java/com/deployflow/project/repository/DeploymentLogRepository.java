package com.deployflow.project.repository;

import com.deployflow.project.entity.DeploymentLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, Long> {
    List<DeploymentLog> findByDeploymentIdOrderByCreatedAtAsc(Long deploymentId);
}