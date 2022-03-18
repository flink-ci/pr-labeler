package de.robertmetzger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;

public class Utils {

    public static GitHubWithCache getGitHub(String user, String password, Path cacheDir, int cacheMB) throws IOException {
        GitHubBuilder ghBuilder = GitHubBuilder.fromEnvironment().withPassword(user, password);
        Cache cache = null;
        if(cacheDir != null) {
            cache = new Cache(cacheDir.toFile(), cacheMB * 1024 * 1024);
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .cache(cache)
                .build();
            ghBuilder
                .withConnector(new OkHttpGitHubConnector(okHttpClient));
        }
        GitHub gh = ghBuilder.build();
        if (!gh.isCredentialValid()) {
            throw new RuntimeException("Invalid credentials");
        }

        return new GitHubWithCache(gh, cache);
    }

    public static class GitHubWithCache {
        public final GitHub gitHub;
        public final Cache cache;

        public GitHubWithCache(GitHub gitHub, Cache cache) {
            this.gitHub = gitHub;
            this.cache = cache;
        }
    }

    public static String getVersion() {
        Properties properties = new Properties();
        try {
            properties.load(Utils.class.getClassLoader().getResourceAsStream("git.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties.getProperty("git.commit.id");
    }
}
