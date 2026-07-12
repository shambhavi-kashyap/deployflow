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
@RequestMapping("/api") // Mapped to /api to handle both projects and deployments
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

    // ==========================================
    // NEW: Fetch deployment history for the UI Sidebar
    // ==========================================
    @GetMapping("/deployments/project/{projectId}")
    public ResponseEntity<List<Deployment>> getProjectDeployments(@PathVariable Long projectId) {
        List<Deployment> deployments = deploymentRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        return ResponseEntity.ok(deployments);
    }

    // ==========================================
    // 1. Trigger the REAL deployment from the React Dashboard
    // ==========================================
    @PostMapping("/projects/{projectId}/deploy")
    public ResponseEntity<String> triggerRealDeployment(@PathVariable Long projectId) {
        // Fetch the project
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Project not found"));

        // Create the pending deployment in your database
        Deployment newDeployment = deploymentService.createPendingDeployment(project, "manual-trigger");

        // Trigger your REAL background build engine
        buildEngine.runPipeline(newDeployment.getId());

        // Return JUST the ID as a string, which is exactly what React's live terminal needs to connect
        return ResponseEntity.ok(String.valueOf(newDeployment.getId()));
    }

    // ==========================================
    // 2. Stream the REAL logs from your Database to the React Terminal
    // ==========================================
    @GetMapping(value = "/deployments/{deploymentId}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRealLogs(@PathVariable Long deploymentId) {
        SseEmitter emitter = new SseEmitter(300000L); // Keep terminal open for up to 5 minutes

        new Thread(() -> {
            try {
                int lastSentLogCount = 0;
                boolean pipelineRunning = true;

                while (pipelineRunning) {
                    // Query the database for the real logs your BuildEngine is saving!
                    List<DeploymentLog> logs = deploymentLogRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);

                    // If new logs have been saved since our last check, send them to React!
                    for (int i = lastSentLogCount; i < logs.size(); i++) {
                        
                        // NOTE: If your log entity uses a different variable name (like getLogText() or getDetails()), change it here!
                        String message = logs.get(i).getMessage(); 
                        
                        emitter.send(message);

                        // Stop streaming if the Build Engine prints either of the final UI keywords
                        if (message.contains("SUCCESS!") || message.contains("FAILED")) {
                            pipelineRunning = false;
                        }
                    }
                    lastSentLogCount = logs.size();

                    if (pipelineRunning) {
                        Thread.sleep(1000); // Wait 1 second before querying the DB again
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