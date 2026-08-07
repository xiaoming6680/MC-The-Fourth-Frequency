package com.xm.thefourthfrequency.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards a Mixin rule that every check this project can otherwise run will pass.
 *
 * <p>{@code @Shadow} resolves <b>fields</b> against the target class alone. Inheritance is not
 * searched, because a field access is not virtual - so shadowing a field the target merely inherits
 * throws {@code "@Shadow field X was not located in the target class"} at class-load. Shadow
 * <em>methods</em> do not have this problem, which is what makes the rule easy to get wrong.
 *
 * <p>Nothing else catches it. It compiles, because the field is visible through the superclass. It
 * remaps correctly, because the field genuinely exists and the mapping is found. Reading the
 * remapped jar shows a correct intermediary name. It only fails when the target class is actually
 * loaded - and for a screen or an entity that can be a long way into a session.
 *
 * <p>So this test reads the sources and asks the real class whether it <em>declares</em> each
 * shadowed field, which is the same question Mixin asks.
 */
class ShadowFieldOwnershipTest {
	private static final Pattern MIXIN_TARGET = Pattern.compile(
			"@Mixin\\s*\\(\\s*(?:value\\s*=\\s*)?\\{?\\s*([A-Za-z_][A-Za-z0-9_.]*)\\.class");

	/** A {@code @Shadow} field: annotations, modifiers, a type, a name, then a semicolon. */
	private static final Pattern SHADOW_FIELD = Pattern.compile(
			"@Shadow\\b[^;{}()]*?\\b(?:private|protected|public)\\s+[^;{}()=]*?\\b([A-Za-z_$][A-Za-z0-9_$]*)\\s*;");

	private static final List<Path> MIXIN_ROOTS = List.of(
			Path.of("src/main/java/com/xm/thefourthfrequency/mixin"),
			Path.of("src/client/java/com/xm/thefourthfrequency/mixin"));

	@Test
	void everyShadowedFieldIsDeclaredByItsOwnTargetClass() throws IOException {
		List<String> offenders = new ArrayList<>();
		List<String> unresolved = new ArrayList<>();
		int checked = 0;

		for (Map.Entry<Path, String> source : mixinSources().entrySet()) {
			String body = source.getValue();
			Matcher target = MIXIN_TARGET.matcher(body);
			if (!target.find()) continue;
			Class<?> targetClass = resolve(body, target.group(1));
			if (targetClass == null) {
				unresolved.add(source.getKey().getFileName() + " -> " + target.group(1));
				continue;
			}
			Matcher field = SHADOW_FIELD.matcher(body);
			while (field.find()) {
				String name = field.group(1);
				checked++;
				try {
					targetClass.getDeclaredField(name);
				} catch (NoSuchFieldException missing) {
					// Inherited rather than declared, or gone. Both are the same failure to Mixin.
					offenders.add(source.getKey().getFileName() + ": " + name
							+ " is not declared by " + targetClass.getName());
				}
			}
		}

		// A regex that stopped matching would otherwise make this a no-op, and an unresolvable target
		// would silently skip a whole file.
		assertTrue(checked >= 12, "only " + checked + " shadowed fields found; the pattern looks wrong");
		assertTrue(unresolved.isEmpty(), "could not resolve mixin targets: " + unresolved);
		assertTrue(offenders.isEmpty(),
				"@Shadow resolves fields against the target class only - inheritance is not searched, "
						+ "so these fail at class-load with \"was not located in the target class\": "
						+ offenders);
	}

	private static Map<Path, String> mixinSources() throws IOException {
		Map<Path, String> sources = new LinkedHashMap<>();
		for (Path root : MIXIN_ROOTS) {
			assertTrue(Files.isDirectory(root), "mixin root moved: " + root);
			try (Stream<Path> paths = Files.list(root)) {
				for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
					sources.put(path, Files.readString(path, StandardCharsets.UTF_8));
				}
			}
		}
		return sources;
	}

	/** Resolves a simple class name against the file's own imports. */
	private static Class<?> resolve(String body, String simpleName) {
		Matcher imports = Pattern.compile("(?m)^import\\s+([A-Za-z0-9_.]+)\\s*;").matcher(body);
		while (imports.find()) {
			String imported = imports.group(1);
			if (imported.endsWith("." + simpleName)) return load(imported);
		}
		// An inner class named as Outer.Inner, whose outer half is what was imported.
		int dot = simpleName.indexOf('.');
		if (dot > 0) {
			Class<?> outer = resolve(body, simpleName.substring(0, dot));
			if (outer != null) return load(outer.getName() + "$" + simpleName.substring(dot + 1));
		}
		return null;
	}

	private static Class<?> load(String binaryName) {
		try {
			return Class.forName(binaryName, false, ShadowFieldOwnershipTest.class.getClassLoader());
		} catch (ClassNotFoundException | LinkageError absent) {
			return null;
		}
	}
}
