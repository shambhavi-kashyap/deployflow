package com.deployflow.project.dto.webhook;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepositoryDto {
    @JsonProperty("html_url")
    private String htmlUrl;

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }
}