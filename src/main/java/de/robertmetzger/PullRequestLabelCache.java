package de.robertmetzger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;
import org.kohsuke.github.GHLabel;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is caching access to GitHub labels on disk Since most PR's labels do not change,
 * should reduce the number of requests to the GitHub API on re-checking old PRs.
 */
public class PullRequestLabelCache {
  private static final Logger LOG = LoggerFactory.getLogger(PullRequestLabelCache.class);

  private final Path directory;

  public PullRequestLabelCache(Path directory) throws IOException {
    Files.createDirectories(directory);
    this.directory = directory;
  }

  public Collection<String> getLabelsFor(GHPullRequest pullRequest) throws IOException {
    Path fileOnDisk = locateFile(Integer.toString(pullRequest.getNumber()));
    if (!Files.exists(fileOnDisk)) {
      return getAndCache(pullRequest, fileOnDisk);
    }
    CacheEntry entry = getFromDisk(fileOnDisk);
    // cache >= GitHub API
    if (entry.lastUpdated.equals(pullRequest.getUpdatedAt())
        || entry.lastUpdated.after(pullRequest.getUpdatedAt())) {
      // cache hit
      return entry.labels;
    }
    return getAndCache(pullRequest, fileOnDisk);
  }

  private CacheEntry getFromDisk(Path fileOnDisk) throws IOException {
    try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(fileOnDisk))) {
      return (CacheEntry) ois.readObject();
    } catch (ClassNotFoundException e) {
      throw new IOException("Class not found", e);
    }
  }

  private Collection<String> getAndCache(GHPullRequest pullRequest, Path fileOnDisk)
      throws IOException {
    LOG.info("Getting labels for PR #{} from GitHub", pullRequest.getNumber());
    CacheEntry entry = new CacheEntry();
    entry.labels =
        pullRequest.getLabels().stream().map(GHLabel::getName).collect(Collectors.toList());
    entry.lastUpdated = pullRequest.getUpdatedAt();
    try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(fileOnDisk))) {
      oos.writeObject(entry);
    }
    return entry.labels;
  }

  public static class CacheEntry implements Serializable {
    public Date lastUpdated;
    public Collection<String> labels;
  }

  private Path locateFile(String key) {
    String name = Base64.getEncoder().encodeToString(key.getBytes());
    return directory.resolve(name);
  }
}
