package com.deployflow.project.service;

import com.deployflow.project.entity.Deployment;
import com.deployflow.project.entity.DeploymentLog;
import com.deployflow.project.entity.Project;
import com.deployflow.project.enums.DeploymentStatus;
import com.deployflow.project.repository.DeploymentLogRepository;
import com.deployflow.project.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentService {
    
    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository deploymentLogRepository; 

    public DeploymentService(DeploymentRepository deploymentRepository, DeploymentLogRepository deploymentLogRepository) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogRepository = deploymentLogRepository;
    }

    @Transactional
    public Deployment createPendingDeployment(Project project, String commitSha) {
        Deployment deployment = new Deployment();
        deployment.setProject(project);
        deployment.setCommitSha(commitSha);
        deployment.setStatus(DeploymentStatus.PENDING);
        
        log.info("Created PENDING deployment for project: {}", project.getName());
        return deploymentRepository.save(deployment);
    }

    @Transactional
    public void updateStatus(Long deploymentId, DeploymentStatus status, String liveUrl) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));
        
        deployment.setStatus(status);
        if (liveUrl != null) {
            deployment.setLiveUrl(liveUrl);
        }
        deploymentRepository.save(deployment);
        log.info("Updated deployment {} to status: {}", deploymentId, status);
    }

    @Transactional
    public void failDeployment(Long deploymentId, String errorLogs) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));
        
        deployment.setStatus(DeploymentStatus.FAILED);
        deployment.setErrorLogs(errorLogs);
        deploymentRepository.save(deployment);
        log.error("Deployment {} FAILED. Logs: {}", deploymentId, errorLogs);
    }
    
    @Transactional(readOnly = true)
    public String getProjectNameForDeployment(Long deploymentId) {
         Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));
         
         // Extract the string HERE while the @Transactional connection is still active!
         return deployment.getProject().getName(); 
    }
    @Transactional
    public void saveLog(Long deploymentId, String message) {
        Deployment deployment = deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new RuntimeException("Deployment not found: " + deploymentId));

        DeploymentLog logEntry = new DeploymentLog();
        logEntry.setDeployment(deployment);
        logEntry.setMessage(message);
        deploymentLogRepository.save(logEntry);
    }
}