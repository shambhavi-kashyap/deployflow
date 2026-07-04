package com.deployflow.project.enums;

public enum DeploymentStatus {
    PENDING,
    CLONING,
    BUILDING,
    PUSHING_IMAGE,
    DEPLOYING,
    SUCCESS,
    FAILED
}