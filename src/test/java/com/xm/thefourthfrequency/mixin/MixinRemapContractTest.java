package com.xm.thefourthfrequency.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the one Mixin mistake no run of this project can catch.
 *
 * <p>Loom rewrites Mixin annotations in place at remap time rather than emitting a refMap. To turn a
 * member name into its intermediary form it needs a class to resolve that name against, and an
 * injection-point descriptor written without an owner - {@code "move(...)V"} rather than
 * {@code "Lnet/minecraft/.../EnderDragon;move(...)V"} - gives it none, so the string is copied
 * through verbatim. Every environment this repository can run tests in is the named one, where
 * verbatim is already the correct name: dev client, dev server, GameTest and client GameTest all
 * pass. Only the remapped jar, in a real launcher, looks for a Mojang name among intermediary ones,
 * finds nothing, and - under {@code defaultRequire: 1} - dies during bootstrap.
 *
 * <p>So this test reads the sources instead of running them.
 */
class MixinRemapContractTest {
	/** {@code target = } followed by one or more adjacent string literals, possibly {@code +}-joined. */
	private static final Pattern TARGET = Pattern.compile(
			"target\\s*=\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");

	private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

	private static final List<Path> MIXIN_ROOTS = List.of(
			Path.of("src/main/java/com/xm/thefourthfrequency/mixin"),
			Path.of("src/client/java/com/xm/thefourthfrequency/mixin"));

	@Test
	void everyInjectionPointDescriptorNamesItsOwner() throws IOException {
		List<String> ownerless = new ArrayList<>();
		int scanned = 0;
		int descriptors = 0;

		for (Path root : MIXIN_ROOTS) {
			assertTrue(Files.isDirectory(root), "Mixin root moved: " + root);
			try (Stream<Path> sources = Files.list(root)) {
				for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
					scanned++;
					Matcher targets = TARGET.matcher(Files.readString(source, StandardCharsets.UTF_8));
					while (targets.find()) {
						descriptors++;
						StringBuilder descriptor = new StringBuilder();
						Matcher literals = LITERAL.matcher(targets.group(1));
						while (literals.find()) descriptor.append(literals.group(1));
						// "Lowner;name(args)ret" for a method, "Lowner;name:Ldesc;" for a field.
						if (descriptor.isEmpty() || descriptor.charAt(0) != 'L'
								|| descriptor.indexOf(";") < 0)
							ownerless.add(source.getFileName() + ": " + descriptor);
					}
				}
			}
		}

		// A regex that silently stops matching would otherwise turn this whole guard into a no-op.
		assertTrue(scanned >= 40, "Only " + scanned + " mixin sources found; the roots look wrong");
		assertTrue(descriptors >= 20,
				"Only " + descriptors + " injection-point descriptors matched; the pattern looks wrong");
		assertTrue(ownerless.isEmpty(),
				"Injection-point descriptors without an owner survive remapping unchanged and fail to "
						+ "bind in a remapped jar: " + ownerless);
	}
}
