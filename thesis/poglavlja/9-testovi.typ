#import "../funkcije.typ": todo

= Testovi <testovi>

Pošto je za korektno funkcionisanje sistema potrebno mnogo kompleksnih funkcionalnosti i algoritama, potrebno je izvršiti intenzivno testiranje istih da bi se dokazala pravilna i efikasna implementacija. Slojevi od kojih se sistem sastoji su testirani izolovano, s tim da se slojevi višeg apstrakcionog nivoa ne testiraju odvojeno od slojeva nižeg apstrakcionog nivoa.

== Organizacija testova

Sve testove u sistemu podržava _JUnit_#footnote[https://junit.org/] biblioteka. _JUnit_ sadrži razne konfiguracione parametre, a za testiranje _LBDB_ sistema su najbitniji parametri koji omogućavaju definisanje čistača i parametri koji omogućavaju paralelno pokretanje testova.

#figure(
  ```properties
  junit.jupiter.extensions.autodetection.enabled = true
  junit.jupiter.execution.parallel.enabled = true
  junit.jupiter.execution.parallel.mode.default = concurrent
  ```,
  caption: [
    Konfiguracioni parametri _JUnit_ biblioteke
  ],
)<fig:junit_konfiguracija>

Sistem prati standardnu definiciju strukture direktorijuma izvornog koda _Maven_#footnote[https://maven.apache.org/] sistema za upravljanje zavisnostima. Više o njemu u o pregledu sistema (sekcija @buildsystem). Po _Maven_-u, testovi se nalaze unutar `src/test/java` direktorijuma, a konfiguracioni parametri _JUnit_ biblioteke se nalaze unutar `src/test/resources` direktorijuma. Testovi su grupisani po istim modulima kao i glavni izvorni kod.

=== Testno okruženje

Da bi se postiglo korektno i uniformno testiranje svih funkcionalnosti, potrebno je pružiti im odgovarajuće testno okruženje. Pošto većina funkcionalnosti zahteva rad sa datotekama, glavna dužnost testnog okruženja je da izoluje direktorijume gde će se ove datoteke nalaziti. Time se postiže da test _A_ koji kreira na primer tri tabele ne može da utiče na test _B_ koji kreira dve tabele gde se imena tabela poklapaju.

`TestUtils` je pomoćna klasa koja pruža implementaciju ove izolacije, ali pruža i dodatne pomoćne metode koje olakšavaju testiranje:
- provera postojanja datoteka,
- dobavljanje privatnih polja putem _Java_ refleksije.

=== Sistem izolacije direktorijuma na disku <disk-filesystem>

Prvi od dva načina pokretanja testova je u okviru direktorijuma koji se nalaze na fizičkom disku. Prednosti ovog načina pokretanja su laki pregled generisanih datoteka zarad otklanjanja grešaka i nezahtevno pokretanje. Mana ovog načina pokretanja je brzina jer je pristup fizičkom disku spor.

Da bi se postigla izolacija i kroz iteracije pokretanja istih testova, potrebno je očistiti stare direktorijume. _JUnit_ omogućava konfiguraciju čistača, to jest funkcije koja se izvršava pre svih testova. `GlobalCleanup` klasa sadrži ovu logiku.

=== Sistem izolacije direktorijuma u radnoj memoriji

Drugi od dva načina pokretanja testova je u okviru direktorijuma koji se nalaze u radnoj memoriji. Pošto je _LBDB_ sistem kompatibilan sa `java.nio.file` _API_-jem, rukovođenje direktorijumima u radnoj memoriji se vrši preko _Jimfs_ biblioteke. Prednost ovog načina pokretanja je brzina testova. Mane ovog načina pokretanja su težak pristup datotekama zarad otklanjanja grešaka i velika potrošnja radne memorije.

U okviru `TestUtils` klase se podešava način pokretanja testova, gde je pokretanje u radnoj memoriji podrazumevano podešeno. `TestUtils` definiše jednu instancu `Jimfs` implementacije `Filesystem` klase koja se koristi za sve testove. Nema potrebe za čišćenjem preko `GlobalCleanup` klase jer se radna memorija sama čisti kada se proces u kom su pokrenuti testovi završi.

== Funkcionalni testovi

Funkcionalni testovi potvrđuju da li su algoritmi i strukture podataka korektni, to jest da li imaju smisleno i tačno ponašanje.

