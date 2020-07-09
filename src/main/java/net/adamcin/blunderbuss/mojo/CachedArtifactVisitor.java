package net.adamcin.blunderbuss.mojo;

import io.reactivex.rxjava3.core.Emitter;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.repository.metadata.ArtifactRepositoryMetadata;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

public final class CachedArtifactVisitor extends SimpleFileVisitor<Path> {
	private static final String POM_TYPE = "pom";

	private static final String POM_EXT = "." + POM_TYPE;

	private final ArtifactHandler pomArtifactHandler;

	private final Path localRepoPath;

	private final Emitter<ArtifactGroup> emitter;

	public CachedArtifactVisitor(
			@NotNull final ArtifactHandler pomArtifactHandler,
			@NotNull final Path localRepoPath,
			@NotNull final Emitter<ArtifactGroup> emitter) {
		this.pomArtifactHandler = pomArtifactHandler;
		this.localRepoPath = localRepoPath;
		this.emitter = emitter;
	}

	@Override
	public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
		if (file.toString().endsWith(POM_EXT)) {
			final String artifactId = file.getParent().getParent().toFile().getName();
			final String version = file.getParent().toFile().getName();
			if (file.toString().endsWith(artifactId + "-" + version + POM_EXT)) {
				final String groupId = localRepoPath.relativize(file.getParent().getParent().getParent())
						.toString().replace('/', '.');
				final DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, version,
						"import", POM_TYPE, null, pomArtifactHandler);
				artifact.setFile(file.toFile());
				artifact.addMetadata(new ArtifactRepositoryMetadata(artifact));
				emitter.onNext(new ArtifactGroup(localRepoPath.relativize(file.getParent()), artifact));
				return FileVisitResult.SKIP_SIBLINGS;
			}
		}
		return FileVisitResult.CONTINUE;
	}

	public static <E extends Emitter<ArtifactGroup>> E walkLocalRepo(
			@NotNull final ArtifactHandlerManager artifactHandlerManager,
			@NotNull final Path localRepoPath,
			@NotNull final E emitter) throws IOException {
		Files.walkFileTree(localRepoPath,
				new CachedArtifactVisitor(artifactHandlerManager.getArtifactHandler(POM_TYPE),
						localRepoPath, emitter));
		return emitter;
	}
}
