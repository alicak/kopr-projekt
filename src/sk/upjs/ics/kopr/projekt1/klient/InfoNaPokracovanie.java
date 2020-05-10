package sk.upjs.ics.kopr.projekt1.klient;

import java.util.List;

/**
 * Informacie potrebne pre pokracovanie v stahovani suboru
 */
public class InfoNaPokracovanie {

    private String nazov;
    private int pocetSoketov;   
    private long pocetPrijatychBajtov;
    private List<Long> offsety;

    public InfoNaPokracovanie(String nazov, int pocetSoketov, long pocetPrijatychBajtov, List<Long> offsety) {
        this.nazov = nazov;
        this.pocetSoketov = pocetSoketov;
        this.pocetPrijatychBajtov = pocetPrijatychBajtov;
        this.offsety = offsety;
    }

    public String getNazov() {
        return nazov;
    }

    public int getPocetSoketov() {
        return pocetSoketov;
    }

    public long getPocetPrijatychBajtov() {
        return pocetPrijatychBajtov;
    }

    public List<Long> getOffsety() {
        return offsety;
    }
 
}