Za podsistem relacionih operatora, postoji pomoćna klasa `QueryTestUtils` koja pruža dodatne pomoćne metode za testiranje ovog podsistema:
- inicijalizacija i popunjavanje jedne tabele sa $250$ slogova, koja ima tri _Integer_, tri _String_ i tri _Boolean_ kolone,
- inicijalizacija i popunjavanje dve tabele sa po $250$ slogova koje imaju istu šemu kao tabela u prethodnoj pomoćnoj metodi.

Za podsistem planiranja, postoji pomoćna klasa `PlanTestUtils` koja pruža dodatne pomoćne metode za testiranje ovog podsistema:
- pokretanje *samo* provere `SELECT` naredbe i vraćanje proširenog `SelectStatement` objekta,
- pokretanje *samo* provere modifikacionih naredbi,
- pravljenje stabla planova za `SELECT` naredbe,
- pokretanje modifikacionih naredbi,
- kreiranje novih transakcija, korisno za proveru izolacije,
- inicijalizacija tri prazne ili tri popunjene tabele, gde prve dve imaju istu šemu kao kod pomoćne klase za testiranje podsistema relacionih operatora, a treća sadrži samo jedno _Integer_ polje i korisna je za testiranje spajanja tabela samih sa sobom.

== Testiranje performansi

Testovi performanse imaju zadatak da potvrde pretpostavke iz teksta rada. Za korektno izvršavanje testova performansi, potrebno je pružiti mehanizme pokretanja koji vrše dodatnu izolaciju. Testovi performanse uvek moraju da se izvršavaju serijski, u zasebnim procesima i na disku. `TestUtils` već pruža mogućnost izbora izvršavanja na disku, dok se serijsko izvršavanje i izvršavanje u zasebnim procesima konfiguriše preko _Maven_ sistema i _JUnit_ biblioteke. Bitna napomena je da je vreme izvršavanja zavisno od hardvera, dok ostale metrike nisu, ali vreme izvršavanja i dalje može pokazati korisne uvide. Svaki test se pokreće deset puta i prikazane vrednosti su medijalne da bi se izbegao šum.

Da bi _Maven_ instancirao nov _JVM_ proces za svaki test, neophodno je da svaki test stoji u zasebnoj klasi. Pošto svi testovi performanse jednog dela sistema strukturalno izgledaju isto, najbolji način da se ovo obezbedi je da se definiše jedna apstraktna klasa u kojoj stoji logika testa i po jedna klasa naslednica za svaku željenu kombinaciju parametara testa. Listing @fig:apst_klasa_podesavanja pokazuje _JUnit_ anotacije koje se vezuju za apstraktnu klasu i koji su neophodni za serijsko pokretanje.

#figure(
  ```java
  @Execution(ExecutionMode.SAME_THREAD)
  @EnabledIfSystemProperty(named = "benchmark", matches = ".*")
  ```,
  caption: [
    Podešavanja apstraktne klase testa performanse
  ],
)<fig:apst_klasa_podesavanja>

=== Testovi performanse algoritama smene bafera <test-perf-asb>

Postoje četiri tipa testova koji imaju funkciju testiranja algoritama smene bafera pod različitim okolnostima. Tipovi su opisani naredbom koja se izvršava i količinom slogova u tabelama. Tipovi testova koji modifikuju podatke (sufiks _\_M_) imaju dodatnu logiku pokretanja dve `UPDATE` naredbe koja će učiniti bafere obe tabele "prljavim" i time dati mogućnost izbora na osnovu tog podatka.

#figure(
  ```java
  public enum BufferTestType {
      HOT_BUFFERS("SELECT * FROM table1, table2;", 1000, 50),
      CONTINUOUS_READS("SELECT * FROM table1;", 50000, 0),
      HOT_BUFFERS_M("SELECT * FROM table1, table2;", 1000, 50),
      CONTINUOUS_READS_M("SELECT * FROM table1;", 50000, 0);

      BufferTestType(String query, int numRecords1, int numRecords2) { ... }
  }
  ```,
  caption: [
    Tipovi testova za algoritme smene bafera
  ],
)<fig:tipovi_testova_asb>

#[
  #figure(
    ```sh
    mvn test -Dgroups="[TIP PO ENUMERACIJI]" -Dbenchmark
    ```,
    caption: [
      Pokretanje testova performanse algoritama smene bafera određenog tipa
    ],
  )<fig:pokretanje_testova_asb>
]

