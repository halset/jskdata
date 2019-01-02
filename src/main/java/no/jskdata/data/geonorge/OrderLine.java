package no.jskdata.data.geonorge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @see https://nedlasting.geonorge.no/Help/ResourceModel?modelName=Geonorge.
 *      NedlastingApi.V1.OrderLineType
 */
public class OrderLine {

    private final List<OrderArea> areas = new ArrayList<>();
    private final List<Format> formats = new ArrayList<>();
    public String metadataUuid;
    public String coordinates;
    private final List<Projection> projections = new ArrayList<>();

    public void addArea(OrderArea area) {
        areas.add(area);
    }

    public void setProjection(Projection projection) {
        projections.clear();
        projections.add(projection);
    }

    public boolean hasProjection(Projection projection) {
        return projections.contains(projection);
    }

    public void setFormats(Collection<Format> formats) {
        this.formats.clear();
        this.formats.addAll(formats);
    }

    public boolean hasFormats(Collection<Format> formats) {
        return this.formats.containsAll(formats);
    }

}
