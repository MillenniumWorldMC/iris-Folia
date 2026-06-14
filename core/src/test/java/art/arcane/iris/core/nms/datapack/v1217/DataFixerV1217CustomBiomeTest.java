package art.arcane.iris.core.nms.datapack.v1217;

import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DataFixerV1217CustomBiomeTest {
    private final DataFixerV1217 fixer = new DataFixerV1217();

    @Test
    public void keepsSpigotBiomeColorsInEffects() {
        IrisBiomeCustom biome = new IrisBiomeCustom();
        biome.setId("spigot_colors");
        biome.setGrassColor("#28a040");
        biome.setFoliageColor("#249030");

        JSONObject json = new JSONObject(biome.generateJson(fixer));
        JSONObject effects = json.getJSONObject("effects");

        assertFalse(json.has("attributes"));
        assertTrue(effects.has("water_color"));
        assertTrue(effects.has("water_fog_color"));
        assertEquals(0x28a040, effects.getInt("grass_color"));
        assertEquals(0x249030, effects.getInt("foliage_color"));
    }
}
