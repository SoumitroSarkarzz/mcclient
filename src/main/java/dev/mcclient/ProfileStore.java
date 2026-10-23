package dev.mcclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** Local test metadata only. This class has no access to Minecraft sessions. */
public final class ProfileStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Profile(UUID id, String label, String username) {}
    private record State(int schema, UUID selected, List<Profile> profiles) {}
    private record Fixture(String mode, boolean authenticated, UUID localProfileId, String username) {}
    private final Path directory;
    private State state;

    public ProfileStore(Path directory) throws IOException {
        this.directory = directory;
        Path file = directory.resolve("profiles.json");
        if (!Files.exists(file)) {
            state = new State(1, null, List.of());
            return;
        }
        try {
            if (Files.size(file) > 262144) throw new IllegalArgumentException();
            state = JSON.fromJson(Files.readString(file), State.class);
            validate(state);
        } catch (RuntimeException error) {
            throw new IOException("Profile settings are invalid. Back up config/mcprofiles/profiles.json before repairing it; it was not overwritten.", error);
        }
    }

    public List<Profile> profiles() { return List.copyOf(state.profiles()); }
    public Profile selected() { return state.profiles().stream().filter(p -> p.id().equals(state.selected())).findFirst().orElse(null); }

    public void add(String label, String username) throws IOException {
        if (state.profiles().size() >= 100) throw new IllegalArgumentException("Maximum 100 local test profiles.");
        Profile p = new Profile(UUID.randomUUID(), checkedLabel(label), checkedUsername(username));
        List<Profile> next = new ArrayList<>(state.profiles());
        next.add(p);
        commit(new State(1, p.id(), next));
    }

    public void update(String label, String username) throws IOException {
        Profile selected = requireSelected();
        Profile replacement = new Profile(selected.id(), checkedLabel(label), checkedUsername(username));
        commit(new State(1, selected.id(), state.profiles().stream().map(p -> p.id().equals(selected.id()) ? replacement : p).toList()));
    }

    public void cycle(int offset) throws IOException {
        if (state.profiles().isEmpty()) return;
        int index = state.profiles().indexOf(selected());
        Profile next = state.profiles().get(Math.floorMod(index + offset, state.profiles().size()));
        commit(new State(1, next.id(), state.profiles()));
    }

    public void delete() throws IOException {
        Profile selected = requireSelected();
        List<Profile> next = state.profiles().stream().filter(p -> !p.id().equals(selected.id())).toList();
        commit(new State(1, next.isEmpty() ? null : next.getFirst().id(), next));
    }

    public Path exportFixture() throws IOException {
        Profile p = requireSelected();
        Path path = directory.resolve("test-fixtures").resolve(p.id() + ".json");
        writePrivate(path, new Fixture("local-test-only", false, p.id(), p.username()));
        return path;
    }

    private Profile requireSelected() {
        Profile p = selected();
        if (p == null) throw new IllegalArgumentException("Add or select a local test profile first.");
        return p;
    }

    private void commit(State next) throws IOException {
        validate(next);
        writePrivate(directory.resolve("profiles.json"), next);
        state = next;
    }

    private static void validate(State s) {
        if (s == null || s.schema() != 1 || s.profiles() == null || s.profiles().size() > 100) throw new IllegalArgumentException("Invalid profile settings.");
        Set<UUID> ids = new HashSet<>();
        for (Profile p : s.profiles()) {
            if (p == null || p.id() == null || !ids.add(p.id())) throw new IllegalArgumentException("Invalid profile ID.");
            checkedLabel(p.label());
            checkedUsername(p.username());
        }
        if ((s.selected() == null) != s.profiles().isEmpty() || (s.selected() != null && !ids.contains(s.selected()))) throw new IllegalArgumentException("Invalid selected profile.");
    }

    private static String checkedLabel(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 40 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use a label of 1–40 characters without control characters.");
        return value.strip();
    }

    private static String checkedUsername(String value) {
        if (value == null || !value.strip().matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Test usernames need 3–16 letters, numbers, or underscores.");
        return value.strip();
    }

    private static void writePrivate(Path file, Object data) throws IOException {
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(file.getParent())) throw new IOException("Profile storage cannot use symbolic links.");
        boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
        if (posix) Files.createDirectories(file.getParent(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        else Files.createDirectories(file.getParent());
        Path temp = posix
            ? Files.createTempFile(file.getParent(), ".profiles-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            : Files.createTempFile(file.getParent(), ".profiles-", ".tmp");
        try {
            Files.writeString(temp, JSON.toJson(data));
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
