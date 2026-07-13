package com.deployflow.project.service;

import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class GitService {

    public File cloneRepository(String repoUrl) {
        try {
            Path tempDir = Files.createTempDirectory("deployflow-workspace-");
            File workspace = tempDir.toFile();

            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(workspace)
                    .call();

            return workspace;
            
        } catch (Exception e) {
            throw new RuntimeException("Git clone failed: " + e.getMessage(), e);
        }
    }
}