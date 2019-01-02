package no.jskdata.data.geonorge;

public class Format {

    public String name;
    public String version;

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Format)) {
            return false;
        }
        Format o = (Format) obj;
        return name.equals(o.name);
    }

}
