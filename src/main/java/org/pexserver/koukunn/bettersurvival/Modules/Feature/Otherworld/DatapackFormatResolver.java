package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.metadata.pack.PackFormat;

/** Resolves the active Minecraft data-pack format directly from the running NMS version. */
final class DatapackFormatResolver {
    private DatapackFormatResolver() { }

    static ResolvedPackFormat resolve() {
        PackFormat format = SharedConstants.getCurrentVersion().datapackVersion();
        return new ResolvedPackFormat(format.major(), format.minor());
    }

    record ResolvedPackFormat(int major, int minor) {
        ResolvedPackFormat {
            if (major < 0 || minor < 0) throw new IllegalArgumentException("Negative pack format");
        }

        String minFormatJson() {
            return minor == 0 ? Integer.toString(major) : "[" + major + ", " + minor + "]";
        }

        String maxFormatJson() {
            return Integer.toString(major);
        }

        @Override
        public String toString() {
            return minor == 0 ? Integer.toString(major) : major + "." + minor;
        }
    }
}