#[
  #show table.cell: set text(size: 10pt)
  #set table(inset: 4.9pt)
  #set table(columns: (1.4fr, 0.9fr, 0.5fr, 0.5fr, 0.5fr, 0.7fr))
  #set table(align: (center, center))

  #figure(
    {
      set par(justify: false)
      table(
        [Tip testa],
        [Algoritam],
        [Čitanja],
        [Pisanja],
        [Pogoci],
        [Vreme (ms)],
        //
        [_HOT_BUFFERS_],
        [_LRU_],
        [$337$],
        [$1$],
        [$13007$],
        [$229$],
        //
        [_HOT_BUFFERS_],
        [_FIFO_],
        [$337$],
        [$1$],
        [$13006$],
        [$219$],
        //
        [_HOT_BUFFERS_],
        [_Clock_],
        [$1792$],
        [$1$],
        [$11552$],
        [$227$],
        //
        [_HOT_BUFFERS_],
        [_First unmodified_],
        [$13342$],
        [$1$],
        [$2$],
        [$238$],
        //
        [_HOT_BUFFERS_],
        [_LRM_],
        [$13342$],
        [$1$],
        [$2$],
        [$252$],
        //
        [_HOT_BUFFERS_],
        [_Naive_],
        [$13342$],
        [$1$],
        [$2$],
        [$236$],
      )
    },
    caption: [Rezultati testiranja algoritama smene bafera za _HOT_BUFFERS_],
  )<tbl:rezultati_testova_hot_buffers>

  #figure(
    {
      set par(justify: false)
      table(
        [Tip testa],
        [Algoritam],
        [Čitanja],
        [Pisanja],
        [Pogoci],
        [Vreme (ms)],
        //
        [_CONTINUOUS_READS_],
        [_LRU_],
        [$16669$],
        [$1$],
        [$3$],
        [$162$],
        //
        [_CONTINUOUS_READS_],
        [_FIFO_],
        [$16669$],
        [$1$],
        [$3$],
        [$162$],
        //
        [_CONTINUOUS_READS_],
        [_Clock_],
        [$16669$],
        [$1$],
        [$3$],
        [$164$],
        //
        [_CONTINUOUS_READS_],
        [_First unmodified_],
        [$16671$],
        [$1$],
        [$1$],
        [$157$],
        //
        [_CONTINUOUS_READS_],
        [_LRM_],
        [$16671$],
        [$1$],
        [$1$],
        [$162$],
        //
        [_CONTINUOUS_READS_],
        [_Naive_],
        [$16671$],
        [$1$],
        [$1$],
        [$161$],
      )
    },
    caption: [Rezultati testiranja algoritama smene bafera za _CONTINUOUS_READS_],
  )<tbl:rezultati_testova_continuous_reads>

  #figure(
    {
      set par(justify: false)
      table(
        [Tip testa],
        [Algoritam],
        [Čitanja],
        [Pisanja],
        [Pogoci],
        [Vreme (ms)],
        //
        [_HOT_BUFFERS_M_],
        [_LRU_],
        [$3505$],
        [$3317$],
        [$64991$],
        [$1046$],
        //
        [_HOT_BUFFERS_M_],
        [_FIFO_],
        [$3488$],
        [$3291$],
        [$65010$],
        [$1082$],
        //
        [_HOT_BUFFERS_M_],
        [_Clock_],
        [$10640$],
        [$2036$],
        [$57855$],
        [$1103$],
        //
        [_HOT_BUFFERS_M_],
        [_First unmodified_],
        [$13412$],
        [$3542$],
        [$55083$],
        [$1118$],
        //
        [_HOT_BUFFERS_M_],
        [_LRM_],
        [$68481$],
        [$3671$],
        [$14$],
        [$1232$],
        //
        [_HOT_BUFFERS_M_],
        [_Naive_],
        [$68481$],
        [$3671$],
        [$14$],
        [$1170$],
      )
    },
    caption: [Rezultati testiranja algoritama smene bafera za _HOT_BUFFERS_M_],
  )<tbl:rezultati_testova_hot_buffers_m>

  #figure(
    {
      set par(justify: false)
      table(
        [Tip testa],
        [Algoritam],
        [Čitanja],
        [Pisanja],
        [Pogoci],
        [Vreme (ms)],
        //
        [_CONTINUOUS_READS_M_],
        [_LRU_],
        [$166695$],
        [$161509$],
        [$45$],
        [$1751$],
        //
        [_CONTINUOUS_READS_M_],
        [_FIFO_],
        [$166695$],
        [$162112$],
        [$45$],
        [$1709$],
        //
        [_CONTINUOUS_READS_M_],
        [_Clock_],
        [$166695$],
        [$95856$],
        [$45$],
        [$1586$],
        //
        [_CONTINUOUS_READS_M_],
        [_First unmodified_],
        [$166609$],
        [$174945$],
        [$131$],
        [$1814$],
        //
        [_CONTINUOUS_READS_M_],
        [_LRM_],
        [$166735$],
        [$175019$],
        [$5$],
        [$1816$],
        //
        [_CONTINUOUS_READS_M_],
        [_Naive_],
        [$166735$],
        [$175019$],
        [$5$],
        [$1799$],
      )
    },
    caption: [Rezultati testiranja algoritama smene bafera za _CONTINUOUS_READS_M_],
  )<tbl:rezultati_testova_continuous_reads_m>
]

