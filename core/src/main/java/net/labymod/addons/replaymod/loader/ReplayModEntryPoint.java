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

import java.nio.file.Path;
import java.util.Collection;
import net.labymod.api.Laby;
import net.labymod.api.addon.entrypoint.Entrypoint;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.modloader.ModLoaderDiscoveryEvent;
import net.labymod.api.loader.MinecraftVersions;
import net.labymod.api.models.addon.annotation.AddonEntryPoint;
import net.labymod.api.models.version.Version;
import net.labymod.api.util.logging.Logging;

/**
 * This entry point is loaded before the entry point of labyfabric (the Fabric Loader addon), which
 * means that we can download the replaymod jar and add it to the mod loader before the mod loader
 * itself is initialized.
 */
@AddonEntryPoint(priority = 900)
public class ReplayModEntryPoint implements Entrypoint {

  private static final Logging LOGGER = Logging.create(ReplayModLoader.class);

  private final ReplayModFiles replayModFiles = new ReplayModFiles();

  public ReplayModEntryPoint() {
    this.replayModFiles.registerArtifact(MinecraftVersions.V1_20_4, "gxDkodfS");
    this.replayModFiles.registerArtifact(MinecraftVersions.V1_20_3, "gxDkodfS");
    this.replayModFiles.registerArtifact(MinecraftVersions.V1_20_2, "G3s7lNSQ");
    this.replayModFiles.registerArtifact(MinecraftVersions.V1_20_1, "NIH877ct");

    this.replayModFiles.registerArtifact(MinecraftVersions.V1_19_4, "hWebWQ5c");
    this.replayModFiles.registerArtifact(MinecraftVersions.V1_19_3, "KnZ6lROh");
    this.replayModFiles.registerArtifact(MinecraftVersions.V1_19_2, "BYJF82Q8");

    Laby.labyAPI().eventBus().registerListener(this);
  }

  @Override
  public void initialize(Version version) {
    // NO-OP
  }

  @Subscribe
  public void onModLoaderDiscover(ModLoaderDiscoveryEvent event) {
    if (!event.modLoader().getId().equals("fabricloader")) {
      return;
    }

    // Use next best mod directory as base directory, should optimally be .minecraft\labymod-neo\fabric\{version}\mods\
    Collection<Path> modDirectoryPaths = event.modLoader().getModDirectoryPaths();
    Path modDirectory = null;
    for (Path modDirectoryPath : modDirectoryPaths) {
      modDirectory = modDirectoryPath;
      break;
    }

    if (modDirectory == null) {
      LOGGER.error("Could not find mod directory. Skipping replaymod installation");
      return;
    }

    try {
      // Load replaymod from a subfolder, this way we can easily do anything we want without unintentionally breaking other mods
      Path replayModDirectory = modDirectory.resolve("replaymod");
      Path replayModFile = this.replayModFiles.downloadReplayMod(
          Laby.labyAPI().labyModLoader().version(),
          replayModDirectory
      );

      // Add the path of the replaymod jar file to the mod loader
      event.addAdditionalDiscovery(replayModFile);
    } catch (Exception e) {
      LOGGER.error("Failed to load replaymod", e);
    }
  }
}
