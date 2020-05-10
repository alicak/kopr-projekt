package sk.upjs.ics.kopr.projekt1.klient;

import javax.swing.SwingWorker;

public class KopirovaciWorker extends SwingWorker<Void, Void> {
    
    private final Klient klient;
    private final OknoKlienta oknoKlienta;
    
    public KopirovaciWorker(OknoKlienta oknoKlienta, Klient klient) {
        this.oknoKlienta = oknoKlienta;
        this.klient = klient;
    }
    
    @Override
    protected Void doInBackground() throws Exception {
        klient.stahuj();
        
        oknoKlienta.zastavProgresBarWorker();
        
        if (klient.isJePrerusene()) {
            klient.ulozStav();
        }
        
        if (klient.isJeUkoncene()) {
            klient.vymazStahovanySubor();
        }
        
        oknoKlienta.getBtnObnovit().setEnabled(false);
        oknoKlienta.getBtnZacat().setEnabled(true);
        oknoKlienta.getBtnPrerusit().setEnabled(false);
        oknoKlienta.getBtnUkoncit().setEnabled(false);
        
        return null;
    }
    
}
