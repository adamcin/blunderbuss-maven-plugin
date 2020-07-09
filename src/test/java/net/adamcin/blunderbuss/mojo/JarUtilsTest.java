package net.adamcin.blunderbuss.mojo;

import io.reactivex.rxjava3.core.Completable;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JarUtilsTest {
	final Path baseDir = Paths.get("target", "test-out", getClass().getSimpleName()).toAbsolutePath();

	@BeforeEach
	void setUp() throws Exception {
		Files.createDirectories(baseDir);
	}

	static Set<String> getJarEntrySet(final @NotNull File file) throws Exception {
		final Set<String> entryNames = new LinkedHashSet<>();
		try (final JarFile jarFile = new JarFile(file)) {
			for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
				String entryName = entries.nextElement().getName();
				entryNames.add(entryName);
			}
		}
		return entryNames;
	}

	static <T> Set<T> setOf(final @NotNull T... elements) {
		return new HashSet<>(Arrays.asList(elements));
	}

	@Test
	void includedEntryFilter() {
		final Path testOut = baseDir.resolve("includedEntryFilter");
		assertTrue(JarUtils.includedEntry.accept(testOut.resolve("someFile.jar").toFile()),
				"some file is accepted");
		assertTrue(JarUtils.includedEntry.accept(testOut.resolve("META-INF").toFile(), "someFile.jar"),
				"some file under META-INF");
		assertFalse(JarUtils.includedEntry.accept(testOut.resolve("META-INF/MANIFEST.MF").toFile()),
				"META-INF/MANIFEST.MF is not accepted");
		assertFalse(JarUtils.includedEntry.accept(testOut.resolve("META-INF").toFile(), "MANIFEST.MF"),
				"manifest is not accepted under META-INF dir");
	}

	static Completable deleteRecursively(final Path path) {
		return Completable.create(emitter -> {
			if (Files.exists(path)) {
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
						Files.deleteIfExists(file);
						return super.visitFile(file, attrs);
					}

					@Override
					public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
						Files.deleteIfExists(dir);
						return super.postVisitDirectory(dir, exc);
					}
				});
			}
			emitter.onComplete();
		});
	}

	@Test
	void extractsJarFile() throws Exception {
		final Path testOut = baseDir.resolve("extractsJarFile");
		deleteRecursively(testOut).blockingAwait();
		Files.createDirectories(testOut);

		final File embeddedJar = testOut.resolve("embedded.jar").toFile();
		final Path extractedDir = testOut.resolve("extracted");
		final Set<String> expectChildren = setOf("META-INF", "META-INF/MANIFEST.MF", "someScript.js");
		Files.createDirectories(extractedDir);

		for (String relPath : expectChildren) {
			assertFalse(Files.exists(extractedDir.resolve(relPath)), relPath + " not exists");
		}

		JarUtils.createJarFile(embeddedJar, Paths.get("src/test/resources/embedded"))
				.andThen(JarUtils.extractJarFile(embeddedJar, extractedDir))
				.blockingAwait();

		for (String relPath : expectChildren) {
			assertTrue(Files.exists(extractedDir.resolve(relPath)), relPath + " exists");
		}
	}

	@Test
	void createsJarFile() throws Exception {
		final Path testOut = baseDir.resolve("createsJarFile");
		deleteRecursively(testOut).blockingAwait();
		Files.createDirectories(testOut);
		final File embeddedJar = testOut.resolve("embedded.jar").toFile();
		JarUtils.createJarFile(embeddedJar, Paths.get("src/test/resources/embedded"))
				.blockingAwait();

		assertEquals(setOf("META-INF/", "META-INF/MANIFEST.MF", "someScript.js"),
				getJarEntrySet(embeddedJar),
				"embedded jar has entries");

		assertEquals("jcr_root/apps/oakpal/install/",
				"jcr_root/apps/oakpal/install/".replaceFirst("/?$", "/"),
				"replace works");

		final File outJar = testOut.resolve("simple.jar").toFile();
		JarUtils.createJarFile(outJar, Paths.get("src/test/resources/extracted/simple"))
				.blockingAwait();

		assertEquals(
				setOf(
						"META-INF/",
						"META-INF/vault/",
						"META-INF/vault/filter.xml",
						"META-INF/vault/properties.xml",
						"jcr_root/",
						"jcr_root/apps/",
						"jcr_root/apps/oakpal/",
						"jcr_root/apps/oakpal/.content.xml"
				),
				getJarEntrySet(outJar), "simple jar has entries");
	}

	@Test
	void createJarFileFails() throws Exception {
		final Path testOut = baseDir.resolve("createJarFileFails");
		deleteRecursively(testOut).blockingAwait();
		Files.createDirectories(testOut);
		final Path parentDir = testOut.resolve("parentDir");
		final File outJar = parentDir.resolve("out.jar").toFile();
		Files.createFile(parentDir);
		assertThrows(Exception.class, () -> {
			JarUtils.createJarFile(outJar, Paths.get("src/test/resources/extracted/simple"))
					.blockingAwait();
		}, "expect exception");

	}
}