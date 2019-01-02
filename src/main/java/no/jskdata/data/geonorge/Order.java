package no.jskdata.data.geonorge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @see https://nedlasting.geonorge.no/Help/ResourceModel?modelName=Geonorge.
 *      NedlastingApi.V1.OrderType
 */
public class Order {

    public String email = "";
    private final List<OrderLine> orderLines = new ArrayList<>();

    public void addOrderLine(OrderLine orderLine) {
        orderLines.add(orderLine);
    }

    public OrderLine getOrCreateOrderLine(String metadataUuid, Projection projection, Collection<Format> formats) {
        for (OrderLine orderLine : orderLines) {
            if (!metadataUuid.equals(orderLine.metadataUuid)) {
                continue;
            }
            if (!orderLine.hasProjection(projection)) {
                continue;
            }
            if (!orderLine.hasFormats(formats)) {
                continue;
            }
            return orderLine;
        }
        
        OrderLine orderLine = new OrderLine();
        orderLine.metadataUuid = metadataUuid;
        orderLine.setFormats(formats);
        orderLine.setProjection(projection);
        addOrderLine(orderLine);
        
        return orderLine;
    }

    public boolean isEmpty() {
        return orderLines.isEmpty();
    }

}
