package com.deployflow.project.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public class GitHubPushEvent {
    private String ref;
    private RepositoryDto repository;
    
    @JsonProperty("head_commit")
    private HeadCommitDto headCommit;

    public String getRef() { return ref; }
    public void setRef(String ref) { this.ref = ref; }
    public RepositoryDto getRepository() { return repository; }
    public void setRepository(RepositoryDto repository) { this.repository = repository; }
    public HeadCommitDto getHeadCommit() { return headCommit; }
    public void setHeadCommit(HeadCommitDto headCommit) { this.headCommit = headCommit; }
}