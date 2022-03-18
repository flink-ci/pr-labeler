package de.robertmetzger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import okhttp3.Cache;
import org.kohsuke.github.GHDirection;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHPullRequestQueryBuilder;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PullUpdater {
  private static final Logger LOG = LoggerFactory.getLogger(PullUpdater.class);

  private static final String LABEL_COLOR = "175fb7";
  private static final String COMPONENT_PREFIX = "component=";

  private final GitHub cachedGitHubForPulls;
  private final GitHub uncachedGitHubForWritingLabels;

  private final Cache gitHubForLabelsCache;
  private final GHRepository uncachedRepoForWritingLabels;
  private final GHRepository cachedRepoForPulls;
  private final GHRepository cachedRepoForLabels;
  private final PullRequestLabelCache labelCache;

  private final DiskCachedJira jira;

  public PullUpdater(
      String user,
      String token,
      int mainCacheMB,
      Path directory,
      DiskCachedJira jira,
      PullRequestLabelCache labelCache,
      String repoName)
      throws IOException {
    this.jira = jira;

    cachedGitHubForPulls =
        Utils.getGitHub(user, token, directory.resolve("githubPullCache"), mainCacheMB).gitHub;

    this.cachedRepoForPulls = cachedGitHubForPulls.getRepository(repoName);

    Utils.GitHubWithCache ghForLabels =
        Utils.getGitHub(user, token, directory.resolve("githubLabelCache"), mainCacheMB);
    this.cachedRepoForLabels = ghForLabels.gitHub.getRepository(repoName);
    this.gitHubForLabelsCache = ghForLabels.cache;

    this.uncachedGitHubForWritingLabels = Utils.getGitHub(user, token, null, 0).gitHub;
    this.uncachedRepoForWritingLabels = uncachedGitHubForWritingLabels.getRepository(repoName);
    this.labelCache = labelCache;
  }

  public void checkPullRequests() {
    try {
      LOG.info(
          "Checking pull requests. GitHub API limits read: {}, write: {}",
          cachedGitHubForPulls.getRateLimit(),
          uncachedGitHubForWritingLabels.getRateLimit());
    } catch (IOException e) {
      LOG.warn("Error while getting rate limits", e);
    }

    /*
     * Statistics on March 14:
     * With empty cache:
     * Space on Disk: 14 MB
     * Rate limit usage: 266 requests
     * seconds elapsed: 360 seconds
     *
     * Against a full cache:
     * Rate limit usage: 266 requests
     * seconds elapsed: 360 seconds
     */
    // this is a very expensive call
    // List<GHPullRequest> pullRequests = cachedRepoForPulls.getPullRequests(GHIssueState.ALL);

    /*
     * Statistics on March 14:
     * With empty cache:
     * Space on Disk: 14 MB
     * Rate limit usage: 266 requests
     * seconds elapsed: 360 seconds
     *
     * Against a full cache:
     * Rate limit usage: 266 requests
     * seconds elapsed: 360 seconds
     */

    // Use a deterministic query (only the last page changes)
    // TODO: this doesn't work

    GHPullRequestQueryBuilder prQuery = cachedRepoForPulls.queryPullRequests();
    prQuery.state(GHIssueState.ALL);
    prQuery.sort(GHPullRequestQueryBuilder.Sort.CREATED);
    prQuery.direction(GHDirection.DESC); // start with newest PRs

    try {
      LOG.info(
          "GitHub API limits read: {}, write: {}",
          cachedGitHubForPulls.getRateLimit(),
          uncachedGitHubForWritingLabels.getRateLimit());
    } catch (IOException e) {
      LOG.warn("Error while getting rate limits", e);
    }

    for (GHPullRequest pullRequest : prQuery.list()) {
      String jiraId = extractJiraId(pullRequest.getTitle());
      if (jiraId == null) {
        LOG.warn("Failed to extract Jira ID from PR '{}'.", pullRequest.getTitle());
        continue;
      }
      try {
        Set<String> jiraComponents = normalizeComponents(jira.getComponents(jiraId));
        Set<String> requiredLabels = getComponentLabels(jiraComponents);

        Set<String> existingPRLabels =
            labelCache.getLabelsFor(pullRequest).stream()
                .filter(l -> l.startsWith(COMPONENT_PREFIX))
                .collect(Collectors.toSet());

        Set<String> toAdd = new HashSet<>(requiredLabels);
        toAdd.removeAll(existingPRLabels);

        Set<String> toRemove = new HashSet<>(existingPRLabels);
        toRemove.removeAll(requiredLabels);

        if (toRemove.size() > 0 || toAdd.size() > 0) {
          LOG.info(
              "Updating PR '{}' adding labels '{}', removing '{}'",
              pullRequest.getTitle(),
              toAdd,
              toRemove);
          if (!toAdd.isEmpty()) {
            pullRequest.addLabels(toAdd.toArray(new String[] {}));
          }
          if (!toRemove.isEmpty()) {
            pullRequest.removeLabels(toRemove.toArray(new String[] {}));
          }
        } else {
          LOG.trace("Skipping PR '{}'", pullRequest.getTitle());
        }
      } catch (HttpException e) {
        LOG.error(
            "An error occurred while processing PR '{}': {} {}.",
            pullRequest.getTitle(),
            e.getResponseCode(),
            e.getResponseMessage(),
            e);
      } catch (Exception e) {
        LOG.error("An error occurred while processing PR '{}'.", pullRequest.getTitle(), e);
      }
    }
  }

  private Set<String> getComponentLabels(Set<String> jiraComponents) throws IOException {
    Set<String> labels = new HashSet<>(jiraComponents.size());
    for (String label : jiraComponents) {
      try {
        labels.add(createOrGetLabel(label));
      } catch (IOException e) {
        throw new IOException("Error while getting label " + label, e);
      }
    }
    return labels;
  }

  private String createOrGetLabel(String labelString) throws IOException {
    try {
      return cachedRepoForLabels.getLabel(labelString).getName();
    } catch (FileNotFoundException noLabel) {
      LOG.info("Label '{}' did not exist, creating it", labelString);
      // empty the cache for getting labels so that the newly created label can be found
      gitHubForLabelsCache.evictAll();
      return uncachedRepoForWritingLabels.createLabel(labelString, LABEL_COLOR).getName();
    }
  }

  private static final Pattern pattern = Pattern.compile("(?i).*(FLINK-[0-9]+).*");

  static String extractJiraId(String title) {
    Matcher matcher = pattern.matcher(title);
    if (matcher.find()) {
      return matcher.group(1).toUpperCase();
    }
    return null;
  }

  static Set<String> normalizeComponents(List<String> components) {
    if (components.size() == 0) {
      return Collections.singleton(COMPONENT_PREFIX + "<none>");
    }
    return components.stream()
        .map(
            c -> {
              if (c.startsWith("Formats")) {
                return COMPONENT_PREFIX + "Formats";
              }
              String s = COMPONENT_PREFIX + c.replaceAll(" ", "");
              return s.substring(0, Math.min(s.length(), 50));
            })
        .collect(Collectors.toSet());
  }
}
