package sk.upjs.ics.kopr.projekt1.klient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public class Klient {
    
    private static final String ADRESA_SERVERA = "localhost";
    // port, na ktorom dohodneme stahovanie
    private static final int PORT_SERVERA = 50000;
    // ukladame do priecinka s projektom
    private static final String CIELOVY_PRIECINOK = System.getProperty("user.dir");

    // pocet bajtov suboru, ktore sme uz od servera prijali
    private AtomicLong pocetPrijatychBajtov = new AtomicLong(0);
    
    private final boolean jeNove;
    private final String nazovSuboru;
    private final int pocetSoketov;
    private List<Long> offsety;
    
    private long velkostSuboru;
    private long velkostDielu;
    private File zapisovanySubor;
    
    private List<Socket> sokety;
    
    private Socket riadiaciSoket;
    private DataInputStream dis;
    private DataOutputStream dos;
    
    private ExecutorService exekutor;
    private List<Callable<Boolean>> ulohy;
    private List<Future<Boolean>> futures;
    private List<Long> offsetyPoPreruseni;
    
    private boolean jePrerusene;
    private boolean jeUkoncene;

    /**
     * Konstruktor pre nove kopirovanie
     *
     * @param nazovSuboru
     * @param pocetSoketov
     */
    public Klient(String nazovSuboru, int pocetSoketov) {
        this.jeNove = true;
        this.nazovSuboru = nazovSuboru;
        this.pocetSoketov = pocetSoketov;
    }

    /**
     * Konstruktor pre obnovene kopirovanie
     *
     * @param info
     */
    public Klient(InfoNaPokracovanie info) {
        this.jeNove = false;
        this.nazovSuboru = info.getNazov();
        this.pocetSoketov = info.getPocetSoketov();
        this.pocetPrijatychBajtov.set(info.getPocetPrijatychBajtov());
        this.offsety = info.getOffsety();
    }

    /**
     * Ak sa podari vsetko pootvarat, tak stahuje, a po dokonceni uloh sa
     * exekutor vypne
     */
    public void stahuj() {
        if (urobUvodneDohovory() && nachystajSubor() && nachystajSokety()) {
            vyrobASpustiUlohy();
            exekutor.shutdown();
        }
    }

    /**
     *
     * @return true, ak sa podari urobit uvodne dohovory
     */
    private boolean urobUvodneDohovory() {
        try {
            pripojSaNaServer();
            posliPoziadavky(jeNove);
            precitajVelkost();
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Uvodne dohovory sa nepodarili.");
            return false;
        }
    }
    
    private void pripojSaNaServer() throws IOException {
        riadiaciSoket = new Socket(ADRESA_SERVERA, PORT_SERVERA);
        dis = new DataInputStream(new BufferedInputStream(riadiaciSoket.getInputStream()));
        dos = new DataOutputStream(new BufferedOutputStream(riadiaciSoket.getOutputStream()));
    }
    
    private void posliPoziadavky(boolean jeNove) throws IOException {
        int mod = (jeNove) ? 0 : 1;
        dos.writeInt(mod);
        dos.writeInt(pocetSoketov);
        dos.writeUTF(nazovSuboru);
        if (!jeNove) { // pri obnovenom stahovani posleme aj offsety
            for (int i = 0; i < pocetSoketov; i++) {
                dos.writeLong(offsety.get(i));
            }
        }
        dos.flush();
    }
    
    private void precitajVelkost() throws IOException {
        velkostSuboru = dis.readLong();
    }

    /**
     *
     * @return true, ak sa podari vytvorit subor na disku a nastavit mu velkost
     */
    private boolean nachystajSubor() {
        try {
            zapisovanySubor = new File(CIELOVY_PRIECINOK + "\\" + nazovSuboru);
            RandomAccessFile subor = new RandomAccessFile(zapisovanySubor, "rw");
            subor.setLength(velkostSuboru);
            subor.close();
            velkostDielu = velkostSuboru / pocetSoketov;
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Nepodarilo sa nachystat subor.");
            return false;
        }
    }

    /**
     *
     * @return true, ak sa podari spojenie na vsetkych soketoch
     */
    private boolean nachystajSokety() {
        sokety = new ArrayList<>();
        Socket soket;
        try {
            for (int i = 0; i < pocetSoketov; i++) {
                soket = new Socket(ADRESA_SERVERA, PORT_SERVERA + 1 + i);
                sokety.add(soket);
            }
            return true;
        } catch (IOException ex) {
            ex.printStackTrace();
            System.err.println("Nepodarilo sa nachystat sokety.");
            return false;
        }
    }
    
    private void vyrobASpustiUlohy() {
        // kazdy soket ma svoje vlakno
        exekutor = Executors.newFixedThreadPool(pocetSoketov);
        ulohy = new ArrayList<>();
        PrijimaciaUloha prijimaciaUloha = null;
        
        if (jeNove) {
            for (int i = 0; i < pocetSoketov; i++) {
                prijimaciaUloha = new PrijimaciaUloha(
                        sokety.get(i),
                        i * velkostDielu,
                        zapisovanySubor,
                        pocetPrijatychBajtov);
                ulohy.add(prijimaciaUloha);
            }
        } else {
            for (int i = 0; i < pocetSoketov; i++) {
                prijimaciaUloha = new PrijimaciaUloha(
                        sokety.get(i),
                        offsety.get(i),
                        zapisovanySubor,
                        pocetPrijatychBajtov);
                ulohy.add(prijimaciaUloha);
            }
        }
        
        try {
            // blokovana operacia - cakame, kym sa ukoncia vsetky ulohy
            futures = exekutor.invokeAll(ulohy);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            System.err.println("Tato vynimka nastala prerusenim, ale nepoznam nikoho, kto by ho mohol sposobit.");
        }
    }
    
    private void zoberVysledky() {
        offsetyPoPreruseni = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            try {
                f.get();
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof FileNotFoundException) {
                    System.err.println("Nepodarilo sa otvorit subor na zapisovanie.");
                } else if (cause instanceof PredcasneUkonceniePrijimaniaException) {
                    System.err.println("Soket bol uzavrety pred koncom prijimania.");
                    long offset = ((PredcasneUkonceniePrijimaniaException) (cause)).getOffset();
                    offsetyPoPreruseni.add(offset);
                }
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                System.err.println("Tato vynimka nastava prerusenim, ale nepoznam nikoho, kto by ho mohol sposobit.");
            }
        }
    }
    
    private void zavriSokety() {
        for (Socket soket : sokety) {
            if (!soket.isClosed()) { // ked ich zatvarame, tak uz niektore mohli byt zavrete
                try {
                    soket.close();
                } catch (IOException ex) {
                    System.err.println("Soket sa nepodarilo zavriet.");
                }
            }
        }
    }

    /**
     * Zavretim soketov prerusi stahovanie.
     *
     * @param jePrerusene true, ak chce uzivatel prerusit stahovanie s moznostou
     * obnovy, false, ak ho chce ukoncit
     */
    public void prerusStahovanie(boolean jePrerusene) {
        this.jePrerusene = jePrerusene;
        this.jeUkoncene = !jePrerusene;
        zavriSokety();
    }
    
    public void ulozStav() {
        zoberVysledky();
        
        File info = new File("infooprerusenom.txt");
        PrintWriter pw = null;
        
        try {
            pw = new PrintWriter(info);
            pw.println(nazovSuboru);
            pw.println(pocetSoketov);
            pw.println(pocetPrijatychBajtov.get());
            for (Long offset : offsetyPoPreruseni) {
                pw.println(offset);
            }
            System.out.println("Stav stahovania ulozeny.");
        } catch (FileNotFoundException ex) {
            System.err.println("Nepodarilo sa ulozit stav preruseneho stahovania.");
        } finally {
            if (pw != null) {
                pw.close();
            }
        }
    }
    
    public void vymazStahovanySubor() {
        if (!zapisovanySubor.delete()) {
            System.err.println("Nepodarilo sa vymazat stahovany subor.");
        } else {
            System.out.println("Stahovanie ukoncene.");
        }
    }
    
    public AtomicLong getPocetPrijatychBajtov() {
        return pocetPrijatychBajtov;
    }
    
    public long getVelkostSuboru() {
        return velkostSuboru;
    }
    
    public boolean isJePrerusene() {
        return jePrerusene;
    }
    
    public boolean isJeUkoncene() {
        return jeUkoncene;
    }
    
}
