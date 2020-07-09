package net.adamcin.blunderbuss.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactGroupTest {
	final Path baseDir = Paths.get("target", "test-out", getClass().getSimpleName()).toAbsolutePath();

	final Path repo1Dir = Paths.get("src/test/resources/repo1").toAbsolutePath();

	final ArtifactHandlerManager handlers = new DefaultArtifactHandlers();

	@ParameterizedTest
	@CsvSource({
			"com, widget, 1",
			"com, widget, 1-SNAPSHOT",
			"com, widget, v12345",
			"com.ex, widget, 1",
			"com.ex, widget, 1-SNAPSHOT",
			"com.ex, widget, v12345",
			"com.ex.ex, widget, 1",
			"com.ex.ex, widget, 1-SNAPSHOT",
			"com.ex.ex, widget, v12345",
			"net, widget, 1",
			"net, widget, 1-SNAPSHOT",
			"net, widget, v12345",
			"net.ex, widget, 1",
			"net.ex, widget, 1-SNAPSHOT",
			"net.ex, widget, v12345",
			"net.ex.ex, widget, 1",
			"net.ex.ex, widget, 1-SNAPSHOT",
			"net.ex.ex, widget, v12345"
	})
	void constructsArtifactGroupMinParams(String groupId, String artifactId, String version) {
		final Path layoutPrefix = getLayoutPrefix(groupId, artifactId, version);
		final Artifact artifact = getMockPomArtifact(groupId, artifactId, version);
		ArtifactGroup group = new ArtifactGroup(layoutPrefix, artifact);
		assertEquals(layoutPrefix, group.getLayoutPrefix(), "expect equal layoutPrefix");
		assertSame(artifact, group.getPomArtifact(), "expect same pom artifact");
		assertEquals(Gav.fromArtifact(artifact), group.getGav(), "expect equal gav");
		assertEquals(Collections.<Path, Artifact>emptyMap(), group.getDeployables(), "expect equal deployables");
		assertEquals(Collections.emptySet(), group.getIndexed(), "expect equal indexed");
		assertFalse(group.isTerminateOnFailure(), "expect false terminateOnFailure");
		assertEquals(artifact.isSnapshot(), group.isSnapshot(), "expect correct isSnapshot");
		assertNotEquals(artifact.isSnapshot(), group.nonSnapshot(), "expect correct nonSnapshot");
		assertEquals(layoutPrefix.getParent().resolve(layoutPrefix.getFileName().toString() + ".txt"),
				group.getIndexFileRelPath(),
				"expect equal indexFileRelPath");
	}

	@ParameterizedTest
	@CsvSource({
			"com, widget, 1, , , false",
			"com, widget, 1-SNAPSHOT, widget-1-SNAPSHOT-reversed.txt, , true",
			"com, widget, v12345, , widget-v12345.txt, false",
			"com.ex, widget, 1, widget-1-reversed.txt, widget-1-reversed.txt, false",
			"com.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT-reversed.txt, widget-1-SNAPSHOT-reversed.txt, false",
			"com.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT-reversed.txt, widget-1-SNAPSHOT.txt, false",
			"com.ex, widget, v12345, widget-v12345.txt, , true",
			"com.ex.ex, widget, 1, widget-1.txt, widget-1.txt, true",
			"com.ex.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom, , false",
			"com.ex.ex, widget, v12345, , widget-v12345.pom, true"
	})
	void constructsArtifactGroupMoreParams(
			String groupId, String artifactId, String version,
			String deployableFilename, String indexedFilename,
			boolean terminateOnFailure) {
		final Path layoutPrefix = getLayoutPrefix(groupId, artifactId, version);
		final Artifact artifact = getMockPomArtifact(groupId, artifactId, version);
		final Map<Path, Artifact> deployables = deployableFilename == null
				? Collections.emptyMap()
				: Collections.singletonMap(Paths.get(deployableFilename), getMockArtifact(groupId, artifactId, version, deployableFilename));
		final Set<Path> indexed = indexedFilename == null
				? Collections.emptySet()
				: Collections.singleton(Paths.get(indexedFilename));
		ArtifactGroup group = new ArtifactGroup(layoutPrefix, artifact, deployables, indexed, terminateOnFailure);
		assertEquals(layoutPrefix, group.getLayoutPrefix(), "expect equal layoutPrefix");
		assertSame(artifact, group.getPomArtifact(), "expect same pom artifact");
		assertEquals(Gav.fromArtifact(artifact), group.getGav(), "expect equal gav");
		assertEquals(deployables, group.getDeployables(), "expect equal deployables");
		assertEquals(indexed, group.getIndexed(), "expect equal indexed");
		assertEquals(terminateOnFailure, group.isTerminateOnFailure(), "expect same terminateOnFailure");
		assertEquals(artifact.isSnapshot(), group.isSnapshot(), "expect correct isSnapshot");
		assertNotEquals(artifact.isSnapshot(), group.nonSnapshot(), "expect correct nonSnapshot");
		assertEquals(layoutPrefix.getParent().resolve(layoutPrefix.getFileName().toString() + ".txt"),
				group.getIndexFileRelPath(),
				"expect equal indexFileRelPath");
	}

	Path getLayoutPrefix(String groupId, String artifactId, String version) {
		final String[] headTail = groupId.split(Pattern.quote("."), 2);
		return Paths.get(headTail[0], headTail.length > 1 ? headTail[1].split(Pattern.quote(".")) : new String[0])
				.resolve(artifactId)
				.resolve(version);
	}

	Artifact getMockPomArtifact(String groupId, String artifactId, String version) {
		return getMockArtifact(groupId, artifactId, version, artifactId + "-" + version + ".pom");
	}

	Artifact getMockArtifact(String groupId, String artifactId, String version, String filename) {
		final String prefix = artifactId + "-" + version;
		if (!filename.startsWith(prefix) && filename.contains(".")) {
			throw new IllegalArgumentException(String.format("illegal filename %s for mock artifact %s",
					filename, new Gav(groupId, artifactId, version)));
		}
		final String suffix = filename.substring(prefix.length());
		final int firstPeriod = suffix.indexOf(".");
		final String classifier = filename.startsWith("-") ? suffix.substring(1, firstPeriod) : null;
		final String type = suffix.substring(firstPeriod + 1);
		final Path layoutPrefix = getLayoutPrefix(groupId, artifactId, version);
		final File file = repo1Dir.resolve(layoutPrefix).resolve(filename).toFile();
		final Artifact artifact = mock(Artifact.class);
		when(artifact.getGroupId()).thenReturn(groupId);
		when(artifact.getArtifactId()).thenReturn(artifactId);
		when(artifact.getVersion()).thenReturn(version);
		when(artifact.isSnapshot()).thenReturn(version.endsWith("SNAPSHOT") || version.equals("LATEST"));
		when(artifact.getType()).thenReturn(type);
		when(artifact.getClassifier()).thenReturn(classifier);
		when(artifact.getArtifactHandler()).thenReturn(handlers.getArtifactHandler(type));
		when(artifact.getFile()).thenReturn(file);
		return artifact;
	}

	@ParameterizedTest
	@CsvSource({
			"com, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt",
			"com, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt widget-1-SNAPSHOT-reversed.txt",
			"com, widget, v12345, widget-v12345.pom widget-v12345.txt",
			"com.ex, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt",
			"com.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt widget-1-SNAPSHOT-reversed.txt",
			"com.ex, widget, v12345, widget-v12345.pom widget-v12345.txt",
			"com.ex.ex, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt",
			"com.ex.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt widget-1-SNAPSHOT-reversed.txt",
			"com.ex.ex, widget, v12345, widget-v12345.pom widget-v12345.txt",
			"net, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt",
			"net, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt widget-1-SNAPSHOT-reversed.txt",
			"net, widget, v12345, widget-v12345.pom widget-v12345.txt",
			"net.ex, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt",
			"net.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt widget-1-SNAPSHOT-reversed.txt",
			"net.ex, widget, v12345, widget-v12345.pom widget-v12345.txt",
			"net.ex.ex, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt",
			"net.ex.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt widget-1-SNAPSHOT-reversed.txt",
			"net.ex.ex, widget, v12345, widget-v12345.pom widget-v12345.txt"
	})
	void findsDeployables(String groupId, String artifactId, String version, String deployableFilenames) {
		final Path layoutPrefix = getLayoutPrefix(groupId, artifactId, version);
		final Artifact artifact = getMockPomArtifact(groupId, artifactId, version);
		ArtifactGroup preGroup = new ArtifactGroup(layoutPrefix, artifact);
		ArtifactGroup group = preGroup.findDeployables(handlers);
		final Set<Path> deployableKeys = group.getDeployables().keySet();
		assertEquals(Stream.of(deployableFilenames.split("\\s+")).map(Paths::get).collect(Collectors.toSet()),
				deployableKeys, "expect same deployables");

		assertSame(preGroup.getPomArtifact(), group.getPomArtifact(), "expect same pom artifact");
		assertEquals(preGroup.getGav(), group.getGav(), "expect same gav");
		assertEquals(preGroup.getLayoutPrefix(), group.getLayoutPrefix(), "expect same layoutPrefix");
		assertEquals(preGroup.isSnapshot(), group.isSnapshot(), "expect same isSnapshot");
		assertEquals(preGroup.nonSnapshot(), group.nonSnapshot(), "expect same nonSnapshot");
		assertEquals(preGroup.getIndexed(), group.getIndexed(), "expect equal indexed");
		assertEquals(preGroup.isTerminateOnFailure(), group.isTerminateOnFailure(), "expect equal isTerminateOnFailure");
	}

	@ParameterizedTest
	@CsvSource({
			"com, widget, 1, widget-1.pom widget-1.txt widget-1-reversed.txt, 0",
			"com, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.txt, 1",
			"com, widget, v12345, widget-v12345.pom widget-v12345.txt, 0",
			"com.ex, widget, 1, widget-1.pom widget-1.txt widget-1-desrever.txt, 1",
			"com.ex, widget, 1-SNAPSHOT, , 3",
			"com.ex, widget, v12345, , 2",
			"com.ex.ex, widget, 1, widget-1.txt widget-1.pom widget-1-reversed.txt, 0",
			"com.ex.ex, widget, 1-SNAPSHOT, widget-1-SNAPSHOT.pom widget-1-SNAPSHOT.pom widget-1-SNAPSHOT-reversed.txt, 1",
			"com.ex.ex, widget, v12345, widget-v12345.pom widget-1-SNAPSHOT.txt, 1"
	})
	void filtersByIndex(String groupId, String artifactId, String version, String indexedFilenames, int expectRemaining) {
		final Path layoutPrefix = getLayoutPrefix(groupId, artifactId, version);
		final Artifact artifact = getMockPomArtifact(groupId, artifactId, version);
		ArtifactGroup preGroup = new ArtifactGroup(layoutPrefix, artifact);
		ArtifactGroup group = preGroup.findDeployables(handlers);
		final Set<Path> origDeployables = new HashSet<>(group.getDeployables().keySet());
		final List<Path> expectIndexed = indexedFilenames == null
				? Collections.emptyList()
				: Stream.of(indexedFilenames.split("\\s+")).map(Paths::get).collect(Collectors.toList());
		ArtifactGroup indexedGroup = group.filteredByIndex(expectIndexed);
		final Set<Path> expectDeployables = new HashSet<>(origDeployables);
		expectDeployables.removeAll(expectIndexed);
		assertEquals(new HashSet<>(expectIndexed), indexedGroup.getIndexed(), "expect equal indexed");
		assertEquals(expectDeployables, indexedGroup.getDeployables().keySet(), "expect equal deployable keys");
		assertEquals(expectRemaining, indexedGroup.getDeployables().size(), "expect equal deployable size");

		assertSame(preGroup.getPomArtifact(), indexedGroup.getPomArtifact(), "expect same pom artifact");
		assertEquals(preGroup.getGav(), indexedGroup.getGav(), "expect same gav");
		assertEquals(preGroup.getLayoutPrefix(), indexedGroup.getLayoutPrefix(), "expect same layoutPrefix");
		assertEquals(preGroup.isSnapshot(), indexedGroup.isSnapshot(), "expect same isSnapshot");
		assertEquals(preGroup.nonSnapshot(), indexedGroup.nonSnapshot(), "expect same nonSnapshot");
		assertEquals(preGroup.isTerminateOnFailure(), indexedGroup.isTerminateOnFailure(), "expect same nonSnapshot");
	}

	@ParameterizedTest
	@CsvSource({
			"com, widget, 1, false, false",
			"com, widget, 1-SNAPSHOT, true, false",
			"com, widget, v12345, false, true",
			"com.ex, widget, 1, false, true",
			"com.ex, widget, 1-SNAPSHOT, true, true",
			"com.ex, widget, v12345, false, false",
			"com.ex.ex, widget, 1, true, false",
			"com.ex.ex, widget, 1-SNAPSHOT, false, false",
			"com.ex.ex, widget, v12345, true, true",
			"net, widget, 1, true, true",
			"net, widget, 1-SNAPSHOT, false, true",
			"net, widget, v12345, true, false"
	})
	void marksAsTerminateOnFailure(String groupId, String artifactId, String version, boolean firstMark, boolean secondMark) {
		final Path layoutPrefix = getLayoutPrefix(groupId, artifactId, version);
		final Artifact artifact = getMockPomArtifact(groupId, artifactId, version);
		ArtifactGroup preGroup = new ArtifactGroup(layoutPrefix, artifact).findDeployables(handlers);
		assertFalse(preGroup.isTerminateOnFailure(), "expect default false isTerminateOnFailure");
		ArtifactGroup group = preGroup.findDeployables(handlers);
		assertFalse(group.isTerminateOnFailure(), "expect carryover false isTerminateOnFailure");
		ArtifactGroup firstGroup = group.markTerminateOnFailure(firstMark);
		assertEquals(firstMark, firstGroup.isTerminateOnFailure(), "expect first new isTerminateOnFailure: " + firstMark);
		ArtifactGroup secondGroup = firstGroup.markTerminateOnFailure(secondMark);
		assertEquals(secondMark, secondGroup.isTerminateOnFailure(), "expect second new isTerminateOnFailure: " + secondMark);

		assertSame(preGroup.getPomArtifact(), secondGroup.getPomArtifact(), "expect same pom artifact");
		assertEquals(preGroup.getGav(), secondGroup.getGav(), "expect same gav");
		assertEquals(preGroup.getLayoutPrefix(), secondGroup.getLayoutPrefix(), "expect same layoutPrefix");
		assertEquals(preGroup.isSnapshot(), secondGroup.isSnapshot(), "expect same isSnapshot");
		assertEquals(preGroup.nonSnapshot(), secondGroup.nonSnapshot(), "expect same nonSnapshot");
		assertEquals(group.getIndexed(), secondGroup.getIndexed(), "expect equal indexed");
		assertEquals(group.getDeployables(), secondGroup.getDeployables(), "expect equal deployables");
	}
}