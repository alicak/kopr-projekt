package sk.upjs.ics.kopr.projekt1.klient;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class PrijimaciaUloha implements Callable<Boolean> {

    private final Socket soket;
    private final long zaciatocnaPoziciaVSubore;
    private final File zapisovanySubor;
    private AtomicLong pocetPrijatychBajtov;
    private RandomAccessFile subor;

    public PrijimaciaUloha(Socket soket, long zaciatocnaPoziciaVSubore, File subor, AtomicLong pocetPrijatychBajtov) {
        this.soket = soket;
        this.zaciatocnaPoziciaVSubore = zaciatocnaPoziciaVSubore;
        this.zapisovanySubor = subor;
        this.pocetPrijatychBajtov = pocetPrijatychBajtov;
    }

    @Override
    public Boolean call() throws Exception {
        byte[] bajty = new byte[1460];
        // pole pre pripad, ze sa zo soketu precita menej ako 1460 bajtov
        byte[] mensieBajty;

        int pocetPrecitanych;
        long aktualnaPoziciaVSubore = zaciatocnaPoziciaVSubore;

        try {
            subor = new RandomAccessFile(zapisovanySubor, "rw");
            InputStream input = soket.getInputStream();

            while (true) {
                // blokuje, kym nieco neprecita
                pocetPrecitanych = input.read(bajty);

                // vrati -1, ak je koniec streamu
                if (pocetPrecitanych == -1) {
                    break;
                }

                subor.seek(aktualnaPoziciaVSubore);

                if (pocetPrecitanych < 1460) {
                    mensieBajty = Arrays.copyOf(bajty, pocetPrecitanych);
                    subor.write(mensieBajty);
                } else {
                    subor.write(bajty);
                }

                aktualnaPoziciaVSubore += pocetPrecitanych;
                pocetPrijatychBajtov.addAndGet(pocetPrecitanych);
            }

            subor.close();
            soket.close();
        } catch (FileNotFoundException ex) {
            soket.close();
            throw ex;
        } catch (IOException ex) {
            subor.close();
            if (!soket.isClosed()) { // pre istotu...
                soket.close();
            }
            throw new PredcasneUkonceniePrijimaniaException(aktualnaPoziciaVSubore);
        }

        return true;
    }

}
