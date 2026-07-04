package com.deployflow.project.service;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class GitService {

    /**
     * Clones a GitHub repository into a temporary workspace folder on your hard drive.
     * @param repoUrl The HTTPS URL of the GitHub repository.
     * @return The File object representing the folder where the code was downloaded.
     */
    public File cloneRepository(String repoUrl) {
        try {
            // 1. Create a unique, temporary workspace folder on your computer
            Path tempDir = Files.createTempDirectory("deployflow-workspace-");
            File workspace = tempDir.toFile();

            // 2. Instruct JGit to clone the repository into that specific folder
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(workspace)
                    .call();

            return workspace;
            
        } catch (Exception e) {
            // If the repository is private or the URL is broken, it will fail here
            throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
        }
    }
}