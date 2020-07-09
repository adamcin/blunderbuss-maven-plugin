package net.adamcin.blunderbuss.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GavTest {

	@ParameterizedTest(name = "Gav fromArtifact {0}:{1}:{2}")
	@CsvSource({
			"mygroup, myartifact, 1.0"
	})
	void constructsFromArtifact(String groupId, String artifactId, String version) {
		Artifact artifact = mock(Artifact.class);
		when(artifact.getGroupId()).thenReturn(groupId);
		when(artifact.getArtifactId()).thenReturn(artifactId);
		when(artifact.getVersion()).thenReturn(version);
		Gav gav = Gav.fromArtifact(artifact);
		assertEquals(groupId, gav.getGroupId(), "expect groupId");
		assertEquals(artifactId, gav.getArtifactId(), "expect artifactId");
		assertEquals(version, gav.getVersion(), "expect version");
	}

	@ParameterizedTest(name = "Gav fromProject {0}:{1}:{2}")
	@CsvSource({
			"mygroup, myartifact, 1.0"
	})
	void constructsFromProject(String groupId, String artifactId, String version) {
		MavenProject project = new MavenProject();
		project.setGroupId(groupId);
		project.setArtifactId(artifactId);
		project.setVersion(version);
		Gav gav = Gav.fromProject(project);
		assertEquals(groupId, gav.getGroupId(), "expect groupId");
		assertEquals(artifactId, gav.getArtifactId(), "expect artifactId");
		assertEquals(version, gav.getVersion(), "expect version");
	}

	@ParameterizedTest(name = "Gav constructor {0}:{1}:{2}")
	@CsvSource({
			"mygroup, myartifact, 1.0"
	})
	void constructor(String groupId, String artifactId, String version) {
		Gav gav = new Gav(groupId, artifactId, version);
		assertEquals(groupId, gav.getGroupId(), "expect groupId");
		assertEquals(artifactId, gav.getArtifactId(), "expect artifactId");
		assertEquals(version, gav.getVersion(), "expect version");
	}

	@ParameterizedTest(name = "Gav toString {0}:{1}:{2}")
	@CsvSource({
			"mygroup, myartifact, 1.0"
	})
	void gavToString(String groupId, String artifactId, String version) {
		Gav gav = new Gav(groupId, artifactId, version);
		assertEquals(String.format("%s:%s:%s", groupId, artifactId, version), gav.toString(), "expect toString");
	}

	@ParameterizedTest(name = "Gav isSnapshot {0}:{1}:{2} ? {3}")
	@CsvSource({
			"mygroup, myartifact, 1.0, false",
			"mygroup, myartifact, 1.0-SNAPSHOT, true",
			"mygroup, myartifact, LATEST, true",
	})
	void gavIsSnapshot(String groupId, String artifactId, String version, boolean expectIsSnapshot) {
		Gav gav = new Gav(groupId, artifactId, version);
		assertEquals(expectIsSnapshot, gav.isSnapshot(), String.format("expect %s %s", gav, expectIsSnapshot ? "is snapshot" : "is not snapshot"));
	}

	@ParameterizedTest(name = "Gav equalsHashCode {0}:{1}:{2} == {3}:{4}:{5} ? {6}")
	@CsvSource({
			"mygroup, myartifact, 1.0, mygroup, myartifact, 1.0, true",
			"mygroup, myartifact, 1.0, mygroup, myartifact, 1.5, false",
			"mygroup, myartifact, 1.0, mygroup, noartifact, 1.0, false",
			"mygroup, myartifact, 1.0, nogroup, myartifact, 1.0, false",
	})
	void gavEqualsHashCode(
			String groupId, String artifactId, String version,
			String otherGroupId, String otherArtifactId, String otherVersion, boolean expectEquals) {
		final Gav gav = new Gav(groupId, artifactId, version);
		final Gav otherGav = new Gav(otherGroupId, otherArtifactId, otherVersion);
		final String gavString = gav.toString();
		assertTrue(gav.equals(gav), "expect instance equals itself");
		assertFalse(gav.equals(null), "expect instance not equals null");
		assertFalse(gav.equals(gavString), "expect instance not equals a String");
		assertEquals(expectEquals, gav.equals(otherGav), "expect instance " + (expectEquals ? "equals" : "not equals"));
		if (expectEquals) {
			assertEquals(gav.hashCode(), otherGav.hashCode(), "expect hashCode equals");
		} else {
			assertNotEquals(gav.hashCode(), otherGav.hashCode(), "expect hashCode not equals");
		}
	}
}