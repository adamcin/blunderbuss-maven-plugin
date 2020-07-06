package net.adamcin.blunderbuss.mojo;

import io.reactivex.rxjava3.core.Flowable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class ReactorFilter implements ArtifactPipe {
	@SuppressWarnings("unused")
	private final Context context;

	private final Set<Gav> reactorGavs;

	private final boolean reactorAware;

	private final boolean reactorDeploySnapshots;

	public ReactorFilter(
			@NotNull final Context context,
			@NotNull final Collection<Gav> reactorGavs,
			final boolean reactorAware,
			final boolean reactorDeploySnapshots) {
		this.context = context;
		this.reactorGavs = new HashSet<>(reactorGavs);
		this.reactorAware = reactorAware;
		this.reactorDeploySnapshots = reactorDeploySnapshots;
	}

	public boolean isReactorDeployable(@NotNull final ArtifactGroup artifactGroup) {
		return reactorAware && (reactorGavs.contains(artifactGroup.getGav()))
				&& (reactorDeploySnapshots || artifactGroup.nonSnapshot());
	}

	ArtifactGroup transformReactorArtifact(@NotNull final ArtifactGroup artifactGroup) {
		if (reactorAware && isReactorDeployable(artifactGroup)) {
			return artifactGroup.markFailOnError(true);
		}
		return artifactGroup;
	}

	@Override
	public @NotNull Flowable<ArtifactGroup> attachPipe(@NotNull final Flowable<ArtifactGroup> artifactGroups) {
		return artifactGroups
				.filter(artifactGroup -> artifactGroup.nonSnapshot() || isReactorDeployable(artifactGroup))
				.map(this::transformReactorArtifact);
	}
}
