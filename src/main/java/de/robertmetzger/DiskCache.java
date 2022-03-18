package de.robertmetzger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DiskCache implements Cache {
  private static final Logger LOG = LoggerFactory.getLogger(DiskCache.class);

  private final Path directory;

  public DiskCache(Path directory) throws IOException {
    Files.createDirectories(directory);
    this.directory = directory;
  }

  private Path locateFile(String key) {
    String name = Base64.getEncoder().encodeToString(key.getBytes());
    return directory.resolve(name);
  }

  @Override
  public List<String> get(String key) {
    if (key == null) {
      return null;
    }
    Path file = locateFile(key);
    if (Files.exists(file)) {

      try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(file))) {
        return (List<String>) ois.readObject();
      } catch (IOException | ClassNotFoundException e) {
        LOG.warn("Error while deserializing cached value", e);
        return null;
      }
    } else {
      return null;
    }
  }

  @Override
  public void put(String key, List<String> elements) throws IOException {
    Path file = locateFile(key);
    try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(file))) {
      oos.writeObject(elements);
    }
  }

  @Override
  public boolean remove(String key) {
    try {
      Files.delete(locateFile(key));
      return true;
    } catch (IOException e) {
      return false;
    }
  }
}