U tabelama @tbl:rezultati_testova_hot_buffers, @tbl:rezultati_testova_continuous_reads, @tbl:rezultati_testova_hot_buffers_m, @tbl:rezultati_testova_continuous_reads_m stoje vrednosti metrika: broja čitanja sa diska, broja upisa na disk, broj pogodaka bafera koji je već u memoriji i vreme izvršavanja.

Rezultati testiranja performansi za tip testa gde se radi upit koji zahteva zadržavanje istih bafera u memoriji su predstavljeni tabelom @tbl:rezultati_testova_hot_buffers. Ovi podaci jasno pokazuju podelu između algoritama koji uspešno prepoznaju radni skup unutrašnje petlje i onih koji potpuno zakazuju. _LRU_ i _FIFO_ ostvaruju optimalne rezultate sa minimalnim brojem čitanja i maksimalnim brojem pogodaka jer uspešno zadržavaju blokove manje tabele u memoriji. Nasuprot njima, _Naive_, _LRM_ i _First Unmodified_ pokazuju najgore performanse jer konstantno izbacuju potrebne podatke pre nego što se oni ponovo iskoriste, dok je _Clock_ pozicioniran između njih sa umerenim brojem promašaja.

Rezultati testiranja performansi za tip testa gde radi upit koji zahteva sekvencijalno čitanje su predstavljeni tabelom @tbl:rezultati_testova_continuous_reads. U ovom scenariju svi ispitivani algoritmi pokazuju praktično identično ponašanje jer veličina tabele drastično prevazilazi kapacitet bafer pula. Nijedna strategija ne može da zadrži podatke za ponovnu upotrebu, pa svi algoritmi završavaju sa istim brojem čitanja i minimalnim brojem pogodaka. Vreme izvršavanja je ujednačeno jer usko grlo u ovom testu diktira isključivo brzina samog diska.

Rezultati testiranja performansi za tip testa gde se prvo modifikuju podaci pa se radi upit koji zahteva zadržavanje istih bafera u memoriji su predstavljeni tabelom @tbl:rezultati_testova_hot_buffers_m. Uvođenje modifikacije podataka dodatno naglašava razlike u efikasnosti, gde _LRU_ i _FIFO_ ponovo postižu najbolje rezultate sa najmanje čitanja jer uspešno čuvaju radni skup unutrašnje petlje. _First Unmodified_ donekle uspešno iskorišćava činjenicu da su neki baferi "prljavi", ali daje lošije rezultate jer ne izbacivanjem "prljavih" bafera previše sužava raspoloživi prostor u memoriji. _Naive_ i _LRM_ uvek izbacuju pogrešne bafere. _Clock_ zauzima sredinu jer uspešno smanjuje broj upisa na disk, ali uz cenu većeg broja čitanja.

Rezultati testiranja performansi za tip testa gde se prvo modifikuju podaci pa se radi upit koji zahteva sekvencijalno čitanje su predstavljeni tabelom @tbl:rezultati_testova_continuous_reads_m. Pri masovnom skeniranju izmenjenih podataka broj čitanja ostaje isti za sve strategije jer se svaka stranica mora povući sa diska, ali se ključna razlika uočava u broju upisa. _Clock_ algoritam se ovde pokazuje kao najefikasniji jer značajno rasterećuje rad sa diskom u odnosu na _LRU_ i _FIFO_.

Generalno, u svim testnim tipovima, broj bafera u listi bafera je izabran da bude $15$ jer se time postiže da algoritmi izbora bafera za smenu moraju da rade pod pritiskom i izaberu najbolje bafere za smenu. Jedan slog tabele `table1` je dužine $1240$ bajtova, jedan slog tabele `table2` je dužine $916$ bajtova, dok je veličina bloka u sistemu podešena na $4096$ bajtova. Ovo znači da u jednom bloku staje tri sloga tabele `table1` ili četiri sloga tabele `table2`.

