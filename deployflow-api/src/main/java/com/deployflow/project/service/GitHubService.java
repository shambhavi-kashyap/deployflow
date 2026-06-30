package com.deployflow.project.service;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class GitHubService {

    public GitHub connectToGitHub(String personalAccessToken) throws IOException {
        return new GitHubBuilder().withOAuthToken(personalAccessToken).build();
    }

    public Map<String, String> getUserRepositories(String personalAccessToken) throws IOException {
        GitHub github = connectToGitHub(personalAccessToken);
        
        return github.getMyself().getAllRepositories().values().stream()
                .collect(Collectors.toMap(
                        GHRepository::getName,
                        repo -> repo.getHtmlUrl().toString() 
                ));
    }
}