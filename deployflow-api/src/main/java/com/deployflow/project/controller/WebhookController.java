package com.deployflow.project.controller;

import com.deployflow.common.exception.ProjectNotFoundException;
import com.deployflow.project.dto.webhook.GitHubPushEvent;
import com.deployflow.project.entity.Deployment;
import com.deployflow.project.entity.Project;
import com.deployflow.project.repository.ProjectRepository;
import com.deployflow.project.service.BuildEngine;
import com.deployflow.project.service.DeploymentService;
import com.deployflow.project.service.WebhookSecurityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final ProjectRepository projectRepository;
    private final DeploymentService deploymentService;
    private final BuildEngine buildEngine;
    private final WebhookSecurityService webhookSecurityService;
    private final ObjectMapper objectMapper;

    public WebhookController(ProjectRepository projectRepository, 
                             DeploymentService deploymentService, 
                             BuildEngine buildEngine,
                             WebhookSecurityService webhookSecurityService,
                             ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.deploymentService = deploymentService;
        this.buildEngine = buildEngine;
        this.webhookSecurityService = webhookSecurityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestBody String rawPayload, 
            @RequestHeader("X-GitHub-Event") String eventType,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        
        if (!webhookSecurityService.verifySignature(rawPayload, signature)) {
            log.error("🚨 Unauthorized webhook attempt! Signature mismatch.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid signature");
        }

        if ("ping".equals(eventType)) {
            log.info("Received GitHub ping event");
            return ResponseEntity.ok("Ping received");
        }
        
        if (!"push".equals(eventType)) {
            log.info("⏭️ Ignored unsupported event type: {}", eventType);
            return ResponseEntity.ok("Ignored");
        }
        
        try {
            GitHubPushEvent payload = objectMapper.readValue(rawPayload, GitHubPushEvent.class);
            
            String repoUrl = payload.getRepository().getHtmlUrl();
            String branch = payload.getRef().replace("refs/heads/", "");
            String commitSha = payload.getHeadCommit() != null ? payload.getHeadCommit().getId() : "unknown-sha";

            Project project = projectRepository.findByGithubRepoUrl(repoUrl)
                    .orElseThrow(() -> new ProjectNotFoundException("Project not found: " + repoUrl));

            if (!project.getBranch().equals(branch)) {
                return ResponseEntity.ok("Ignored branch");
            }

            Deployment deployment = deploymentService.createPendingDeployment(project, commitSha);
            buildEngine.runPipeline(deployment.getId());

            return ResponseEntity.ok("Webhook processed");

        } catch (Exception e) {
            log.error("Failed to process webhook payload", e);
            return ResponseEntity.badRequest().body("Invalid payload");
        }
    }
}