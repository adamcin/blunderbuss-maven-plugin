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
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

public class JarUtils {

	static Completable extractJarFile(@NotNull final File srcJar, @NotNull final Path pathIsRoot) {
		return Completable.create(emitter -> {
			try (JarFile jarFile = new JarFile(srcJar)) {
				for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
					JarEntry entry = entries.nextElement();
					final String relPath = entry.getName()
							.replaceFirst("^/*", "")
							.replaceFirst("/?$", "")
							.replaceAll(Pattern.quote("/"), File.separator);
					if (relPath.isEmpty()) {
						continue;
					}
					final Path entryPath = pathIsRoot.resolve(relPath);
					if (entry.isDirectory()) {
						Files.createDirectories(entryPath);
					} else {
						Files.createDirectories(entryPath.getParent());
						try (InputStream contents = jarFile.getInputStream(entry);
							 OutputStream fos = new FileOutputStream(entryPath.toFile())) {
							IOUtils.copy(contents, fos);
						}
					}
				}
			} catch (Exception e) {
				emitter.onError(e);
			}
			emitter.onComplete();
		});
	}

	static final IOFileFilter includedEntry = new IOFileFilter() {
		@Override
		public boolean accept(File file) {
			return !("META-INF".equals(file.getParentFile().getName()) && "MANIFEST.MF".equals(file.getName()));
		}

		@Override
		public boolean accept(File dir, String name) {
			return !("META-INF".equals(dir.getName()) && "MANIFEST.MF".equals(name));
		}
	};

	static void buildJarOutputStreamFromDir(final @NotNull File srcDir,
			final @NotNull JarOutputStream jos,
			final @NotNull Map<String, File> additionalEntries) throws IOException {
		final String absPath = srcDir.getAbsolutePath();
		for (File file : FileUtils.listFilesAndDirs(srcDir, includedEntry, TrueFileFilter.INSTANCE)) {
			final String filePath = file.getAbsolutePath();
			final String entryName = filePath.substring(absPath.length())
					.replaceFirst("^" + Pattern.quote(File.separator) + "?", "")
					.replace(File.separator, "/");
			if (entryName.isEmpty()) {
				continue;
			}
			if (file.isDirectory()) {
				JarEntry entry = new JarEntry(entryName + "/");
				entry.setTime(file.lastModified());
				jos.putNextEntry(entry);
			} else {
				JarEntry entry = new JarEntry(entryName);
				entry.setTime(file.lastModified());
				jos.putNextEntry(entry);
				try (FileInputStream fileInput = new FileInputStream(file)) {
					IOUtils.copy(fileInput, jos);
				}
			}
			jos.closeEntry();
		}
		for (Map.Entry<String, File> add : additionalEntries.entrySet()) {
			final String entryName = add.getKey();
			if (entryName.isEmpty()) {
				continue;
			}
			if (add.getValue().isDirectory()) {
				JarEntry entry = new JarEntry(entryName.replaceFirst("/?$", "/"));
				entry.setTime(add.getValue().lastModified());
				jos.putNextEntry(entry);
			} else {
				JarEntry entry = new JarEntry(entryName.replaceFirst("/?$", ""));
				entry.setTime(add.getValue().lastModified());
				jos.putNextEntry(entry);
				try (FileInputStream fileInput = new FileInputStream(add.getValue())) {
					IOUtils.copy(fileInput, jos);
				}
			}
			jos.closeEntry();
		}
	}

	static Completable createJarFile(@NotNull final File targetJar, @NotNull final Path pathIsRoot) {
		final Map<String, File> additionalEntries = Collections.emptyMap();
		return Completable.create(emitter -> {
			final File targetDir = targetJar.getParentFile();
			if (!targetDir.isDirectory() && !targetDir.mkdirs()) {
				emitter.onError(new IOException("failed to create parent target directory: " + targetDir.getAbsolutePath()));
			}
			final File manifestFile = pathIsRoot.resolve(JarFile.MANIFEST_NAME.replaceAll(Pattern.quote("/"), File.separator)).toFile();
			if (manifestFile.exists()) {
				try (InputStream manIn = new FileInputStream(manifestFile)) {
					final Manifest man = new Manifest(manIn);
					try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(targetJar), man)) {
						buildJarOutputStreamFromDir(pathIsRoot.toFile(), jos, additionalEntries);
					} catch (Exception e) {
						emitter.onError(e);
					}
				}
			} else {
				try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(targetJar))) {
					buildJarOutputStreamFromDir(pathIsRoot.toFile(), jos, additionalEntries);
				} catch (Exception e) {
					emitter.onError(e);
				}
			}
			emitter.onComplete();
		});
	}
}
