package sk.upjs.ics.kopr.projekt1.klient;

public class PredcasneUkonceniePrijimaniaException extends RuntimeException {

    private long offset;

    public PredcasneUkonceniePrijimaniaException(long offset) {
        this.offset = offset;
    }

    public long getOffset() {
        return offset;
    }

}
