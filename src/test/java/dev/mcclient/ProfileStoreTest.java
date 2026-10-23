package dev.mcclient;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {
    @TempDir Path directory;

    @Test void profilesCanBeAddedRenamedSwitchedAndDeletedAcrossRestarts() throws Exception {
        ProfileStore store = new ProfileStore(directory);
        store.add("Dev world", "DevPlayer");
        var original = store.selected().id();
        store.add("Other", "OtherPlayer");
        store.cycle(-1);
        assertEquals(original, store.selected().id());
        store.update("Renamed", "NewTestName");
        store = new ProfileStore(directory);
        assertEquals("Renamed", store.selected().label());
        assertEquals("NewTestName", store.selected().username());
        store.delete();
        assertEquals("OtherPlayer", store.selected().username());
        store.delete();
        assertNull(new ProfileStore(directory).selected());
    }

    @Test void fixtureCannotRepresentAnAuthenticatedSession() throws Exception {
        ProfileStore store = new ProfileStore(directory);
        store.add("Development", "LocalTester");
        var json = JsonParser.parseString(Files.readString(store.exportFixture())).getAsJsonObject();
        assertEquals("local-test-only", json.get("mode").getAsString());
        assertFalse(json.get("authenticated").getAsBoolean());
        assertEquals(4, json.size());
        assertFalse(json.has("accessToken"));
        assertFalse(json.has("session"));
        assertFalse(json.has("uuid"));
    }

    @Test void invalidInputDoesNotModifyMemoryOrDisk() throws Exception {
        ProfileStore store = new ProfileStore(directory);
        store.add("Valid", "LocalTester");
        String before = Files.readString(directory.resolve("profiles.json"));
        for (String name : new String[]{"ab", "../player", "name with spaces", "x".repeat(17), "foo\nbar"}) {
            assertThrows(IllegalArgumentException.class, () -> store.add("Invalid", name));
        }
        assertThrows(IllegalArgumentException.class, () -> store.update("", "NewPlayer"));
        assertEquals(before, Files.readString(directory.resolve("profiles.json")));
        assertEquals(1, store.profiles().size());
    }

    @Test void corruptSettingsArePreserved() throws Exception {
        Path file = directory.resolve("profiles.json");
        Files.writeString(file, "not json");
        assertThrows(IOException.class, () -> new ProfileStore(directory));
        assertEquals("not json", Files.readString(file));
    }

    @Test void forgedSelectionIsRejected() throws Exception {
        Files.writeString(directory.resolve("profiles.json"), "{\"schema\":1,\"selected\":\"00000000-0000-0000-0000-000000000001\",\"profiles\":[]}");
        assertThrows(IOException.class, () -> new ProfileStore(directory));
    }

    @Test void privatePermissionsAreAppliedOnPosix() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) return;
        ProfileStore store = new ProfileStore(directory.resolve("private"));
        store.add("Test", "TestPlayer");
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.resolve("private/profiles.json"))));
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directory.resolve("private"))));
    }

    @Test void failedWriteLeavesSelectedProfileUnchanged() throws Exception {
        if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) return;
        ProfileStore store = new ProfileStore(directory);
        store.add("First", "TestPlayer");
        Path file = directory.resolve("profiles.json");
        Path target = directory.resolve("original.json");
        Files.move(file, target);
        Files.createSymbolicLink(file, target);
        assertThrows(IOException.class, () -> store.add("Second", "OtherPlayer"));
        assertEquals(1, store.profiles().size());
        assertEquals("First", store.selected().label());
    }
}
