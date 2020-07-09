/*
 * Copyright 2020 Mark Adamcin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.adamcin.blunderbuss.mojo;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.jetbrains.annotations.NotNull;

import java.io.FilenameFilter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArtifactGroup {
	private final Path layoutPrefix;

	private final Artifact pomArtifact;

	private final Gav gav;

	private final Map<Path, Artifact> deployables;

	private final Set<Path> indexed;

	private final boolean terminateOnFailure;

	public ArtifactGroup(@NotNull final Path layoutPrefix, @NotNull final Artifact pomArtifact) {
		this(layoutPrefix, pomArtifact, Collections.emptyMap(), Collections.emptySet(), false);
	}

	public ArtifactGroup(
			@NotNull final Path layoutPrefix,
			@NotNull final Artifact pomArtifact,
			@NotNull final Map<Path, Artifact> deployables,
			@NotNull final Set<Path> indexed,
			final boolean terminateOnFailure) {
		this.layoutPrefix = layoutPrefix;
		this.pomArtifact = pomArtifact;
		this.gav = Gav.fromArtifact(pomArtifact);
		this.deployables = Collections.unmodifiableMap(deployables);
		this.indexed = Collections.unmodifiableSet(indexed);
		this.terminateOnFailure = terminateOnFailure;
	}

	public Path getLayoutPrefix() {
		return layoutPrefix;
	}

	public Path getIndexFileRelPath() {
		return layoutPrefix.getParent().resolve(layoutPrefix.getFileName().toString() + ".txt");
	}

	public Artifact getPomArtifact() {
		return pomArtifact;
	}

	public Gav getGav() {
		return gav;
	}

	public Map<Path, Artifact> getDeployables() {
		return deployables;
	}

	public Set<Path> getIndexed() {
		return indexed;
	}

	public boolean isTerminateOnFailure() {
		return terminateOnFailure;
	}

	public ArtifactGroup findDeployables(@NotNull final ArtifactHandlerManager artifactHandlerManager) {
		final Map<Path, Artifact> newDeployables = new LinkedHashMap<>(this.deployables);
		final Path pomFilename = pomArtifact.getFile().toPath().getFileName();
		if (!newDeployables.containsKey(pomFilename) && !indexed.contains(pomFilename)) {
			newDeployables.put(pomFilename, pomArtifact);
		}
		final String pomFileName = pomArtifact.getFile().getName();
		final String prefix = pomArtifact.getArtifactId() + "-" + pomArtifact.getVersion();
		final FilenameFilter filter = (dir, name) -> !name.equals(pomFileName) && name.startsWith(prefix);
		Arrays.stream(pomArtifact.getFile().getParentFile().listFiles(filter))
				.filter(other -> !indexed.contains(other.toPath().getFileName()) && !other.getName().endsWith(".lastUpdated"))
				.map(other -> {
					final String suffix = other.getName().substring(prefix.length());
					final int firstPeriod = suffix.indexOf('.');
					final String type = suffix.substring(firstPeriod + 1);
					final String classifier = firstPeriod > 0 && suffix.startsWith("-") ? suffix.substring(1, firstPeriod) : "";
					final DefaultArtifact artifact = new DefaultArtifact(pomArtifact.getGroupId(), pomArtifact.getArtifactId(),
							pomArtifact.getVersion(), "compile", type, classifier, artifactHandlerManager.getArtifactHandler(type));
					artifact.setFile(other);
					pomArtifact.getMetadataList().forEach(artifact::addMetadata);
					return artifact;
				}).forEachOrdered(artifact -> newDeployables.put(artifact.getFile().toPath().getFileName(), artifact));
		return new ArtifactGroup(this.layoutPrefix, this.pomArtifact, newDeployables, this.indexed, this.terminateOnFailure);
	}

	public ArtifactGroup filteredByIndex(@NotNull final List<Path> indexed) {
		final Map<Path, Artifact> newDeployables = new LinkedHashMap<>(this.deployables);
		final Set<Path> newIndexed = new HashSet<>(this.indexed);
		for (Path indexPath : indexed) {
			newIndexed.add(indexPath);
			if (newDeployables.containsKey(indexPath)) {
				newDeployables.remove(indexPath);
			}
		}
		return new ArtifactGroup(this.layoutPrefix, this.pomArtifact, newDeployables, newIndexed, this.terminateOnFailure);
	}

	public ArtifactGroup markTerminateOnFailure(final boolean terminateOnFailure) {
		return new ArtifactGroup(this.layoutPrefix, this.pomArtifact, this.deployables, this.indexed, terminateOnFailure);
	}

	public boolean isSnapshot() {
		return getPomArtifact().isSnapshot();
	}

	public boolean nonSnapshot() {
		return !isSnapshot();
	}

}
