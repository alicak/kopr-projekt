package sk.upjs.ics.kopr.projekt1.klient;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingWorker;

public class ProgresBarWorker extends SwingWorker<Void, Double> {

    private final OknoKlienta oknoKlienta;
    private final Klient klient;

    public ProgresBarWorker(OknoKlienta oknoKlienta, Klient klient) {
        this.oknoKlienta = oknoKlienta;
        this.klient = klient;
    }

    @Override
    protected Void doInBackground() throws Exception {
        double velkostSuboru = klient.getVelkostSuboru();
        AtomicLong mameBajtov = klient.getPocetPrijatychBajtov();
        while (true) {
            if (isCancelled()) {
                return null;
            }
            if (velkostSuboru == 0) { // worker uz bezi, ale este sme od servera nedostali velkost suboru
                velkostSuboru = klient.getVelkostSuboru();
                continue;
            }

            double percent = (mameBajtov.get() / velkostSuboru) * 100;
            if (percent > 100) {
                percent = 100;
            }
            
            publish(percent);
        }
    }

    @Override
    protected void process(List<Double> percenta) {
        // vezmeme poslednu hodnotu
        double percento = percenta.get(percenta.size() - 1);
        int hodnota = (int) percento;
        oknoKlienta.getProgresBar().setValue(hodnota);
        oknoKlienta.getLblPercento().setText(hodnota + " %");
    }

}
