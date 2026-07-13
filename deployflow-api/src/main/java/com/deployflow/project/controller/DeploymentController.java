package com.deployflow.project.controller;

import com.deployflow.project.entity.Deployment;
import com.deployflow.project.entity.DeploymentLog;
import com.deployflow.project.entity.Project;
import com.deployflow.project.repository.DeploymentLogRepository;
import com.deployflow.project.repository.DeploymentRepository;
import com.deployflow.project.repository.ProjectRepository;
import com.deployflow.project.service.DeploymentService;
import com.deployflow.project.service.BuildEngine;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api") 
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
        this.projectRepository = projectRepository;
        this.deploymentService = deploymentService;
        this.buildEngine = buildEngine;
    }

    @GetMapping("/deployments/project/{projectId}")
    public ResponseEntity<List<Deployment>> getProjectDeployments(@PathVariable Long projectId) {
        List<Deployment> deployments = deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        return ResponseEntity.ok(deployments);
    }

    @PostMapping("/projects/{projectId}/deploy")
    public ResponseEntity<String> triggerRealDeployment(@PathVariable Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        Deployment newDeployment = deploymentService.createPendingDeployment(project, "manual-trigger");

        buildEngine.runPipeline(newDeployment.getId());

        return ResponseEntity.ok(String.valueOf(newDeployment.getId()));
    }

    @GetMapping(value = "/deployments/{deploymentId}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRealLogs(@PathVariable Long deploymentId) {
        SseEmitter emitter = new SseEmitter(300000L); 

        new Thread(() -> {
            try {
                int lastSentLogCount = 0;
                boolean pipelineRunning = true;

                while (pipelineRunning) {
                    List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);

                    for (int i = lastSentLogCount; i < logs.size(); i++) {
                        
                        String message = logs.get(i).getMessage(); 
                        
                        emitter.send(message);

                        if (message.contains("SUCCESS!") || message.contains("FAILED")) {
                            pipelineRunning = false;
                        }
                    }
                    lastSentLogCount = logs.size();

                    if (pipelineRunning) {
                        Thread.sleep(1000); 
                    }
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send("[ERROR] FAILED: Log stream disconnected.");
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        }).start();

        return emitter;
    }
}