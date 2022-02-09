package com.tubalinal.githubrepoevents.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tubalinal.githubrepoevents.models.GithubBranch;
import com.tubalinal.githubrepoevents.models.GithubEvent;
import com.tubalinal.githubrepoevents.models.GithubRepo;
import com.tubalinal.githubrepoevents.utils.ResourceFileUtil;
import org.apache.tomcat.util.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("repo")
public class RepoEventsController {
    static final Logger LOGGER = LoggerFactory.getLogger(RepoEventsController.class.getName());
    static final String GH_EVENT_CREATED = "created";

    @Value("${gh_api_token:}")
    private String apiToken;

    @Value("${gh_base_endpoint}")
    private String baseEndpoint;

    @Value("${gh_api_accept}")
    private String apiAccept;

    @Value("${alert_users}")
    private String alertUsers;

    @Value("${service_secret}")
    private String serviceSecret;


    @PostMapping("event_callback")
    public void handleEvent(@RequestHeader("X-Hub-Signature-256") String githubSignature, @RequestBody String body) throws JsonProcessingException {
        if (validateSignature(githubSignature, body)) {
            ObjectMapper mapper = new ObjectMapper();
            GithubEvent githubEvent = mapper.readValue(body, GithubEvent.class);

            if (githubEvent.getAction().equals(GH_EVENT_CREATED)) {
                LOGGER.info(String.format("Processing event: %s", githubEvent));
                applyProtectionSettingsToRepo(githubEvent.getRepository());
            } else {
                LOGGER.info(String.format("Ignoring event: %s", githubEvent));
            }
        } else {
            LOGGER.error(String.format("Signature supplied is not valid. Not processing request: %s", body));
        }
    }

