package de.robertmetzger;

import com.beust.jcommander.JCommander;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Hello world! */
public class App {
  private static Logger LOG = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) throws Exception {
    LOG.info("Launching PR labeler version {}", Utils.getVersion());
    final Arguments arguments = new Arguments();
    final JCommander jCommander =
        JCommander.newBuilder()
            .addObject(arguments)
            .programName("java -jar pr-labeler.jar")
            .args(args)
            .build();

    if (arguments.help) {
      final StringBuilder helpOutput = new StringBuilder();
      jCommander.usage(helpOutput);
      LOG.info(helpOutput.toString());
      return;
    }

    final Path cacheDirectory = Paths.get(arguments.cacheDir);

    DiskCachedJira jira =
        new DiskCachedJira(arguments.jiraUrl, new DiskCache(cacheDirectory.resolve("jira")));
    PullRequestLabelCache labelCache =
        new PullRequestLabelCache(cacheDirectory.resolve("labelCache"));
    PullUpdater updater =
        new PullUpdater(
            arguments.username,
            arguments.githubToken,
            arguments.mainCacheMB,
            cacheDirectory,
            jira,
            labelCache,
            arguments.repo);

    int checkNewPRSeconds = arguments.pollingIntervalInSeconds;

    Runnable checkPRs =
        () -> {
          while (true) {
            try {
              updater.checkPullRequests();
            } catch (Throwable t) {
              LOG.warn("Error while checking for new PRs", t);
            }
            LOG.info("Done checking pull requests. Waiting for {} seconds", checkNewPRSeconds);
            try {
              Thread.sleep(checkNewPRSeconds * 1000);
            } catch (InterruptedException e) {
              LOG.warn("Thread got interrupted");
              break;
            }
          }
        };

    Thread checkPRThread = new Thread(checkPRs);
    checkPRThread.setName("check-pr-thread");
    checkPRThread.start();

    ScheduledExecutorService jiraInvalidatorExecutor = Executors.newScheduledThreadPool(1);
    int invalidateJiraSeconds = arguments.validationDurationInSeconds;

    if (invalidateJiraSeconds > 0) {
      JiraCacheInvalidator invalidator = new JiraCacheInvalidator(jira, cacheDirectory);
      jiraInvalidatorExecutor.scheduleAtFixedRate(
          () -> {
            try {
              invalidator.run();
            } catch (Throwable t) {
              LOG.warn("Error while invalidating JIRAs", t);
            }
          },
          0,
          invalidateJiraSeconds,
          TimeUnit.SECONDS);
    }
  }
}
