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
import io.reactivex.rxjava3.core.CompletableEmitter;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.functions.Consumer;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployerException;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolverException;
import org.jetbrains.annotations.NotNull;

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

public class IndexBuilder {
	private final Path indexDir;

	private final Artifact indexBuilderMetadataArtifact;

	private final Artifact indexBuilderArtifact;

	private final Context context;

	private volatile boolean dirty;

	public IndexBuilder(
			@NotNull final Path indexDir,
			@NotNull final Artifact indexBuilderArtifact,
			@NotNull final Artifact indexBuilderMetadataArtifact,
			@NotNull final Context context) {
		this.indexDir = indexDir;
		this.indexBuilderArtifact = indexBuilderArtifact;
		this.indexBuilderMetadataArtifact = indexBuilderMetadataArtifact;
		this.context = context;
	}

	public static Single<IndexBuilder> fromIndex(
			@NotNull final Index index,
			@NotNull final Context context) {
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

	Consumer<ArtifactGroup> onNextArtifactGroup(@NotNull final CompletableEmitter emitter) {
		return artifactGroup -> {
			final Set<Path> indexed = new HashSet<>(artifactGroup.getIndexed());
			final Map<Path, Artifact> deployables = artifactGroup.getDeployables().entrySet().stream()
					.filter(entry -> !indexed.contains(entry.getKey()))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			boolean doSave = !deployables.isEmpty();
			try {
				if (!deployables.isEmpty()) {
					context.deploy(deployables.values());
					indexed.addAll(deployables.keySet());
				}
			} catch (ArtifactDeployerException deployAllError) {
				for (Map.Entry<Path, Artifact> deployableEntry : deployables.entrySet()) {
					try {
						context.resolve(deployableEntry.getValue());
						indexed.add(deployableEntry.getKey());
					} catch (ArtifactResolverException resolveOneError) {
						try {
							context.deploy(deployableEntry.getValue());
							indexed.add(deployableEntry.getKey());
						} catch (ArtifactDeployerException deployOneError) {
							doSave = false;
							break;
						}
					}
				}
			}
			if (doSave) {
				Path indexFile = indexDir.resolve(artifactGroup.getIndexFileRelPath());
				if (!Files.isDirectory(indexFile.getParent())) {
					Files.createDirectories(indexFile.getParent());
				}
				Files.write(indexFile, indexed.stream().map(Path::toString).collect(Collectors.toList()), StandardCharsets.UTF_8);
				dirty = true;
			}
		};
	}

	Completable buildIndexFrom(@NotNull final Flowable<ArtifactGroup> artifactGroups) {
		return Completable.create(emitter -> {
			emitter.setDisposable(
					artifactGroups
							.observeOn(Schedulers.io())
							.subscribe(this.onNextArtifactGroup(emitter), emitter::onError, emitter::onComplete));
		});
	}

	Completable finishAndUpload(final boolean noUpload) {
		return JarUtils.createJarFile(indexBuilderArtifact.getFile(), indexDir)
				.andThen(Completable.create(emitter -> {
					if (dirty && !noUpload) {
						context.deploy(this.getArtifacts());
					}
					emitter.onComplete();
				}));
	}

}
