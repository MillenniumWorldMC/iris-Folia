package art.arcane.iris.engine.platform;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class BukkitChunkGeneratorOverlayPolicyTest {
    @Test
    public void skipsAirOverlayBlocks() {
        BlockData air = mock(BlockData.class);
        doReturn(Material.AIR).when(air).getMaterial();

        assertFalse(BukkitChunkGenerator.shouldApplyMantleOverlayBlock(air));
    }

    @Test
    public void skipsCaveAirOverlayBlocks() {
        BlockData air = mock(BlockData.class);
        doReturn(Material.CAVE_AIR).when(air).getMaterial();

        assertFalse(BukkitChunkGenerator.shouldApplyMantleOverlayBlock(air));
    }

    @Test
    public void skipsVoidAirOverlayBlocks() {
        BlockData air = mock(BlockData.class);
        doReturn(Material.VOID_AIR).when(air).getMaterial();

        assertFalse(BukkitChunkGenerator.shouldApplyMantleOverlayBlock(air));
    }

    @Test
    public void appliesSolidOverlayBlocks() {
        BlockData stone = mock(BlockData.class);
        doReturn(Material.STONE).when(stone).getMaterial();

        assertTrue(BukkitChunkGenerator.shouldApplyMantleOverlayBlock(stone));
    }

    @Test
    public void skipsNullOverlayBlocks() {
        assertFalse(BukkitChunkGenerator.shouldApplyMantleOverlayBlock(null));
    }
}
