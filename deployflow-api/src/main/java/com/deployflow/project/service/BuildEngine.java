package com.deployflow.project.service;

import com.deployflow.project.entity.Deployment;
import com.deployflow.project.entity.Project;
import com.deployflow.project.enums.DeploymentStatus;
import com.deployflow.project.repository.DeploymentRepository;
import org.springframework.transaction.annotation.Transactional;
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
    private final DeploymentRepository deploymentRepository; // 1. Added to fetch database info

    public BuildEngine(DeploymentService deploymentService, GitService gitService, DeploymentRepository deploymentRepository) {
        this.deploymentService = deploymentService;
        this.gitService = gitService;
        this.deploymentRepository = deploymentRepository;
    }

    @Async
    @Transactional
    public void runPipeline(Long deploymentId) {
        try {
            log.info("⏳ [Pipeline {}] Initializing...", deploymentId);
            
            Deployment deployment = deploymentRepository.findById(deploymentId)
                    .orElseThrow(() -> new RuntimeException("Deployment not found"));
            Project project = deployment.getProject();

            // --- ADD THIS SAFETY CHECK ---
            if (project == null || project.getGithubRepoUrl() == null || project.getGithubRepoUrl().isEmpty()) {
                throw new RuntimeException("No GitHub URL found in the database for this project!");
            }
            // -----------------------------

            // Phase 1: Cloning (REAL INTEGRATION!)
            deploymentService.updateStatus(deploymentId, DeploymentStatus.CLONING, null);
            deploymentService.saveLog(deploymentId, "Initializing workspace...");
            deploymentService.saveLog(deploymentId, "Cloning repository: " + project.getGithubRepoUrl() + " ...");
            
            // This line actually reaches out to GitHub and downloads the code!
            File workspace = gitService.cloneRepository(project.getGithubRepoUrl());
            
            deploymentService.saveLog(deploymentId, "Successfully cloned to: " + workspace.getAbsolutePath());

            // Phase 2: Building (FULL-STACK COMPILATION!)
            deploymentService.updateStatus(deploymentId, DeploymentStatus.BUILDING, null);
            deploymentService.saveLog(deploymentId, "========================================");
            
            // --- 1. BUILD THE JAVA BACKEND ---
            // Tell Java to look inside the "backend" folder of your downloaded code
            File backendDir = new File(workspace, "deployflow-api"); // <-- Change "backend" if your folder is named differently!
            
            if (backendDir.exists()) {
                deploymentService.saveLog(deploymentId, "Starting Maven Build (Backend)...");
                // Run Maven
                executeCommand(deploymentId, backendDir, "cmd.exe", "/c", "mvnw.cmd", "clean", "package", "-DskipTests");
                deploymentService.saveLog(deploymentId, "Backend build successful!");
            } else {
                deploymentService.saveLog(deploymentId, "⚠️ Warning: Could not find 'backend' folder.");
            }

            deploymentService.saveLog(deploymentId, "----------------------------------------");

            // --- 2. BUILD THE REACT FRONTEND ---
            // Tell Java to look inside the "frontend" folder
            File frontendDir = new File(workspace, "frontend"); // <-- Change "frontend" if your folder is named differently!
            
            if (frontendDir.exists()) {
                deploymentService.saveLog(deploymentId, "Starting React/TS Build (Frontend)...");
                
                deploymentService.saveLog(deploymentId, "Running npm install (this may take a minute)...");
                // Run npm install
                executeCommand(deploymentId, frontendDir, "cmd.exe", "/c", "npm", "install");
                
                deploymentService.saveLog(deploymentId, "Running npm run build...");
                // Run npm run build
                executeCommand(deploymentId, frontendDir, "cmd.exe", "/c", "npm", "run", "build");
                
                deploymentService.saveLog(deploymentId, "Frontend build successful!");
            } else {
                deploymentService.saveLog(deploymentId, "⚠️ Warning: Could not find 'frontend' folder.");
            }

            deploymentService.saveLog(deploymentId, "========================================");

            // Phase 3: Pushing & Deploying (DOCKER CONTAINERIZATION)
            deploymentService.updateStatus(deploymentId, DeploymentStatus.PUSHING_IMAGE, null);
            deploymentService.saveLog(deploymentId, "========================================");
            deploymentService.saveLog(deploymentId, "Packaging Docker Images...");

            // --- 1. DOCKERIZE THE BACKEND ---
            if (backendDir.exists()) {
                deploymentService.saveLog(deploymentId, "Building backend Docker image using local engine...");
                
                // Compiles the Java app into a Docker container tagged with the project name
                // The '.' at the end tells Docker to look inside the backendDir for the Dockerfile
                executeCommand(deploymentId, backendDir, "cmd.exe", "/c", "docker", "build", "-t", project.getName() + "-backend:latest", ".");
                
                deploymentService.saveLog(deploymentId, "Backend Docker image built successfully!");
            }

            // --- 2. DOCKERIZE THE FRONTEND ---
            if (frontendDir.exists()) {
                deploymentService.saveLog(deploymentId, "Building frontend Docker image using local engine...");
                
                // Compiles the React static files into a high-performance web server container
                executeCommand(deploymentId, frontendDir, "cmd.exe", "/c", "docker", "build", "-t", project.getName() + "-frontend:latest", ".");
                
                deploymentService.saveLog(deploymentId, "Frontend Docker image built successfully!");
            }

            // Simulating the final cloud infrastructure/Kubernetes deployment step
            deploymentService.updateStatus(deploymentId, DeploymentStatus.DEPLOYING, null);
            deploymentService.saveLog(deploymentId, "========================================");
            deploymentService.saveLog(deploymentId, "Applying deployment manifests to cluster (Simulated)...");

            // Phase 4: Success
            String mockLiveUrl = "https://" + project.getName() + ".deployflow.app";
            
            deploymentService.saveLog(deploymentId, "Routing traffic to " + mockLiveUrl);
            deploymentService.updateStatus(deploymentId, DeploymentStatus.SUCCESS, mockLiveUrl);
            log.info("✅ [Pipeline {}] SUCCESS! App is live at: {}", deploymentId, mockLiveUrl);

        } catch (Exception e) {
            log.error("❌ [Pipeline {}] FAILED: {}", deploymentId, e.getMessage(), e);
            deploymentService.failDeployment(deploymentId, "Pipeline interrupted: " + e.getMessage());
        }
    }

    /**
     * Executes a terminal command in the given directory and streams the output to the deployment logs.
     */
    private void executeCommand(Long deploymentId, File workingDirectory, String... command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory);
        builder.redirectErrorStream(true); // Merge standard output and error output

        Process process = builder.start();

        // Read the live terminal output line-by-line and save it to PostgreSQL!
        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                deploymentService.saveLog(deploymentId, line);
            }
        }

        // Wait for the command to finish and check if it crashed
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code: " + exitCode);
        }
    }
}