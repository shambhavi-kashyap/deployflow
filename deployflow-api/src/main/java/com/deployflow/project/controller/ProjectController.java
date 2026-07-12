package com.deployflow.project.controller;

import com.deployflow.project.entity.Project;
import com.deployflow.project.repository.ProjectRepository;
import com.deployflow.user.entity.User;
import com.deployflow.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public ProjectController(ProjectRepository projectRepository, UserRepository userRepository) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    // 1. List all registered projects on the dashboard
    @GetMapping
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    // 2. Register a new GitHub repository
    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project) {
        // Enforce default branch to "main" if left empty by the frontend
        if (project.getBranch() == null || project.getBranch().isEmpty()) {
            project.setBranch("main");
        }

        // Since your Project entity requires a User (nullable = false),
        // we fetch the first available user in the system to link it.
        // (Later, this can be swapped with the actual logged-in user context)
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        project.setUser(users.get(0));

        Project savedProject = projectRepository.save(project);
        return ResponseEntity.ok(savedProject);
    }
}