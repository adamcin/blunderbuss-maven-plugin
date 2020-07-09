package net.adamcin.blunderbuss.mojo;

import io.reactivex.rxjava3.core.BackpressureStrategy;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subscribers.TestSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CachedArtifactVisitorTest {
	final Path baseDir = Paths.get("target", "test-out", "CachedArtifactVisitorTest").toAbsolutePath();

	private DefaultArtifactHandlers handlers = new DefaultArtifactHandlers();

	@BeforeEach
	void setUp() throws Exception {
		Files.createDirectories(baseDir);
	}

	@Test
	void walksLocalRepo() {
		final Observable<ArtifactGroup> artifactGroups =
				Observable.create(emitter -> CachedArtifactVisitor.walkLocalRepo(handlers,
						Paths.get("src/test/resources/repo1"), emitter).onComplete());

		TestSubscriber<Path> subscriber = new TestSubscriber<>();
		artifactGroups.toFlowable(BackpressureStrategy.BUFFER)
				.map(ArtifactGroup::getLayoutPrefix)
				.subscribe(subscriber);

		subscriber.assertComplete();
		subscriber.assertNoErrors();
		subscriber.assertValueCount(18);
		final Set<Path> expected = Stream.of(Paths.get("com"), Paths.get("net"))
				.flatMap(before -> Stream.of(before, before.resolve("ex"), before.resolve("ex").resolve("ex")))
				.map(before -> before.resolve("widget"))
				.flatMap(before -> Stream.of(before.resolve("1"), before.resolve("1-SNAPSHOT"), before.resolve("v12345")))
				.collect(Collectors.toSet());

		assertEquals(expected, new HashSet<>(subscriber.values()), "expect prefix paths");
	}
}