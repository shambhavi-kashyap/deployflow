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

    @GetMapping
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Project project) {
        if (project.getBranch() == null || project.getBranch().isEmpty()) {
            project.setBranch("main");
        }

        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        project.setUser(users.get(0));

        Project savedProject = projectRepository.save(project);
        return ResponseEntity.ok(savedProject);
    }
}