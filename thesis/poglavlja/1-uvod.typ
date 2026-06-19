#import "../funkcije.typ": todo

= Uvod <uvod>

== O sistemu

_LBDB_ je višekorisniški sistem sa transakcijama za upravljanje relacionim bazama podataka. Njegova svrha je da prima naredbe dobijene od klijenata, interpretira ih po standardu _SQL_ programskog jezika i da vrati rezultat tim klijentima. Rezultat može biti broj pogođenih slogova za slučaj modifikacionih operacija ili rezultujuća tabela za slučaj upita. Zarad efikasnog interpretiranja, potrebno je obezbediti algoritme i strukture podataka za sledeće module: upravljanje datotekama, rad sa transakcijama, rad sa metapodacima, parsiranje upita, pravljenje planova izvršavanja upita, izvršavanje upita i za mrežnu komunikaciju.
Moduli ovog sistema su raspoređeni tako da se svaki brine o jednoj grupi algoritama i struktura podataka kroz koju upit prolazi.

#figure(
  image("../dijagrami/uprosceni_sistem.pdf", height: 54%),
  caption: [
    Uprošćeni dijagram slojevite arhitekture sistema
  ],
)<fig:arh_sistema>

Svaki sloj iz uprošćene arhitekture sistema je posebno detaljno objašnjen u nastavku, a uz te slojeve se dodatno objašnjavaju i transakcije, koje su isprepletene kroz ceo sistem. Sličan dijagram koji opisuje celu arhitekturu sistema, ali mnogo detaljnije, se može pronaći na slici @fig:sekvenca_ceo_sistem.

Osnovna struktura i algoritmi su izvedeni iz knjige _Database Design And Implementation_ @simpledb, a njihova unapređenja su deo ovog rada. Knjiga definiše zadatke na kraju svakog modula i ti zadaci su osnova za unapređivanje sistema. Detaljan spisak urađenih zadataka i njihovih beleški se može pronaći u okviru repozitorijuma #footnote[https://github.com/lukascobbler/lbdb].

== Klijentsko-serverska arhitektura

_LBDB_ sistem se pokreće kao server i njemu se pristupa preko mreže i specijalnog protokola. Postoji implementacija klijentske aplikacije koja implementira ovaj protokol. Detalji se mogu pronaći u poglavlju @klijent-server koje opisuje klijentsko-serversku arhitekturu sistema.

== Sistem za obradu upita <sistem_za_obradu_upita>

`LBDB` klasa predstavlja najviši apstrakcioni nivo sistema obrade upita na koji se server oslanja. Služi za orkestraciju upravljača glavnih podsistema: menadžer transakcija (sekcija @sesije), menadžer metapodataka (sekcija @metadata-menadzer) i planer (sekcija @planner-klasa). Takođe, klasa `LBDB` je odgovorna za logiku inicijalizacije i oporavljanja sistema od neočekivanog gašenja, za određeni direktorijum baze podataka.
