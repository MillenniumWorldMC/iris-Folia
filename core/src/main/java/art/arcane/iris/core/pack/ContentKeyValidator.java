/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.pack;

import art.arcane.iris.spi.PlatformRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ContentKeyValidator {
    private static final int MAX_SUGGESTION_SCANS = 4096;
    private static final String DEFAULT_NAMESPACE = "minecraft";

    private ContentKeyValidator() {
    }

    public enum ContentRegistry {
        BLOCK,
        ITEM,
        ENTITY;

        public String label() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record ContentKeyError(String key, ContentRegistry registry, boolean namespaceLoaded, String suggestion) {
        public String message() {
            StringBuilder sb = new StringBuilder(64);
            sb.append("Unknown ").append(registry.label()).append(" key '").append(key).append('\'');
            sb.append(" (missing from the ").append(registry.label()).append(" registry");
            if (!namespaceLoaded) {
                sb.append("; namespace '").append(namespaceOf(key)).append("' is not loaded on this server");
            }
            sb.append(')');
            if (suggestion != null) {
                sb.append(" - did you mean '").append(suggestion).append("'?");
            }
            return sb.toString();
        }
    }

    public static List<ContentKeyError> validate(PlatformRegistries registries,
                                                 Collection<String> referencedBlocks,
                                                 Collection<String> referencedItems,
                                                 Collection<String> referencedEntities) {
        if (registries == null) {
            return List.of();
        }

        List<String> blockKeys = normalizeKeyList(registries.blockKeys());
        List<String> itemKeys = normalizeKeyList(registries.itemKeys());
        List<String> entityKeys = normalizeKeyList(registries.entityKeys());

        Set<String> knownBlocks = new HashSet<>(blockKeys);
        Set<String> knownItems = new HashSet<>(itemKeys);
        Set<String> knownEntities = new HashSet<>(entityKeys);

        Set<String> loadedNamespaces = new HashSet<>();
        addNamespaces(blockKeys, loadedNamespaces);
        addNamespaces(itemKeys, loadedNamespaces);
        addNamespaces(entityKeys, loadedNamespaces);

        Map<String, ContentKeyError> errors = new LinkedHashMap<>();
        validateCategory(referencedBlocks, ContentRegistry.BLOCK, knownBlocks, blockKeys, loadedNamespaces, errors);
        validateCategory(referencedItems, ContentRegistry.ITEM, knownItems, itemKeys, loadedNamespaces, errors);
        validateCategory(referencedEntities, ContentRegistry.ENTITY, knownEntities, entityKeys, loadedNamespaces, errors);
        return List.copyOf(errors.values());
    }

    static String namespaceOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? DEFAULT_NAMESPACE : key.substring(0, colon);
    }

    private static void validateCategory(Collection<String> referenced,
                                         ContentRegistry registry,
                                         Set<String> known,
                                         List<String> candidatePool,
                                         Set<String> loadedNamespaces,
                                         Map<String, ContentKeyError> errors) {
        if (referenced == null || referenced.isEmpty()) {
            return;
        }
        for (String raw : referenced) {
            String normalized = normalizeKey(raw);
            if (normalized == null || known.contains(normalized)) {
                continue;
            }
            String dedupKey = registry.name() + '|' + normalized;
            if (errors.containsKey(dedupKey)) {
                continue;
            }
            boolean namespaceLoaded = loadedNamespaces.contains(namespaceOf(normalized));
            String suggestion = nearestKey(normalized, candidatePool);
            errors.put(dedupKey, new ContentKeyError(normalized, registry, namespaceLoaded, suggestion));
        }
    }

    private static List<String> normalizeKeyList(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            if (key == null || key.isEmpty()) {
                continue;
            }
            String value = key.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                out.add(value.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ':' + value : value);
            }
        }
        return out;
    }

    private static void addNamespaces(List<String> keys, Set<String> namespaces) {
        for (String key : keys) {
            namespaces.add(namespaceOf(key));
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int bracket = value.indexOf('[');
        if (bracket >= 0) {
            value = value.substring(0, bracket).trim();
        }
        if (value.isEmpty()) {
            return null;
        }
        return value.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ':' + value : value;
    }

    private static String nearestKey(String key, List<String> candidatePool) {
        String path = pathOf(key);
        int maxDistance = Math.max(2, path.length() / 3);
        int bestDistance = Integer.MAX_VALUE;
        String best = null;
        int scans = 0;

        for (String candidate : candidatePool) {
            String candidatePath = pathOf(candidate);
            if (Math.abs(candidatePath.length() - path.length()) > maxDistance) {
                continue;
            }
            if (scans++ >= MAX_SUGGESTION_SCANS) {
                break;
            }
            int distance = boundedLevenshtein(path, candidatePath, maxDistance);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
                if (distance <= 1) {
                    break;
                }
            }
        }

        return bestDistance <= maxDistance ? best : null;
    }

    private static String pathOf(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? key : key.substring(colon + 1);
    }

    private static int boundedLevenshtein(String a, String b, int max) {
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > max) {
            return max + 1;
        }
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lb; j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                if (curr[j] < rowMin) {
                    rowMin = curr[j];
                }
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[lb];
    }
}
