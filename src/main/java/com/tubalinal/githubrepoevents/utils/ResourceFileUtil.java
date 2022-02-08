package com.tubalinal.githubrepoevents.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ResourceFileUtil {
    static final Logger LOGGER = LoggerFactory.getLogger(ResourceFileUtil.class.getName());
    static final String REPO_PROTECTION_TEMPLATE_FILE = "/repo_protection_template.json";
    static final String REPO_ISSUE_BODY_TEMPLATE_FILE = "/repo_protection_issue.md";
    static final String REPO_ISSUE_TEMPLATE_FILE = "/repo_protection_issue_template.json";
    static final String REPO_DEFAULT_README_FILE = "/repo_default_readme.md";

    public static String getProtectionTemplateFileContents(){
        return ResourceFileUtil.getResourceFileContents(REPO_PROTECTION_TEMPLATE_FILE);
    }

    public static String getRepoIssueTemplateFileBodyContents() {
        return ResourceFileUtil.getResourceFileContents(REPO_ISSUE_BODY_TEMPLATE_FILE);
    }

    public static String getRepoIssueTemplateFileContents() {
        return ResourceFileUtil.getResourceFileContents(REPO_ISSUE_TEMPLATE_FILE);
    }

    public static String getRepoDefaultReadmeFileContents() {
        return ResourceFileUtil.getResourceFileContents(REPO_DEFAULT_README_FILE);
    }

    private static String getResourceFileContents(String path) {
        try {
            return Files.readString(Paths.get(
                            ResourceFileUtil.class.getResource(path).toURI()),
                    StandardCharsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }
}