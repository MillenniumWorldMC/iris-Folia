package art.arcane.iris.engine.object;

import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class IrisDimensionBiomeTagTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void biomeTagWritesAreSortedAndDeduplicated() throws Exception {
        Path output = temporaryFolder.getRoot().toPath().resolve("allows_surface_slime_spawns.json");

        IrisDimension.writeBiomeTag(output, "overworld:swamp_b");
        IrisDimension.writeBiomeTag(output, "overworld:swamp_a");
        IrisDimension.writeBiomeTag(output, "overworld:swamp_b");

        JSONObject tag = new JSONObject(Files.readString(output, StandardCharsets.UTF_8));
        JSONArray values = tag.getJSONArray("values");
        assertFalse(tag.getBoolean("replace"));
        assertEquals(2, values.length());
        assertEquals("overworld:swamp_a", values.getString(0));
        assertEquals("overworld:swamp_b", values.getString(1));
    }

    @Test
    public void customBiomeTagUsesTheMinecraftBiomeTagPath() throws Exception {
        KList<String> tags = new KList<>();
        tags.add("minecraft:allows_surface_slime_spawns");
        IrisDimension.installBiomeTags(temporaryFolder.getRoot(), "overworld:swamp", tags);

        Path output = temporaryFolder.getRoot().toPath()
                .resolve("data/minecraft/tags/worldgen/biome/allows_surface_slime_spawns.json");
        JSONObject tag = new JSONObject(Files.readString(output, StandardCharsets.UTF_8));

        assertEquals("overworld:swamp", tag.getJSONArray("values").getString(0));
    }
}