Analizom podataka rezultata testiranja, tvrdnje iz sekcije @algoritmi-smene-bafera su potvrđene. _LRU_ je ubedljivo pobednik za generalni slučaj, ali postoje i situacije gde ne mora biti najbrži.

=== Testovi performanse _RBO_ tehnike u planiranju `SELECT` naredbi

Postoje dva tipa testova koji imaju funkciju testiranja algoritma planiranja `SELECT` naredbe. Tipovi su opisani naredbom koja se izvršava i količinom slogova u tabeli. Kod prvog tipa testa, prvo se stavlja tabela čija je veličina sloga veća. Kod drugog tipa testa, prvo se stavlja tabela čija je veličina sloga manja.

#figure(
  ```java
  public enum ProductOrderTestType {
      BIG_SMALL("SELECT * FROM big, small;", 1000),
      SMALL_BIG("SELECT * FROM small, big;", 1000);

      ProductOrderTestType(String query, int numRecords) { ... }
  }
  ```,
  caption: [
    Tipovi testova za proizvod velikih i malih tabela
  ],
)<fig:tipovi_testova_bs>

#[
  #figure(
    ```sh
    mvn test -Dgroups="[TIP IZ ENUMERACIJE]" -Dbenchmark
    ```,
    caption: [
      Pokretanje testova performanse proizvoda velike i male tabele sa _LRU_ algoritmom smene bafera
    ],
  )<fig:pokretanje_testova_asb>
]

#[
  #figure(
    ```sh
    mvn test -Dgroups="[TIP IZ ENUMERACIJE]" -Dbenchmark -Dnaive
    ```,
    caption: [
      Pokretanje testova performanse proizvoda velike i male tabele sa _Naive_ algoritmom smene bafera
    ],
  )<fig:pokretanje_testova_asb_n>
]

#[
  #show table.cell: set text(size: 10pt)
  #set table(inset: 5pt)
  #set table(columns: (0.5fr, 0.9fr, 1fr, 0.3fr, 0.5fr))
  #set table(align: (center, center))

  #figure(
    {
      set par(justify: false)
      table(
        [Tip testa],
        [Algoritam smene bafera],
        [$text("B")(T_r) = text("B")(T_l) + (text("RPB")(T_l) * text("B")(T_l) * text("B")(T_d))$],
        [Čitanja],
        [Vreme (ms)],
        //
        [_BIG_SMALL_],
        [_LRU_],
        [$2338$],
        [$338$],
        [$4926$],
        //
        [_BIG_SMALL_],
        [_Naive_],
        [$2338$],
        [$2342$],
        [$4845$],
      )
    },
    caption: [Rezultati testiranja _RBO_ tehnike za tip _BIG_SMALL_],
  )<tbl:rezultati_testova_big_small>

  #figure(
    {
      set par(justify: false)
      table(
        [Tip testa],
        [Algoritam smene bafera],
        [$text("B")(T_r) = text("B")(T_l) + (text("RPB")(T_l) * text("B")(T_l) * text("B")(T_d))$],
        [Čitanja],
        [Vreme (ms)],
        //
        [_SMALL_BIG_],
        [_LRU_],
        [$547094$],
        [$338$],
        [$5099$],
        //
        [_SMALL_BIG_],
        [_Naive_],
        [$547094$],
        [$2342$],
        [$5367$],
      )
    },
    caption: [Rezultati testiranja _RBO_ tehnike za tip _SMALL_BIG_],
  )<tbl:rezultati_testova_small_big>
]

U tabelama @tbl:rezultati_testova_big_small, @tbl:rezultati_testova_small_big stoje vrednosti metrika: predviđeni broj čitanja formulom, zapravi broj čitanja i vreme izvršavanja. Dodatno, stoji i algoritam smene bafera jer korišćenjem _LRU_ algoritma se dodatno smanjuje broj pristupa (jer se blokovima manje tabele konstantno pristupa pa ostaju u memoriji), dok se korišćenjem _Naive_ algoritma ne postiže ova optimizacija.

Na osnovu izmerenih podataka, tvrdnje iz sekcije @plan-select su potvrđene. Algoritam planiranja `SELECT` naredbe uvek bira jeftiniju putanju i _RBO_ tehnika optimizacije ima vidljiv uticaj.
