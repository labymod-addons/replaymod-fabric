/*
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.labymod.addons.replaymod.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import net.labymod.api.loader.MinecraftVersion;
import net.labymod.api.models.version.Version;
import net.labymod.api.util.io.IOUtil;
import net.labymod.api.util.io.web.request.Response;
import net.labymod.api.util.io.web.request.types.FileRequest;
import net.labymod.api.util.io.zip.Zips;
import net.labymod.api.util.logging.Logging;

public class ReplayModFiles {

  private static final Logging LOGGER = Logging.create(ReplayModLoader.class);
  private static final Gson GSON = new Gson();

  private static final String MODRINTH_ID = "Nv2fQJo5";
  private static final String REPLAYMOD_VERSION = "2.6.15";

  private final Map<Version, ModrinthArtifact> artifacts = new HashMap<>();

  public void registerArtifact(
      MinecraftVersion version,
      String modrinthVersionId
  ) {
      this.registerArtifact(version, modrinthVersionId, REPLAYMOD_VERSION);
  }

  public void registerArtifact(
      MinecraftVersion version,
      String modrinthVersionId,
      String replayModVersion
  ) {
      this.artifacts.put(version.version(), new ModrinthArtifact(
        modrinthVersionId,
        replayModVersion,
        version.version()
    ));
  }

  public Path downloadReplayMod(Version version, Path directory) throws Exception {
    if (!IOUtil.exists(directory)) {
      Files.createDirectories(directory);
      return this.download(directory, version);
    }

    // list all jar files in the directory
    try (Stream<Path> files = Files.list(directory)) {
      List<Path> existingFiles = files.filter(
          path -> path.getFileName().toString().endsWith(".jar")
      ).toList();
      // if no jar files are present - download replaymod
      if (existingFiles.isEmpty()) {
        return this.download(directory, version);
      }

      // if there are more than one jar files present, it's likely the user did something in this directory. we don't want that
      if (existingFiles.size() > 1) {
        IOUtil.delete(directory);
        Files.createDirectories(directory);
        return this.download(directory, version);
      }

      // if there is just one jar file, we can check if it's 1. replaymod and 2. the correct version
      Path existingFile = existingFiles.get(0);
      AtomicBoolean valid = new AtomicBoolean(false);

      try {
        Zips.read(existingFile, (entry, bytes) -> {
          if (!entry.getName().equals("fabric.mod.json")) {
            return false;
          }

          JsonObject object = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8),
              JsonObject.class);
          String modVersion = object.get("version").getAsString();
          String id = object.get("id").getAsString();
          if (!id.equals("replaymod")) {
            return true;
          }

          String fabricVersion = this.artifacts.get(version).getFabricVersion();
          if (modVersion.equals(fabricVersion)) {
            valid.set(true);
            return true;
          }

          LOGGER.info(
              "Installed ReplayMod Version is outdated (installed: " + modVersion + ", latest: "
                  + fabricVersion + ")! Updating...");
          return true;
        });
      } catch (Exception e) {
        LOGGER.warn("Failed to read fabric.mod.json of local replaymod to verify", e);
      }

      // if the installed replaymod is up to date, we can return the existing file
      if (valid.get()) {
        LOGGER.info("The installed ReplayMod is up to date!");
        return existingFile;
      }

      // otherwise we delete the existing file and download the latest version
      IOUtil.delete(existingFile);
      return this.download(directory, version);
    }
  }

  private Path download(Path directory, Version version) {
    ModrinthArtifact artifact = this.artifacts.get(version);
    if (artifact == null) {
      throw new IllegalStateException("No artifact registered for version " + version);
    }

    return this.download(directory, artifact);
  }

  private Path download(Path directory, ModrinthArtifact artifact) {
    LOGGER.info("Downloading ReplayMod " + artifact.getFabricVersion() + "...");
    Path file = directory.resolve(
        "replaymod-" + artifact.minecrafVersion + "-" + artifact.replayModVersion + ".jar");
    Response<Path> pathResponse = FileRequest.of(file).url(artifact.getDownloadUrl()).executeSync();
    if (pathResponse.hasException()) {
      throw new RuntimeException("Failed to download ReplayMod", pathResponse.exception());
    }

    // create a file that indicates that this is a mod directory, as we don't want the user to put other files in here
    Path infoFile = directory.resolve("this is not a mod directory");
    if (!IOUtil.exists(infoFile)) {
      try {
        Files.createFile(infoFile);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    LOGGER.info("Successfully downloaded ReplayMod " + artifact.getFabricVersion());

    // return the path to the downloaded file so we can hand it over to the mod loader
    return file;
  }

  private static class ModrinthArtifact {

    private final String modrinthVersionId;
    private final String replayModVersion;
    private final Version minecrafVersion;

    private ModrinthArtifact(
        String modrinthVersionId,
        String replayModVersion,
        Version minecrafVersion
    ) {
      this.modrinthVersionId = modrinthVersionId;
      this.replayModVersion = replayModVersion;
      this.minecrafVersion = minecrafVersion;
    }

    public String getFabricVersion() {
      return this.minecrafVersion + "-" + this.replayModVersion;
    }

    private String getDownloadUrl() {
      return "https://cdn.modrinth.com/data/" + MODRINTH_ID + "/versions/" + this.modrinthVersionId
          + "/replaymod-" + this.minecrafVersion + "-" + this.replayModVersion + ".jar";
    }
  }
}
