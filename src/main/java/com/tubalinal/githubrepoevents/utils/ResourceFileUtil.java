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
        return getFileFromEnvPathOrResourcePath("REPO_PROTECTION_TEMPLATE_FILE", REPO_PROTECTION_TEMPLATE_FILE);
    }

    public static String getRepoIssueTemplateFileBodyContents() {
        return getFileFromEnvPathOrResourcePath("REPO_ISSUE_BODY_TEMPLATE_FILE", REPO_ISSUE_BODY_TEMPLATE_FILE);
    }

    public static String getRepoIssueTemplateFileContents() {
        return getFileFromEnvPathOrResourcePath("REPO_ISSUE_TEMPLATE_FILE", REPO_ISSUE_TEMPLATE_FILE);
    }

    public static String getRepoDefaultReadmeFileContents() {
        return getFileFromEnvPathOrResourcePath("REPO_DEFAULT_README_FILE", REPO_DEFAULT_README_FILE);
    }

    private static String getFileFromEnvPathOrResourcePath(String envVarPath, String defaultFile) {
        String file = System.getenv(envVarPath);
        if (file != null && !file.equals("")) {
            return ResourceFileUtil.getFileFromEnvironmentPath(file);
        } else {
            return ResourceFileUtil.getFileFromResourcePath(defaultFile);
        }
    }

    private static String getFileFromResourcePath(String path) {
        try {
            return Files.readString(
                    Paths.get(ResourceFileUtil.class.getResource(path).toURI()),
                    StandardCharsets.UTF_8);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getFileFromEnvironmentPath(String path){
        try {
            LOGGER.info(path);
            return Files.readString(Paths.get(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}