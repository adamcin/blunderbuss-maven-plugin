package net.adamcin.blunderbuss.mojo;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;

import java.util.HashMap;
import java.util.Map;

public final class DefaultArtifactHandlers implements ArtifactHandlerManager {
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
	public void addHandlers(final Map<String, ArtifactHandler> handlers) {
		throw new UnsupportedOperationException("addHandlers");
	}
}
