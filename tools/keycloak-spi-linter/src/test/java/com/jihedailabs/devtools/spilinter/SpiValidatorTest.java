package com.jihedailabs.devtools.spilinter;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiValidatorTest {

    private SpiValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SpiValidator(SpiValidatorTest.class.getClassLoader());
    }

    // --- Dummy SPI Interfaces and Implementations ---

    public interface DummySpi {
        String getId();
    }

    public static class ValidProvider implements DummySpi {
        @Override
        public String getId() { return "valid-id"; }
    }

    public static class ValidProvider2 implements DummySpi {
        @Override
        public String getId() { return "valid-id-2"; }
    }

    public static class DuplicateIdProvider implements DummySpi {
        @Override
        public String getId() { return "valid-id"; }
    }

    public static class NoEmptyConstructorProvider implements DummySpi {
        public NoEmptyConstructorProvider(String arg) {}
        @Override
        public String getId() { return "no-empty-ctor"; }
    }

    public static class NullIdProvider implements DummySpi {
        @Override
        public String getId() { return null; }
    }

    public static class NotImplementingProvider {
        public String getId() { return "not-impl"; }
    }

    // --- Tests ---

    @Test
    void testValidProvider(@TempDir Path tempDir) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        
        Path serviceFile = servicesDir.resolve(DummySpi.class.getName());
        Files.write(serviceFile, Arrays.asList(ValidProvider.class.getName()));

        assertTrue(validator.validate(tempDir), "Should be valid");
    }

    @Test
    void testMissingClass(@TempDir Path tempDir) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        
        Path serviceFile = servicesDir.resolve(DummySpi.class.getName());
        Files.write(serviceFile, Arrays.asList("com.example.NonExistentClass"));

        assertFalse(validator.validate(tempDir), "Should fail due to missing class");
    }

    @Test
    void testNotImplementingInterface(@TempDir Path tempDir) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        
        Path serviceFile = servicesDir.resolve(DummySpi.class.getName());
        Files.write(serviceFile, Arrays.asList(NotImplementingProvider.class.getName()));

        assertFalse(validator.validate(tempDir), "Should fail because it doesn't implement the interface");
    }

    @Test
    void testNoEmptyConstructor(@TempDir Path tempDir) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        
        Path serviceFile = servicesDir.resolve(DummySpi.class.getName());
        Files.write(serviceFile, Arrays.asList(NoEmptyConstructorProvider.class.getName()));

        assertFalse(validator.validate(tempDir), "Should fail due to missing no-arg constructor");
    }

    @Test
    void testNullId(@TempDir Path tempDir) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        
        Path serviceFile = servicesDir.resolve(DummySpi.class.getName());
        Files.write(serviceFile, Arrays.asList(NullIdProvider.class.getName()));

        assertFalse(validator.validate(tempDir), "Should fail due to null ID");
    }

    @Test
    void testDuplicateId(@TempDir Path tempDir) throws IOException {
        Path servicesDir = tempDir.resolve("META-INF/services");
        Files.createDirectories(servicesDir);
        
        Path serviceFile = servicesDir.resolve(DummySpi.class.getName());
        Files.write(serviceFile, Arrays.asList(ValidProvider.class.getName(), DuplicateIdProvider.class.getName()));

        assertFalse(validator.validate(tempDir), "Should fail due to duplicate ID");
    }

    @Test
    void testRealKeycloakSpiWorkbench() throws IOException {
        // Points at the compiled target/classes of the sibling keycloak-spi-workbench checkout.
        // Only runs when that repo is cloned next to this one and already built (`mvn compile`
        // there first) — true on this ecosystem's dev machine, not in this repo's own CI, which
        // only checks out dev-tools-workbench. Using an assumption (not a silent if/return) so a
        // missing checkout shows up as SKIPPED in the test report, not a false green PASS — this
        // exact test silently no-op'd once already because of a wrong relative path, and it
        // reported as passed with zero assertions run.
        Path workbenchClasses = Paths.get("../../../keycloak-spi-workbench/target/classes");
        Assumptions.assumeTrue(Files.exists(workbenchClasses),
                "keycloak-spi-workbench not found at " + workbenchClasses.toAbsolutePath()
                        + " — clone it as a sibling of dev-tools-workbench and run `mvn compile` there to exercise this test");

        // The system classloader only has THIS project's own classpath (JUnit, the test-scoped
        // keycloak-server-spi jars) — it has never heard of keycloak-spi-workbench's target/classes,
        // so every impl class lookup fails with ClassNotFoundException regardless of whether the
        // provider is actually valid. Add that directory as a URLClassLoader on top of the system
        // classloader so the Keycloak interfaces resolve via the parent and the impl classes
        // resolve via the child, exactly like the real linter does when pointed at a target project.
        URLClassLoader targetClassLoader = new URLClassLoader(
                new URL[]{workbenchClasses.toUri().toURL()}, ClassLoader.getSystemClassLoader());
        SpiValidator realValidator = new SpiValidator(targetClassLoader);
        assertTrue(realValidator.validate(workbenchClasses), "The real keycloak-spi-workbench project should pass validation without false positives");
    }
}
