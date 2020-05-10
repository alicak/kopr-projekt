package sk.upjs.ics.kopr.projekt1.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.concurrent.Callable;

public class OdosielaciaUloha implements Callable<Boolean> {

    private final long zaciatocnaPoziciaVSubore;
    private final long konecnaPoziciaVSubore;
    private final File subor;
    private RandomAccessFile rafsubor;
    private final ServerSocket serverSoket;

    public OdosielaciaUloha(ServerSocket soket, long zaciatocnaPoziciaVSubore, long konecnaPoziciaVSubore, File subor) {
        this.serverSoket = soket;
        this.zaciatocnaPoziciaVSubore = zaciatocnaPoziciaVSubore;
        this.konecnaPoziciaVSubore = konecnaPoziciaVSubore;
        this.subor = subor;
    }

    @Override
    public Boolean call() throws Exception {
        try {
            this.rafsubor = new RandomAccessFile(subor, "r");

            // blokovana operacia - caka, kym sa niekto pripoji a nadviaze s nim TCP spojenie
            Socket soket = serverSoket.accept();
            OutputStream output = soket.getOutputStream();

            byte[] bajty = new byte[100000];
            byte[] mensieBajty; // ak budeme mat na spracovanie menej ako 100000 bajtov

            int pocetPrecitanych;
            long naSpracovanie = konecnaPoziciaVSubore - zaciatocnaPoziciaVSubore + 1;

            while (naSpracovanie > 0) {
                rafsubor.seek(konecnaPoziciaVSubore - naSpracovanie + 1);
                pocetPrecitanych = rafsubor.read(bajty);

                if (pocetPrecitanych < 100000) {
                    mensieBajty = Arrays.copyOf(bajty, pocetPrecitanych);
                    output.write(mensieBajty);
                } else {
                    output.write(bajty);
                }

                naSpracovanie -= pocetPrecitanych;
            }

            output.flush();
            soket.close();
            serverSoket.close();
            rafsubor.close();
        } catch (FileNotFoundException ex) {
            throw ex;
        } catch (IOException ex) {
            if (!serverSoket.isClosed()) {
                serverSoket.close();
            }
            throw ex;
        }
        
        return true;
    }

}
