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

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Function;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoFailureException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class IndexBuilder {
	private final Path indexDir;

	private final Artifact indexBuilderMetadataArtifact;

	private final Artifact indexBuilderArtifact;

	private final Gav indexGav;

	private final Context context;

	public IndexBuilder(
			@NotNull final Path indexDir,
			@NotNull final Artifact indexBuilderArtifact,
			@NotNull final Artifact indexBuilderMetadataArtifact,
			@NotNull final Context context) {
		this.indexDir = indexDir;
		this.indexBuilderArtifact = indexBuilderArtifact;
		this.indexBuilderMetadataArtifact = indexBuilderMetadataArtifact;
		this.indexGav = Gav.fromArtifact(indexBuilderArtifact);
		this.context = context;
	}

	public static Single<IndexBuilder> fromIndex(@NotNull final Index index, @NotNull final Context context) {
		return Single.create(emitter -> {
			final Artifact indexArtifact = index.getIndexArtifact();
			final Artifact indexMetadataArtifact = index.getIndexMetadataArtifact();
			final String groupId = indexArtifact.getGroupId();
			final String artifactId = indexArtifact.getArtifactId();
			final String version = DateTimeFormatter.ofPattern("'v'uuuuMMddHHmmss").format(ZonedDateTime.now(Clock.systemUTC()));
			final Path tempDir = context.getTempDir();
			final File pomFile = tempDir.resolve(artifactId + "-" + version + ".pom").toFile();
			final File jarFile = tempDir.resolve(artifactId + "-" + version + ".jar").toFile();
			final Path indexDir = tempDir.resolve(artifactId + "-" + version + ".dir");
			final Model model = new Model();
			model.setModelVersion("4.0.0");
			model.setGroupId(groupId);
			model.setArtifactId(artifactId);
			model.setVersion(version);
			final MavenXpp3Writer writer = new MavenXpp3Writer();
			try (OutputStream outputStream = new FileOutputStream(pomFile)) {
				writer.write(outputStream, model);
			}
			if (indexArtifact.getFile() == null || !indexArtifact.getFile().isFile()) {
				Files.createDirectories(indexDir);
			} else {
				JarUtils.extractJarFile(indexArtifact.getFile(), indexDir).blockingAwait();
			}
			final Artifact indexBuilderArtifact = new DefaultArtifact(groupId, artifactId, version, indexArtifact.getScope(),
					indexArtifact.getType(), indexArtifact.getClassifier(), indexArtifact.getArtifactHandler());
			indexBuilderArtifact.setFile(jarFile);
			final Artifact indexBuilderMetadataArtifact = new DefaultArtifact(groupId, artifactId, version, indexMetadataArtifact.getScope(),
					indexMetadataArtifact.getType(), indexMetadataArtifact.getClassifier(), indexMetadataArtifact.getArtifactHandler());
			indexBuilderMetadataArtifact.setFile(pomFile);
			emitter.onSuccess(new IndexBuilder(indexDir, indexBuilderArtifact, indexBuilderMetadataArtifact, context));
		});
	}

	public List<Artifact> getArtifacts() {
		return Arrays.asList(indexBuilderMetadataArtifact, indexBuilderArtifact);
	}

	private final Stats NOOP = this.new Stats(0, false);

	private final Stats DIRTY = this.new Stats(0, true);

	private final Stats FAILED = this.new Stats(1, false);

	Stats noop() {
		return NOOP;
	}

	Stats dirty() {
		return DIRTY;
	}

	Stats failed() {
		return FAILED;
	}

	public final class Stats {

		private final int failures;

		private final boolean dirty;

		Stats(final int failures, final boolean dirty) {
			this.failures = failures;
			this.dirty = dirty;
		}

		public int getFailures() {
			return failures;
		}

		public boolean isDirty() {
			return dirty;
		}

		public IndexBuilder getBuilder() {
			return IndexBuilder.this;
		}

		public Stats combine(@Nullable final Stats other) {
			if (other == null || other == NOOP) {
				return this;
			} else if (this == NOOP) {
				return other;
			} else if (this.dirty && other == DIRTY) {
				return this;
			} else if (this == DIRTY && other.dirty) {
				return other;
			} else {
				return new Stats(this.failures + other.failures, this.dirty || other.dirty);
			}
		}
	}

	Function<ArtifactGroup, Stats> getUploadFunction() {
		return artifactGroup -> {
			final Set<Path> indexed = new HashSet<>(artifactGroup.getIndexed());
			final Map<Path, Artifact> deployables = artifactGroup.getDeployables().entrySet().stream()
					.filter(entry -> !indexed.contains(entry.getKey()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			// we never index snapshots, and we shouldn't overwrite index files if we don't have anything to upload
			boolean doSave = artifactGroup.nonSnapshot() && !deployables.isEmpty();
			Stats stats = noop();
			if (!deployables.isEmpty()) {
				try {
					context.syncAll(artifactGroup.getGav(), deployables);
					indexed.addAll(deployables.keySet());
				} catch (Context.SyncFailure syncFailure) {
					stats = failed();
					doSave = false;
					if (artifactGroup.isFailOnError()) {
						throw new MojoFailureException("Required artifact failed to deploy: " + artifactGroup.getGav(),
								syncFailure);
					}
				}
			}
			if (doSave) {
				Path indexFile = indexDir.resolve(artifactGroup.getIndexFileRelPath());
				if (!Files.isDirectory(indexFile.getParent())) {
					Files.createDirectories(indexFile.getParent());
				}
				Files.write(indexFile, indexed.stream().map(Path::toString)
						.collect(Collectors.toList()), StandardCharsets.UTF_8);
				return dirty();
			}
			return stats;
		};
	}

	Single<Stats> buildIndexFrom(@NotNull final Flowable<ArtifactGroup> artifactGroups) {
		return artifactGroups
				.parallel()
				.map(getUploadFunction())
				.sequential()
				.reduce(noop(), Stats::combine);
	}

	Completable finishAndUpload(@NotNull final Stats stats, final boolean noUpload) {
		return JarUtils.createJarFile(indexBuilderArtifact.getFile(), indexDir)
				.andThen(Completable.create(emitter -> {
					if (stats.isDirty() && !noUpload) {
						context.deploy(this.indexGav, this.getArtifacts());
					}
					if (stats.getFailures() > 0) {
						emitter.onError(new MojoFailureException("Failed to upload or resolve at least " + stats.getFailures() + " artifacts."));
					} else {
						emitter.onComplete();
					}
				}));
	}

}
