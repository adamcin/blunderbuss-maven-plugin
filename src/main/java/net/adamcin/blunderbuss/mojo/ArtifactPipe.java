package net.adamcin.blunderbuss.mojo;

import io.reactivex.rxjava3.core.Flowable;
import org.jetbrains.annotations.NotNull;

public interface ArtifactPipe {
	@NotNull Flowable<ArtifactGroup> attachPipe(@NotNull Flowable<ArtifactGroup> artifactGroups);
}
