package no.jskdata.data.geonorge;

public class Projection implements Comparable<Projection> {

    public String code;
    public String name;
    public String codespace;

    @Override
    public int compareTo(Projection o) {
        // prefer UTM as NRL are currently not available in all the projections
        // it announces
        if (o.code.equals(code)) {
            return 0;
        }
        if (name.contains("UTM") && o.name.equals("UTM")) {
            return 0;
        }
        if (name.contains("UTM")) {
            return -1;
        }
        if (o.name.contains("UTM")) {
            return 1;
        }
        return 0;
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof Projection)) {
            return false;
        }
        Projection o = (Projection) obj;
        return code.equals(o.code);
    }

}
