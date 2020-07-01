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
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployer;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployerException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

class Context {
	private final @NotNull ArtifactResolver artifactResolver;

	private final @NotNull ArtifactDeployer artifactDeployer;

	private final @NotNull ArtifactRepository deployRepo;

	private final @NotNull ProjectBuildingRequest buildRequest;

	private final @NotNull Path tempDir;

	private final @NotNull Log log;

	public Context(@NotNull final ArtifactResolver artifactResolver,
			@NotNull final ArtifactDeployer artifactDeployer,
			@NotNull final ArtifactRepository deployRepo,
			@NotNull final ProjectBuildingRequest buildRequest,
			@NotNull final Path tempDir,
			@NotNull final Log log) {
		this.artifactResolver = artifactResolver;
		this.artifactDeployer = artifactDeployer;
		this.deployRepo = deployRepo;
		this.buildRequest = buildRequest;
		this.tempDir = tempDir;
		this.log = log;
	}

	public ArtifactResolver getArtifactResolver() {
		return artifactResolver;
	}

	public ArtifactDeployer getArtifactDeployer() {
		return artifactDeployer;
	}

	public ArtifactRepository getDeployRepo() {
		return deployRepo;
	}

	public ProjectBuildingRequest getBuildRequest() {
		return buildRequest;
	}

	public Path getTempDir() {
		return tempDir;
	}

	public Log getLog() {
		return log;
	}

	public void deploy(@NotNull final Artifact... artifacts) throws ArtifactDeployerException {
		getArtifactDeployer().deploy(getBuildRequest(), getDeployRepo(), Arrays.asList(artifacts));
	}

	public void deploy(@NotNull final Collection<Artifact> artifacts) throws ArtifactDeployerException {
		getArtifactDeployer().deploy(getBuildRequest(), getDeployRepo(), artifacts);
	}

	public Artifact resolve(@NotNull final Artifact artifact) throws ArtifactResolverException {
		return getArtifactResolver().resolveArtifact(getBuildRequest(), artifact).getArtifact();
	}
}
