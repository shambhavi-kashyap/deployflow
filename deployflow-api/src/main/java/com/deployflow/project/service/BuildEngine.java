package com.deployflow.project.service;

import com.deployflow.project.entity.Deployment;
import com.deployflow.project.entity.Project;
import com.deployflow.project.enums.DeploymentStatus;
import com.deployflow.project.repository.DeploymentLogRepository;
import com.deployflow.project.repository.DeploymentRepository;
import com.deployflow.project.repository.ProjectRepository; // <-- ADDED
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class BuildEngine {

    private static final Logger log = LoggerFactory.getLogger(BuildEngine.class);
    private final DeploymentService deploymentService;
    private final GitService gitService;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository deploymentLogRepository;
    private final ProjectRepository projectRepository; // <-- ADDED
    
    public BuildEngine(DeploymentService deploymentService, 
                       GitService gitService, 
                       DeploymentRepository deploymentRepository, 
                       DeploymentLogRepository deploymentLogRepository,
                       ProjectRepository projectRepository) { // <-- ADDED
        this.deploymentService = deploymentService;
        this.gitService = gitService;
        this.deploymentRepository = deploymentRepository;
        this.deploymentLogRepository = deploymentLogRepository;
        this.projectRepository = projectRepository;
    }

    @Async
    public void runPipeline(Long deploymentId) {
        try {
            log.info("⏳ [Pipeline {}] Initializing...", deploymentId);
            
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new RuntimeException("Deployment not found"));
            
            // === THE FIX ===
            // We explicitly grab the Project ID from the proxy, then do a quick, dedicated fetch 
            // so we have all the data we need before moving on!
            Long projectId = deployment.getProject().getId();
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            if (project.getGithubRepoUrl() == null || project.getGithubRepoUrl().isEmpty()) {
                throw new RuntimeException("No GitHub URL found in the database for this project!");
            }

            String branch = (project.getBranch() != null && !project.getBranch().isEmpty()) ? project.getBranch() : "main";
            // Sanitize project name for Docker tags (no spaces, lowercase)
            String safeProjectName = project.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");

            // Phase 1: Cloning
            deploymentService.updateStatus(deploymentId, DeploymentStatus.CLONING, null);
            deploymentService.saveLog(deploymentId, "Initializing workspace...");
            deploymentService.saveLog(deploymentId, "Cloning repository: " + project.getGithubRepoUrl() + " on branch: " + branch + "...");
            
            File workspace = gitService.cloneRepository(project.getGithubRepoUrl());
            
            // Check out the specific branch
            executeCommand(deploymentId, workspace, "cmd.exe", "/c", "git", "checkout", branch);
            
            deploymentService.saveLog(deploymentId, "Successfully cloned and checked out to: " + workspace.getAbsolutePath());

            // ==========================================
            // Phase 2 & Phase 3: Multi-Image Generation
            // ==========================================
            deploymentService.updateStatus(deploymentId, DeploymentStatus.BUILDING, null);
            deploymentService.saveLog(deploymentId, "========================================");
            deploymentService.saveLog(deploymentId, "Analyzing repository structure...");

            File backendDir = new File(workspace, "deployflow-api");
            File frontendDir = new File(workspace, "frontend");

            // SCENARIO A: Monorepo (Both folders exist)
            if (backendDir.exists() && frontendDir.exists()) {
                deploymentService.saveLog(deploymentId, "🌟 Monorepo Detected! Generating 2 separate Docker images...");

                // --- 1. PROCESS BACKEND (API) ---
                deploymentService.saveLog(deploymentId, "--- [1/2] Processing Backend (Java) ---");
                if (new File(backendDir, "pom.xml").exists()) {
                    executeCommand(deploymentId, backendDir, "cmd.exe", "/c", "mvnw.cmd", "clean", "package", "-DskipTests");
                    
                    deploymentService.updateStatus(deploymentId, DeploymentStatus.PUSHING_IMAGE, null);
                    if (new File(backendDir, "Dockerfile").exists()) {
                        deploymentService.saveLog(deploymentId, "Building Backend Docker Image...");
                        executeCommand(deploymentId, backendDir, "cmd.exe", "/c", "docker", "build", "-t", safeProjectName + "-api:latest", ".");
                        deploymentService.saveLog(deploymentId, "✅ Backend image built: " + safeProjectName + "-api:latest");
                    } else {
                        throw new RuntimeException("Missing Dockerfile in /deployflow-api!");
                    }
                }

                // --- 2. PROCESS FRONTEND (UI) ---
                deploymentService.updateStatus(deploymentId, DeploymentStatus.BUILDING, null);
                deploymentService.saveLog(deploymentId, "--- [2/2] Processing Frontend (React) ---");
                if (new File(frontendDir, "package.json").exists()) {
                    executeCommand(deploymentId, frontendDir, "cmd.exe", "/c", "npm", "install");
                    executeCommand(deploymentId, frontendDir, "cmd.exe", "/c", "npm", "run", "build");
                    
                    deploymentService.updateStatus(deploymentId, DeploymentStatus.PUSHING_IMAGE, null);
                    if (new File(frontendDir, "Dockerfile").exists()) {
                        deploymentService.saveLog(deploymentId, "Building Frontend Docker Image...");
                        executeCommand(deploymentId, frontendDir, "cmd.exe", "/c", "docker", "build", "-t", safeProjectName + "-ui:latest", ".");
                        deploymentService.saveLog(deploymentId, "✅ Frontend image built: " + safeProjectName + "-ui:latest");
                    } else {
                        throw new RuntimeException("Missing Dockerfile in /frontend!");
                    }
                }
            } 
            // SCENARIO B: Standalone Root Repositories (Python, Angular, Single Java apps)
            else {
                deploymentService.saveLog(deploymentId, "Standalone Repository Detected.");
                
                if (new File(workspace, "pom.xml").exists()) {
                    deploymentService.saveLog(deploymentId, "Detected standalone Java Project");
                    executeCommand(deploymentId, workspace, "cmd.exe", "/c", "mvnw.cmd", "clean", "package", "-DskipTests");
                } else if (new File(workspace, "package.json").exists()) {
                    deploymentService.saveLog(deploymentId, "Detected standalone Node Project");
                    executeCommand(deploymentId, workspace, "cmd.exe", "/c", "npm", "install");
                    executeCommand(deploymentId, workspace, "cmd.exe", "/c", "npm", "run", "build");
                } else if (new File(workspace, "requirements.txt").exists()) {
                    deploymentService.saveLog(deploymentId, "Detected standalone Python Project");
                } else {
                    throw new RuntimeException("Could not detect standard build files. Aborting.");
                }

                deploymentService.updateStatus(deploymentId, DeploymentStatus.PUSHING_IMAGE, null);
                if (new File(workspace, "Dockerfile").exists()) {
                    deploymentService.saveLog(deploymentId, "Building Standalone Docker Image...");
                    executeCommand(deploymentId, workspace, "cmd.exe", "/c", "docker", "build", "-t", safeProjectName + ":latest", ".");
                    deploymentService.saveLog(deploymentId, "✅ Docker image built successfully!");
                } else {
                    throw new RuntimeException("No Dockerfile found. Aborting deployment.");
                }
            }
            deploymentService.saveLog(deploymentId, "========================================");
            
            // Phase 4: Success
            String mockLiveUrl = "https://" + safeProjectName + ".deployflow.app";
            
            deploymentService.saveLog(deploymentId, "Routing traffic to " + mockLiveUrl);
            deploymentService.updateStatus(deploymentId, DeploymentStatus.SUCCESS, mockLiveUrl);
            
            deploymentService.saveLog(deploymentId, "SUCCESS! App is live at: " + mockLiveUrl);
            log.info("✅ [Pipeline {}] SUCCESS! App is live at: {}", deploymentId, mockLiveUrl);

        } catch (Exception e) {
            deploymentService.saveLog(deploymentId, "FAILED: Pipeline interrupted: " + e.getMessage());
            
            log.error("❌ [Pipeline {}] FAILED: {}", deploymentId, e.getMessage(), e);
            deploymentService.failDeployment(deploymentId, "Pipeline interrupted: " + e.getMessage());
        }
    }

    private void executeCommand(Long deploymentId, File workingDirectory, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory);
        builder.redirectErrorStream(true);

        Process process = builder.start();

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                deploymentService.saveLog(deploymentId, line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
}