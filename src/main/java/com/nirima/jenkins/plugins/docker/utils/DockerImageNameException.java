package com.nirima.jenkins.plugins.docker.utils;

/**
 * @author Joshua Griffiths 10/04/2015
 */
public class DockerImageNameException extends IllegalArgumentException {
    public DockerImageNameException(String message) {
        super(message);
    }
}