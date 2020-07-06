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

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.functions.Function;
import io.reactivex.rxjava3.functions.Predicate;
import io.reactivex.rxjava3.schedulers.Schedulers;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.logging.Log;
import org.jetbrains.annotations.NotNull;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public class Index implements ArtifactPipe {
	private final Log log;

	private final Artifact indexArtifact;

	private final Artifact indexMetadataArtifact;

	public Index(@NotNull final Log log, @NotNull final Artifact indexArtifact, @NotNull final Artifact indexMetadataArtifact) {
		this.log = log;
		this.indexArtifact = indexArtifact;
		this.indexMetadataArtifact = indexMetadataArtifact;
	}

	public Artifact getIndexArtifact() {
		return indexArtifact;
	}

	public Artifact getIndexMetadataArtifact() {
		return indexMetadataArtifact;
	}

	Function<ArtifactGroup, Flowable<ArtifactGroup>> applyFilter(@NotNull final JarFile jarFile) throws Exception {
		return artifactGroup -> {
			JarEntry entry = jarFile.getJarEntry(artifactGroup.getLayoutPrefix().toString() + ".txt");
			if (entry != null) {
				try (Reader indexReader = new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8)) {
					List<String> lines = IOUtils.readLines(indexReader);
					List<Path> contents = lines.stream().map(Paths::get).collect(Collectors.toList());
					final ArtifactGroup newGroup = artifactGroup.filteredByIndex(contents);
					if (newGroup.getDeployables().isEmpty()) {
						return Flowable.empty();
					} else {
						return Flowable.just(newGroup);
					}
				} catch (Exception e) {
					log.info("failed to read index for artifact group: " + artifactGroup.getLayoutPrefix().toString(), e);
				}
			}
			return Flowable.just(artifactGroup);
		};
	}

	Predicate<ArtifactGroup> getNotMyselfPredicate() {
		return artifactGroup -> {
			return !(this.indexMetadataArtifact.getGroupId().equals(artifactGroup.getPomArtifact().getGroupId())
					&& this.indexMetadataArtifact.getArtifactId().equals(artifactGroup.getPomArtifact().getArtifactId()));
		};
	}

	@Override
	@NotNull
	public Flowable<ArtifactGroup> attachPipe(@NotNull final Flowable<ArtifactGroup> artifactGroups) {
		if (indexArtifact.getFile() == null) {
			return artifactGroups.filter(getNotMyselfPredicate());
		}
		return Flowable.create(emitter -> {
			final JarFile jarFile = new JarFile(indexArtifact.getFile());
			final Disposable disposable = artifactGroups
					.filter(getNotMyselfPredicate())
					.flatMap(this.applyFilter(jarFile))
					.observeOn(Schedulers.io())
					.subscribe(emitter::onNext, emitter::onError, emitter::onComplete);
			emitter.setDisposable(new CompositeDisposable(disposable, Disposable.fromAutoCloseable(jarFile)));
		}, BackpressureStrategy.BUFFER);
	}
}
