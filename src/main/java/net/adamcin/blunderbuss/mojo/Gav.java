package net.adamcin.blunderbuss.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Gav {
	private final @NotNull String groupId;

	private final @NotNull String artifactId;

	private final @NotNull String version;

	public Gav(@NotNull final String groupId, @NotNull final String artifactId, @NotNull final String version) {
		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
	}

	public String getGroupId() {
		return groupId;
	}

	public String getArtifactId() {
		return artifactId;
	}

	public String getVersion() {
		return version;
	}

	public boolean isSnapshot() {
		return version.endsWith("SNAPSHOT") || version.endsWith("LATEST");
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final Gav gav = (Gav) o;
		return groupId.equals(gav.groupId) &&
				artifactId.equals(gav.artifactId) &&
				version.equals(gav.version);
	}

	@Override
	public int hashCode() {
		return Objects.hash(groupId, artifactId, version);
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId + ":" + version;
	}

	public static Gav fromArtifact(final @NotNull Artifact artifact) {
		return new Gav(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
	}

	public static Gav fromProject(final @NotNull MavenProject project) {
		return new Gav(project.getGroupId(), project.getArtifactId(), project.getVersion());
	}
}
