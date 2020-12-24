package no.jskdata.data.geonorge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public class Area {

    public String code;
    public String type;
    public String name;

    public List<Projection> projections;
    public List<Format> formats;

    public boolean isCountryWide() {
        return "0000".equals(code);
    }

    public boolean isCounty() {
        return code != null && code.length() == 2;
    }

    public boolean isMunicipal() {
        return code != null && code.length() == 4;
    }

    public OrderArea asOrderArea() {
        OrderArea oa = new OrderArea();
        oa.code = code;
        oa.type = type;
        oa.name = name;
        return oa;
    }
    
    public boolean hasFormat(Predicate<String> formatNameFilter) {
        for (Format format : formats) {
            if (formatNameFilter.test(format.name)) {
                return true;
            }
        }
        return false;
    }
    
    public List<Format> formats(Predicate<String> formatNameFilter) {
        List<Format> r = new ArrayList<>();
        for (Format format : formats) {
            if (formatNameFilter.test(format.name)) {
                r.add(format);
            }
        }
        return Collections.unmodifiableList(r);
    }
    
    public List<Projection> getProjections(Predicate<String> projectionNameFilter) {
        List<Projection> r = new ArrayList<>();
        for (Projection projection : projections) {
            if (projectionNameFilter.test(projection.code) || projectionNameFilter.test(projection.name)) {
                r.add(projection);
            }
        }
        return Collections.unmodifiableList(r);
    }

    @Override
    public String toString() {
        return super.toString() + "{code:" + code + ", type: " + type + ", name: " + name + "}";
    }

}
