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
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.internal.functions.Functions;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.InvalidRepositoryException;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.DeploymentRepository;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Repository;
import org.apache.maven.model.RepositoryPolicy;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;
import org.apache.maven.shared.transfer.artifact.deploy.ArtifactDeployer;
import org.apache.maven.shared.transfer.artifact.resolve.ArtifactResolver;
import org.codehaus.plexus.util.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Upload the local repository cache.
 */
@Mojo(name = "sync", requiresProject = false, inheritByDefault = false, aggregator = true, requiresOnline = true)
public class SyncMojo extends AbstractMojo {
	private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.+)");

	@Parameter(defaultValue = "${settings}", readonly = true)
	private Settings settings;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession session;

	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	/**
	 * Specifies an alternative repository to which the project artifacts should be deployed (other than those specified
	 * in &lt;distributionManagement&gt;). <br/>
	 * Format: <code>id::url</code>
	 * <dl>
	 * <dt>id</dt>
	 * <dd>The id can be used to pick up the correct credentials from the settings.xml</dd>
	 * <dt>url</dt>
	 * <dd>The location of the repository</dd>
	 * </dl>
	 * <b>Note:</b> In maven-deploy-plugin version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
	 * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
	 * has been removed because Maven 3 only supports Maven 2 repository layout.
	 */
	@Parameter(property = "altDeploymentRepository")
	private String altDeploymentRepository;

	/**
	 * The alternative repository to use when the project has a final version.
	 *
	 * <b>Note:</b> In maven-deploy-plugin version 2.x, the format was <code>id::<i>layout</i>::url</code> where <code><i>layout</i></code>
	 * could be <code>default</code> (ie. Maven 2) or <code>legacy</code> (ie. Maven 1), but since 3.0.0 the layout part
	 * has been removed because Maven 3 only supports Maven 2 repository layout.
	 *
	 * @see SyncMojo#altDeploymentRepository
	 * @since 2.8
	 */
	@Parameter(property = "altReleaseDeploymentRepository")
	private String altReleaseDeploymentRepository;

	@Parameter(name = "noResolveIndex", property = "noResolveIndex")
	private boolean noResolveIndex;

	@Parameter(name = "noDeployIndex", property = "noDeployIndex")
	private boolean noDeployIndex;

	@Parameter(name = "indexGroupId", property = "indexGroupId", required = true)
	private String indexGroupId;

	@Parameter(name = "indexArtifactId", property = "indexArtifactId", required = true)
	private String indexArtifactId;

	/**
	 * Comma separated list of groupId:artifactId coordinates for other indexes managed in the same
	 * deployment repository that will be used as additional filters after the index specified with
	 * {@code indexGroupId} and {@code indexArtifactId}. Each coordinate will be trimmed to nil so
	 * that newlines are tolerated in the configuration element in the pom.
	 * <p>
	 * You may omit the groupId portion, leaving only a colon prefix, in which case the {@code indexGroupId}
	 * parameter will be assumed.
	 */
	@Parameter(name = "altIndex", property = "altIndex")
	private String altIndex;

	@Parameter(property = "blunderbuss.tmpdir")
	private File tempDir;

	@Parameter(property = "blunderbuss.limitArtifactCount")
	private long limitArtifactCount;

	@Component
	private RepositorySystem repositorySystem;

	@Component(role = ArtifactRepositoryLayout.class)
	private Map<String, ArtifactRepositoryLayout> repositoryLayouts;

	@Component
	private ArtifactDeployer artifactDeployer;

	@Component
	private ArtifactResolver artifactResolver;

	private final ArtifactHandlerManager artifactHandlerManager = new ArtifactHandlerManager() {
		private final Map<String, ArtifactHandler> handlers = new HashMap<>();

		@Override
		public ArtifactHandler getArtifactHandler(final String type) {
			if (!handlers.containsKey(type)) {
				synchronized (handlers) {
					DefaultArtifactHandler handler = new DefaultArtifactHandler(type);
					handler.setExtension(type);
					handlers.put(type, handler);
				}
			}
			return handlers.get(type);
		}

		@Override
		public void addHandlers(final Map<String, ArtifactHandler> handlers) { /* do nothing */ }
	};

	RepositoryPolicy getDefaultRepositoryPolicy(boolean enabled) {
		RepositoryPolicy policy = new RepositoryPolicy();
		policy.setEnabled(enabled);
		policy.setUpdatePolicy(ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS);
		policy.setChecksumPolicy(ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);
		return policy;
	}

	Maybe<ArtifactRepository> getAltReleaseDeploymentRepository() {
		return Maybe.<Repository>create(emitter -> {
			Optional<String> altRepo = Stream.of(altReleaseDeploymentRepository, altDeploymentRepository)
					.filter(org.apache.maven.shared.utils.StringUtils::isNotBlank).findFirst();
			if (altRepo.isPresent()) {
				final String altDeploymentRepo = altRepo.get();
				getLog().info("Using alternate deployment repository " + altDeploymentRepo);

				Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(altDeploymentRepo);

				if (!matcher.matches()) {
					emitter.onError(new MojoFailureException(altDeploymentRepo, "Invalid syntax for repository.",
							"Invalid syntax for alternative repository. Use \"id::url\"."));
					return;
				} else {
					final String id = matcher.group(1).trim();
					final String url = matcher.group(2).trim();
					Repository repo = new Repository();
					repo.setReleases(getDefaultRepositoryPolicy(true));
					repo.setSnapshots(getDefaultRepositoryPolicy(false));
					repo.setId(id);
					repo.setUrl(url);
					emitter.onSuccess(repo);
				}
			}
			emitter.onComplete();
		}).flatMap(repo -> Optional.ofNullable(repo).map(this::buildArtifactRepository).map(Single::toMaybe).orElseGet(Maybe::empty));
	}

	Single<ArtifactRepository> buildArtifactRepository(@NotNull final Repository repository) {
		return Single.create(emitter -> {
			if (!StringUtils.isEmpty(repository.getId()) && !StringUtils.isEmpty(repository.getUrl())) {
				ArtifactRepository repo = repositorySystem.buildArtifactRepository(repository);
				repositorySystem.injectProxy(session.getRepositorySession(), Collections.singletonList(repo));
				repositorySystem.injectAuthentication(session.getRepositorySession(), Collections.singletonList(repo));
				emitter.onSuccess(repo);
			} else {
				emitter.onError(new InvalidRepositoryException("repository id and url must not be empty", repository.getId()));
			}
		});
	}

	Single<DistributionManagement> getProjectDistMgmtAsSingle() {
		return Single.<MavenProject>create(emitter -> {
			if (project == null) {
				emitter.onError(new IllegalStateException("No maven project available."));
			} else {
				emitter.onSuccess(project);
			}
		}).flatMap(mavenProject -> Single.create(emitter -> {
			if (mavenProject.getDistributionManagement() == null) {
				emitter.onError(new IllegalStateException("Maven project does not contain a distributionManagement section"));
			} else {
				emitter.onSuccess(mavenProject.getDistributionManagement());
			}
		}));
	}

	Single<ArtifactRepository> getProjectReleaseDeploymentRepository() {
		return getProjectDistMgmtAsSingle().flatMap(distMgmt -> Single.<DeploymentRepository>create(emitter -> {
			if (distMgmt.getRepository() == null) {
				emitter.onError(new IllegalStateException("Maven project distributionManagement does not specify a release repository"));
			} else {
				emitter.onSuccess(distMgmt.getRepository());
			}
		})).flatMap(repository ->
				buildArtifactRepository(repository).onErrorResumeNext(e ->
						Single.error(new IllegalStateException("Failed to create release distribution repository for " + project.getId(), e))));
	}

	Single<ArtifactRepository> getReleaseDeploymentRepository() {
		return getAltReleaseDeploymentRepository().switchIfEmpty(getProjectReleaseDeploymentRepository());
	}

	/**
	 * @return an observable of artifact groups
	 */
	Observable<ArtifactGroup> getArtifactGroups() {
		Observable<ArtifactGroup> observable = Observable.create(emitter -> {
			final Path localRepoPath = session.getRequest().getLocalRepositoryPath().toPath().toAbsolutePath();
			final ArtifactHandler artifactHandler = artifactHandlerManager.getArtifactHandler("pom");
			Files.walkFileTree(localRepoPath, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".pom")) {
						final String artifactId = file.getParent().getParent().toFile().getName();
						final String version = file.getParent().toFile().getName();
						if (!version.endsWith("-SNAPSHOT") && file.toString().endsWith(artifactId + "-" + version + ".pom")) {
							final String groupId = localRepoPath.relativize(file.getParent().getParent().getParent())
									.toString().replace('/', '.');
							final DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, version,
									"import", "pom", null, artifactHandler);
							artifact.setFile(file.toFile());
							artifact.addMetadata(new ArtifactRepositoryMetadata(artifact));
							emitter.onNext(new ArtifactGroup(localRepoPath.relativize(file.getParent()), artifact));
							return FileVisitResult.SKIP_SIBLINGS;
						}
					}
					return FileVisitResult.CONTINUE;
				}
			});
			emitter.onComplete();
		});
		if (limitArtifactCount > 0L) {
			return observable.take(limitArtifactCount);
		} else {
			return observable;
		}
	}

	/**
	 * Stream deployable artifacts grouped into lists where all elements share the same GAV coordinates.
	 *
	 * @return stream of GAV-grouped artifacts
	 */
	Flowable<ArtifactGroup> getDeployableArtifacts(@NotNull final Index index, @NotNull final List<Index> altIndexes) {
		return Observable.concat(Observable.just(index), Observable.fromIterable(altIndexes))
				.reduce(getArtifactGroups().toFlowable(BackpressureStrategy.BUFFER), (flow, idx) -> idx.filterByIndex(flow))
				.toFlowable()
				.flatMap(Functions.identity())
				.map(group -> group.findDeployables(artifactHandlerManager));
	}

	Single<ProjectBuildingRequest> getWrappedProjectBuildingRequest(
			@NotNull final ArtifactRepository deployRepo) {
		DefaultProjectBuildingRequest wrapper = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
		wrapper.setRemoteRepositories(Collections.singletonList(deployRepo));
		return Single.just(wrapper);
	}

	Single<Context> getContext() {
		return getReleaseDeploymentRepository()
				.flatMap(deployRepo -> getWrappedProjectBuildingRequest(deployRepo)
						.flatMap(buildRequest -> getTempDir()
								.map(tempDir -> new Context(artifactResolver, artifactDeployer,
										deployRepo, buildRequest, tempDir.toAbsolutePath(), getLog()))));
	}

	Single<Path> getTempDir() {
		return Single.create(emitter -> {
			if (tempDir != null) {
				emitter.onSuccess(Files.createTempDirectory(tempDir.toPath(), "blunderbuss_"));
			} else if (project != null) {
				final Path projectTempDir = project.getBasedir().toPath().resolve("target/blunderbussTmp");
				emitter.onSuccess(Files.createDirectories(projectTempDir));
			} else {
				emitter.onSuccess(Files.createTempDirectory("blunderbuss_"));
			}
		});
	}

	Single<Index> getIndex(@NotNull final Context context) {
		return internalGetIndex(context, indexGroupId, indexArtifactId, !noResolveIndex);
	}

	Single<Index> internalGetIndex(
			@NotNull final Context context,
			@NotNull final String groupId,
			@NotNull final String artifactId,
			final boolean doResolve) {
		return Single.create(emitter -> {
			Artifact indexArtifact = new DefaultArtifact(groupId, artifactId, Artifact.LATEST_VERSION,
					"test", "jar", "", artifactHandlerManager.getArtifactHandler("jar"));
			Artifact indexMetadataArtifact = new DefaultArtifact(indexGroupId, indexArtifactId, Artifact.LATEST_VERSION,
					"import", "pom", "", artifactHandlerManager.getArtifactHandler("pom"));

			if (doResolve) {
				try {
					indexMetadataArtifact = context.resolve(indexMetadataArtifact);
					ArtifactRepositoryMetadata metaMeta = new ArtifactRepositoryMetadata(indexMetadataArtifact);
					indexMetadataArtifact.addMetadata(metaMeta);
					indexArtifact.addMetadata(metaMeta);
					indexArtifact = context.resolve(indexArtifact);
					context.getLog().info("resolved index file to " + indexArtifact.getFile());
				} catch (Exception e) {
					context.getLog().warn("failed to resolve latest index: " + indexArtifact.toString());
					context.getLog().debug("failed to resolve latest index: " + indexArtifact.toString(), e);
				}
			}

			emitter.onSuccess(new Index(getLog(), indexArtifact, indexMetadataArtifact));
		});
	}

	Single<List<Index>> getAltIndexes(@NotNull final Context context) {
		if (StringUtils.isBlank(altIndex)) {
			return Single.just(Collections.emptyList());
		} else {
			return Observable.fromStream(Arrays.stream(altIndex.split(",")))
					.filter(part -> part.contains(":"))
					.map(String::trim)
					.flatMap(coords -> {
						final String[] elements = coords.split(":");
						final String groupId = StringUtils.isNotEmpty(elements[0]) ? elements[0] : indexGroupId;
						final String artifactId = elements[1];
						if (groupId.equals(indexGroupId) && artifactId.equals(indexArtifactId)) {
							return Observable.empty();
						} else {
							return internalGetIndex(context, groupId, artifactId, true)
									.toObservable();
						}
					})
					.collect(Collectors.toList());
		}
	}

	Completable doExecute() {
		return getContext()
				.flatMap(context -> getIndex(context)
						.flatMap(index -> IndexBuilder.fromIndex(index, context)
								.flatMap(indexBuilder -> getAltIndexes(context)
										.map(altIndexes -> indexBuilder.buildIndexFrom(getDeployableArtifacts(index, altIndexes))
												.andThen(indexBuilder.finishAndUpload(noDeployIndex))))))
				.flatMapCompletable(Functions.identity());
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		Completable execution = doExecute();
		try {
			execution.blockingAwait();
		} catch (Exception e) {
			if (e.getCause() instanceof MojoFailureException) {
				throw (MojoFailureException) e.getCause();
			}
			throw new MojoExecutionException(e.getMessage(), e.getCause());
		}
	}

}
