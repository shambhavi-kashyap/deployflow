package com.deployflow.project.controller;

import com.deployflow.project.entity.Deployment;
import com.deployflow.project.entity.DeploymentLog;
import com.deployflow.project.entity.Project; // NEW
import com.deployflow.project.repository.DeploymentLogRepository;
import com.deployflow.project.repository.DeploymentRepository;
import com.deployflow.project.repository.ProjectRepository; // NEW
import com.deployflow.project.service.DeploymentService; // NEW
import com.deployflow.project.service.BuildEngine; // NEW
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/deployments")
public class DeploymentController {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final ProjectRepository projectRepository; 
    private final DeploymentService deploymentService; 
    private final BuildEngine buildEngine;

    public DeploymentController(DeploymentRepository deploymentRepository, 
                                DeploymentLogRepository deploymentLogRepository,
                                ProjectRepository projectRepository, 
                                DeploymentService deploymentService, 
                                BuildEngine buildEngine) {
                                    
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogRepository = deploymentLogRepository;
        
        // 3. ASSIGN THEM TO THE CLASS VARIABLES
        this.projectRepository = projectRepository; 
        this.deploymentService = deploymentService; 
        this.buildEngine = buildEngine; 
    }

    // 1. Get deployment history for a specific project
    @GetMapping("/project/{projectId}")
    public ResponseEntity<List<Deployment>> getProjectDeployments(@PathVariable Long projectId) {
        List<Deployment> deployments = deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        return ResponseEntity.ok(deployments);
    }

    // 2. Get the real-time logs for a specific deployment
    @GetMapping("/{deploymentId}/logs")
    public ResponseEntity<List<DeploymentLog>> getDeploymentLogs(@PathVariable Long deploymentId) {
        List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
        return ResponseEntity.ok(logs);
    }

    // Trigger a new manual deployment from the React dashboard
    @PostMapping("/project/{projectId}")
    public ResponseEntity<Deployment> triggerManualDeployment(@PathVariable Long projectId) {
        // 1. Fetch the project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));
        
        // 2. Create the pending deployment in the database
        Deployment newDeployment = deploymentService.createPendingDeployment(project, "manual-trigger");
        
        // 3. Trigger the background build engine asynchronously
        buildEngine.runPipeline(newDeployment.getId());
        
        // 4. Return the new deployment (which contains the new ID!) to React
        return ResponseEntity.ok(newDeployment);
    }
}