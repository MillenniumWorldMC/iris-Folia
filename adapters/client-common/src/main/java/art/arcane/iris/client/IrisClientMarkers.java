package art.arcane.iris.client;

import art.arcane.iris.spi.protocol.IrisMessage;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class IrisClientMarkers {
    private final Map<IrisTileKey, List<IrisMessage.VisionMarkers.Marker>> byTile;

    public IrisClientMarkers() {
        this.byTile = new ConcurrentHashMap<>();
    }

    public void onMarkers(IrisMessage.VisionMarkers markers) {
        byTile.put(new IrisTileKey(markers.tileX(), markers.tileZ(), markers.zoomLevel()), List.copyOf(markers.markers()));
    }

    public List<IrisMessage.VisionMarkers.Marker> forTile(IrisTileKey key) {
        return byTile.get(key);
    }

    public void clear() {
        byTile.clear();
    }
}
