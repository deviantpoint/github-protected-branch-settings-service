package com.tubalinal.githubrepoevents.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GithubEvent extends GithubEntity {
    private String action;
    private GithubRepo repository;

    public String getAction() {
        if (action == null) {
            return "";
        }

        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public GithubRepo getRepository() {
        return repository;
    }

    public void setRepository(GithubRepo repository) {
        this.repository = repository;
    }
}
