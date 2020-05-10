package sk.upjs.ics.kopr.projekt1.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Server {

    private static String adresar = "C:\\Users\\Ala\\Desktop\\KOPR\\1.projekt\\subory\\";

    private static final int RIADIACI_PORT = 50000;
    private static ServerSocket serverSoket;
    private static Socket riadiaciSoket;
    private static DataInputStream dis;
    private static DataOutputStream dos;

    private static int mod;
    private static int pocetSoketov;
    private static String nazovSuboru;
    private static long velkostDielu;
    private static long zvysok;
    private static long[] offsety;
    private static File posielanySubor;
    private static long velkost;

    private static ServerSocket[] odosielacieSokety;

    private static final int MOD_NOVY = 0;
    private static final int MOD_POKRACOVANIE = 1;

    private static ExecutorService exekutor;
    private static List<Future<Boolean>> futures;

    public static void main(String[] args) {
        if (otvorRiadiaciSoket()) {
            while (true) { // subory mozeme posielat az do konca sveta
                try {
                    akceptujSpojenie();

                    mod = dis.readInt();

                    if (mod == MOD_NOVY || mod == MOD_POKRACOVANIE) {
                        System.out.println("Zaciatok odosielania suboru.");
                        zacniKopirovanie(mod);
                        System.out.println("Koniec odosielania suboru.");
                    } else {
                        System.err.println("Chyba poziadavky klienta.");
                        break;
                    }

                } catch (IOException ex) {
                    ex.printStackTrace();
                    // nastava, ak sa nepodaria uvodne dohody alebo sa nepodari otvorit vsetky potrebne sokety
                    System.err.println("Nepodarilo sa spustit odosielanie.");              
                }
            }
        }
    }

    /**
     *
     * @return true, ak sa podari otvorit soket pre riadiace spojenie
     */
    private static boolean otvorRiadiaciSoket() {
        try {
            serverSoket = new ServerSocket(RIADIACI_PORT);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Nepodarilo sa otvorit riadiaci soket.");
        }
        return serverSoket != null;
    }

    private static void akceptujSpojenie() throws IOException {
        riadiaciSoket = serverSoket.accept();
        dis = new DataInputStream(new BufferedInputStream(riadiaciSoket.getInputStream()));
        dos = new DataOutputStream(new BufferedOutputStream(riadiaciSoket.getOutputStream()));
    }

    private static void zacniKopirovanie(int mod) throws IOException {
        precitajPoziadavky(mod);
        vyrobASpustiUlohy();
        // posleme klientovi velkost suboru az po spusteni vsetkych 
        // odosielacich vlakien, a on zacne stahovat az po jej precitani
        posliVelkostSuboru();
        prijmiVysledky();
    }

    private static void precitajPoziadavky(int mod) throws IOException {
        pocetSoketov = dis.readInt();
        nazovSuboru = dis.readUTF();

        posielanySubor = new File(adresar + nazovSuboru);
        velkost = posielanySubor.length();

        // offsety, od ktorych zacnu sokety posielat data
        offsety = new long[pocetSoketov];

        if (mod == MOD_NOVY) {
            // velkost dielu pre 1 soket
            velkostDielu = velkost / pocetSoketov;
            zvysok = velkost % pocetSoketov;
            for (int i = 0; i < pocetSoketov; i++) {
                offsety[i] = i * velkostDielu; // pri novom kopirovani urci offsety server
            }
        } else if (mod == MOD_POKRACOVANIE) {
            for (int i = 0; i < pocetSoketov; i++) {
                offsety[i] = dis.readLong(); // pri obnovenom sa precitaju od klienta
            }
        }
    }

    private static void vyrobASpustiUlohy() throws IOException {
        // exekutor pre odosielacie vlakna
        exekutor = Executors.newFixedThreadPool(pocetSoketov);

        // ulohy a sokety pre odosielacie vlakna
        OdosielaciaUloha odosielaciaUloha = null;
        odosielacieSokety = new ServerSocket[pocetSoketov];

        // ak uloha vyhodi vynimku, tak z nej zistime, co sa stalo
        futures = new ArrayList<>();
        int port;

        for (int i = 0; i < pocetSoketov; i++) {
            // porty pridelujeme za sebou
            port = RIADIACI_PORT + 1 + i;

            long konecnaPozicia = (i + 1) * velkostDielu - 1;

            // posledny soket dostane vacsiu cast
            if (i == pocetSoketov - 1) {
                konecnaPozicia += zvysok;
            }

            odosielacieSokety[i] = new ServerSocket(port);
            odosielaciaUloha = new OdosielaciaUloha(odosielacieSokety[i], offsety[i],
                    konecnaPozicia, posielanySubor);
            Future<Boolean> future = exekutor.submit(odosielaciaUloha);
            futures.add(future);
        }
    }

    private static void posliVelkostSuboru() throws IOException {
        dos.writeLong(velkost);
        dos.flush();
    }

    private static void prijmiVysledky() throws IOException {
        for (Future<Boolean> f : futures) {
            try {
                f.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof FileNotFoundException) {
                    System.err.println("Nepodarilo sa otvorit subor na citanie.");
                } else if (cause instanceof IOException) {
                    System.out.println("Soket bol uzavrety pred koncom odosielania.");
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                System.err.println("Tato vynimka nastava prerusenim, ale nepoznam nikoho, kto by ho mohol sposobit.");                
            }
        }
        // po dobehnuti uloh exekutor vypneme
        exekutor.shutdown();
    }

}
