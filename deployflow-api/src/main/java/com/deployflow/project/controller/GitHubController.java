package com.deployflow.project.controller;

import com.deployflow.project.service.GitHubService;
import com.deployflow.user.entity.User;
import com.deployflow.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubService gitHubService;
    private final UserRepository userRepository;

    public GitHubController(GitHubService gitHubService, UserRepository userRepository) {
        this.gitHubService = gitHubService;
        this.userRepository = userRepository;
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connectGitHub(@RequestParam String pat) {
        try {
            Map<String, String> repos = gitHubService.getUserRepositories(pat);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUserEmail = auth.getName();

            User user = userRepository.findByEmail(currentUserEmail)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setGithubToken(pat);
            userRepository.save(user);

            return ResponseEntity.ok(repos);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid GitHub Token or Connection Failed.");
        }
    }
}