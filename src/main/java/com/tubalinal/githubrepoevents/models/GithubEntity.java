package com.tubalinal.githubrepoevents.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class GithubEntity {
    static final Logger LOGGER = LoggerFactory.getLogger(GithubEntity.class.getName());

    public String toString() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writeValueAsString(this);
        } catch (Exception ex){
            return super.toString();
        }
    }
}
