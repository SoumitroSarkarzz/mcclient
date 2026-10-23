package dev.mcclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("mcclient.json");
    public boolean hud = true;
    public boolean targetPlayers = false;
    public boolean criticalAura = true;
    public boolean autoJumpCrits = true;
    public double range = 3.0;
    public double flightSpeed = 0.3;
    public static ClientConfig load() {
        try {
            if (Files.exists(FILE)) {
                ClientConfig c = GSON.fromJson(Files.readString(FILE), ClientConfig.class);
                if (c != null) {
                    c.range = Double.isFinite(c.range) ? Math.clamp(c.range, 1, 6) : 3;
                    c.flightSpeed = Double.isFinite(c.flightSpeed) ? Math.clamp(c.flightSpeed, 0.1, 1) : 0.3;
                    return c;
                }
            }
        } catch (IOException | RuntimeException e) { McClient.LOG.warn("Could not load client settings; using defaults", e); }
        return new ClientConfig();
    }
    public void save() {
        try { Files.createDirectories(FILE.getParent()); Files.writeString(FILE, GSON.toJson(this)); }
        catch (IOException e) { McClient.LOG.warn("Could not save client settings", e); }
    }
}
