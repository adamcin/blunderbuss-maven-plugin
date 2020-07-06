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
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

class Context {
	private final @NotNull ArtifactResolver artifactResolver;

	private final @NotNull ArtifactDeployer artifactDeployer;

	private final @NotNull ArtifactRepository releaseRepo;

	private final @Nullable ArtifactRepository snapshotRepo;

	private final @NotNull ProjectBuildingRequest buildRequest;

	private final @NotNull Path tempDir;

	private final @NotNull Log log;

	public Context(@NotNull final ArtifactResolver artifactResolver,
			@NotNull final ArtifactDeployer artifactDeployer,
			@NotNull final ArtifactRepository releaseRepo,
			@Nullable final ArtifactRepository snapshotRepo,
			@NotNull final ProjectBuildingRequest buildRequest,
			@NotNull final Path tempDir,
			@NotNull final Log log) {
		this.artifactResolver = artifactResolver;
		this.artifactDeployer = artifactDeployer;
		this.releaseRepo = releaseRepo;
		this.snapshotRepo = snapshotRepo;
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

	public @NotNull ArtifactRepository getReleaseRepo() {
		return releaseRepo;
	}

	public @Nullable ArtifactRepository getSnapshotRepo() {
		return snapshotRepo;
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

	public void deploy(@NotNull final Gav gav, @NotNull final Artifact... artifacts) throws ArtifactDeployerException {
		this.deploy(gav, Arrays.asList(artifacts));
	}

	public void deploy(@NotNull final Gav gav, @NotNull final Collection<Artifact> artifacts) throws ArtifactDeployerException {
		if (gav.isSnapshot()) {
			if (getSnapshotRepo() == null) {
				throw new ArtifactDeployerException(
						"No snapshot deployment repository is specified. Use -DaltSnapshotDeploymentRepository or -DaltDeploymentRepository.",
						new NullPointerException("snapshotRepo"));
			}
			getArtifactDeployer().deploy(getBuildRequest(), getSnapshotRepo(), artifacts);
		} else {
			getArtifactDeployer().deploy(getBuildRequest(), getReleaseRepo(), artifacts);
		}
	}

	public Artifact resolve(@NotNull final Artifact artifact) throws ArtifactResolverException {
		return getArtifactResolver().resolveArtifact(getBuildRequest(), artifact).getArtifact();
	}

	public void syncAll(@NotNull final Gav gav, @NotNull final Map<Path, Artifact> deployables) throws SyncFailure {
		try {
			this.deploy(gav, deployables.values());
		} catch (ArtifactDeployerException deployAllError) {
			if (gav.isSnapshot() && !deployables.isEmpty()) {
				final Map.Entry<Path, Artifact> firstArtifact = deployables.entrySet().iterator().next();
				throw new SyncFailure(deployAllError, firstArtifact.getKey(), firstArtifact.getValue());
			}
			for (Map.Entry<Path, Artifact> deployableEntry : deployables.entrySet()) {
				try {
					this.resolve(deployableEntry.getValue());
				} catch (ArtifactResolverException resolveOneError) {
					try {
						this.deploy(gav, deployableEntry.getValue());
					} catch (ArtifactDeployerException deployOneError) {
						throw new SyncFailure(deployOneError, deployableEntry.getKey(), deployableEntry.getValue());
					}
				}
			}
		}
	}

	public static class SyncFailure extends Exception {
		private final Path relPath;

		private final Artifact artifact;

		public SyncFailure(final Throwable cause, final Path relPath, final Artifact artifact) {
			super(cause);
			this.relPath = relPath;
			this.artifact = artifact;
		}

		public Path getRelPath() {
			return relPath;
		}

		public Artifact getArtifact() {
			return artifact;
		}
	}

}
