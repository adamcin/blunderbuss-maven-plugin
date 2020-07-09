package net.adamcin.blunderbuss.mojo;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultArtifactHandlersTest {

	@ParameterizedTest
	@CsvSource({
			"pom", "pom.sha1", "jar", "jar.sha1", "zip"
	})
	void getsArtifactHandler(String type) {
		DefaultArtifactHandlers manager = new DefaultArtifactHandlers();
		ArtifactHandler handler = manager.getArtifactHandler(type);
		assertNotNull(handler, "expect handler for type: " + type);
		assertEquals(type, handler.getExtension(), "expect extension is same as input type: " + type);
		ArtifactHandler reget = manager.getArtifactHandler(type);
		assertSame(handler, reget, "expect same handler for type: " + type);
	}

	@Test
	void addHandlersIsUnsupported() {
		DefaultArtifactHandlers manager = new DefaultArtifactHandlers();
		assertThrows(UnsupportedOperationException.class, () -> manager.addHandlers(Collections.emptyMap()),
				"addHandlers is unsupported");
	}
}