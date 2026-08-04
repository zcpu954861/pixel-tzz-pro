package io.github.zcpu954861.pixeltzzpro.content;

import java.util.Set;
import net.minecraft.resources.Identifier;

/** Package-bound access to the registry's exact predicate-retention selection. */
public final class DefinitionRegistryTestAccess {
	private DefinitionRegistryTestAccess() {
	}

	public static Set<Identifier> referencedPredicates(final DefinitionSnapshot snapshot) {
		return DefinitionRegistry.referencedPredicates(snapshot);
	}
}