    private boolean validateSignature(String githubSignature, String body) {
//        https://docs.github.com/en/developers/webhooks-and-events/webhooks/securing-your-webhooks
        try {
            String expectedSignature = "sha256=" + hmacDigest(body, getServiceSecret(), "HmacSHA256");

            LOGGER.info(String.format("Expected Signature: %s, Actual Signature: %s", expectedSignature, githubSignature));

            return expectedSignature.equals(githubSignature);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String hmacDigest(String msg, String keyString, String algo) {
        //Copied this function from: https://gist.github.com/MaximeFrancoeur/bcb7fc2db08c704f322a

        String digest = null;
        try {
            SecretKeySpec key = new SecretKeySpec((keyString).getBytes("UTF-8"), algo);
            Mac mac = Mac.getInstance(algo);
            mac.init(key);

            byte[] bytes = mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));

            StringBuffer hash = new StringBuffer();
            for (int i = 0; i < bytes.length; i++) {
                String hex = Integer.toHexString(0xFF & bytes[i]);
                if (hex.length() == 1) {
                    hash.append('0');
                }
                hash.append(hex);
            }
            digest = hash.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return digest;
    }

    @PostMapping("protect")
    public void applyProtections(@RequestBody String[] reposFullName) {
        RestTemplate restTemplate = getRestTemplateWithAPIRequestHeaders();

        for(int i=0; i<reposFullName.length; i++) {
            String repoFullName = reposFullName[i];
            GithubRepo repo = new GithubRepo();
            repo.setFull_name(repoFullName);
            repo.setDefault_branch("");
            try {
                repo = restTemplate.getForObject(replaceRepoUrlTokens(repo, getRepoEndpoint()), GithubRepo.class);
                applyProtectionSettingsToRepo(repo);
                LOGGER.info(String.format("Protection applied to repo %s", reposFullName));
            } catch (Exception ex) {
                LOGGER.error(String.format("Could not apply protections to repo %s. Error is %s.", repoFullName, ex.getMessage()));
            }
        }
    }

    private void applyProtectionSettingsToRepo(GithubRepo repo) {
        RestTemplate restTemplate = getRestTemplateWithAPIRequestHeaders();

//        Step 1: See if the default branch actually exists.
        ResponseEntity<GithubBranch> branchResponse = null;

        String branchUrl = this.replaceRepoUrlTokens(repo, this.getBranchEndpoint());
        LOGGER.info(String.format("Retrieving branch from branchUrl: %s", branchUrl));

        try {
            branchResponse = restTemplate.getForEntity(branchUrl, GithubBranch.class);
        } catch (HttpClientErrorException ex) {
            LOGGER.info(String.format("Could not retrieve branch %s.", branchUrl));
        }

        if (branchResponse == null || branchResponse.getStatusCode().value() == 404) {
//            Default Branch not found. Create a new branch.
            LOGGER.info("Creating a default branch.", branchUrl);
            createBranch(repo, restTemplate);
        }

//        Step 2: Automate protection of the default branch
        if (!applyProtectionToRepoDefaultBranch(repo, restTemplate)) return;

//        Step 3: Create an issue with a @mention
        createIssue(repo, restTemplate);
    }

    private void createIssue(GithubRepo repo, RestTemplate restTemplate) {
//        https://docs.github.com/en/rest/reference/issues#create-an-issue
//        This will dump the protection settings that was used into the body of the issue
        String issueBodyTemplate = ResourceFileUtil.getRepoIssueTemplateFileBodyContents();

        String issueBody =
                issueBodyTemplate
                        .replace("{{ALERT_USERS}}", getAlertUsers())
                        .replace("{{PROTECTION_SETTINGS}}", ResourceFileUtil.getProtectionTemplateFileContents()
                        .replace("\"", "\\\""));

        String issueTemplate = ResourceFileUtil.getRepoIssueTemplateFileContents();
        String issueContents = issueTemplate.replace("{{BODY_MARKDOWN}}", removeLinebreaks(issueBody));

        String issuesUrl = this.replaceRepoUrlTokens(repo, this.getIssuesEndpoint());

        URI uri = restTemplate.postForLocation(URI.create(issuesUrl), issueContents);
        LOGGER.info(String.format("Issue created at %s", uri.getPath()));
    }

    private boolean applyProtectionToRepoDefaultBranch(GithubRepo repo, RestTemplate restTemplate) {
        String branchProtectionUrl = this.replaceRepoUrlTokens(repo, this.getBranchProtectionEndpoint());
        String protectionFileContents = ResourceFileUtil.getProtectionTemplateFileContents();

        LOGGER.info(String.format("Applying branch protection to: %s", branchProtectionUrl));

        ObjectMapper mapper = new ObjectMapper();

        try {
            Map<String, Object> dataMap = mapper.readValue(protectionFileContents, Map.class);
            restTemplate.put(branchProtectionUrl, dataMap, Map.class);
        } catch (JsonProcessingException ex) {
            ex.printStackTrace();
            return false;
        }

        return true;
    }

    private void createBranch(GithubRepo repo, RestTemplate restTemplate) {
//        In order to create a branch, we need to create a file. So,
//        we'll create a default README file.

//        The file has to be base64 encoded
        String readmeContents = Base64.encodeBase64String(ResourceFileUtil.getRepoDefaultReadmeFileContents().getBytes(StandardCharsets.UTF_8));
        String readmeContentsUrl = repo.getContents_url().replace("{+path}", "README.md");

        Map<String, String> map = new HashMap<>();
        map.put("content", readmeContents);
        map.put("message", "Added default README file");

        restTemplate.put(readmeContentsUrl, map, Map.class);
        LOGGER.info(String.format("Created a readme at: %s", readmeContentsUrl));
    }

    private RestTemplate getRestTemplateWithAPIRequestHeaders() {
        RestTemplate restTemplate = new RestTemplate();
        // We want to make sure every request is for the v3 version of the REST API,
        // so we add this interceptor to make sure the right headers are set.
        // References:
        //  - https://docs.github.com/en/developers/overview/about-githubs-apis
        //  - https://docs.github.com/en/rest/overview/media-types#request-specific-version
        restTemplate.getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set("Accept", getApiAccept());
            request.getHeaders().set("Authorization", String.format("token %s", getApiToken()));
            request.getHeaders().set("Content-Type", "application/json");
            return execution.execute(request, body);
        });

        return restTemplate;
    }

    private String replaceRepoUrlTokens(GithubRepo repo, String url) {
        return url.replace("{full_name}", repo.getFull_name())
                .replace("{branch}", repo.getDefault_branch());
    }

    private String removeLinebreaks(String s) {
        return s.replaceAll("(\\r|\\n|\\r\\n)+", "\\\\n");
    }

    private String getRepoEndpoint() { return getBaseEndpoint() + "/repos/{full_name}"; }

    private String getBranchEndpoint() {
        return getBaseEndpoint() + "/repos/{full_name}/branches/{branch}";
    }

    private String getBranchProtectionEndpoint() {
        return getBranchEndpoint() + "/protection";
    }

    private String getIssuesEndpoint() {
        return getBaseEndpoint() + "/repos/{full_name}/issues";
    }

    public String getApiToken() {
        return apiToken;
    }

    public String getBaseEndpoint() {
        return baseEndpoint;
    }

    public String getApiAccept() {
        return apiAccept;
    }

    public String getAlertUsers() {
        return alertUsers;
    }

    public void setAlertUsers(String alertUsers) {
        this.alertUsers = alertUsers;
    }

    public String getServiceSecret() {
        return serviceSecret;
    }

    public void setServiceSecret(String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }
}