#import "../funkcije.typ": todo

= Testovi <testovi>

Pošto je za korektno funkcionisanje sistema potrebno mnogo kompleksnih funkcionalnosti i algoritama, potrebno je izvršiti intezivno testiranje istih da bi se dokazala pravilna implementacija. Slojevi od kojih se sistem sastoji su testirani izolovano, sa time da se slojevi na višim apstrakcionim nivoima oslanjaju na slojeve na nižim apstrakcionim nivoima.

== Organizacija testova

Sve testove u sistemu podržava _JUnit_ biblioteka. _JUnit_ sadrži razne konfiguracione parametre, a za _LBDB_ sistem testiranja su najbitniji parametri koji omogućavaju #link(<disk-filesystem>)[definisanje čistača] i parametri koji omogućavaju paralelno pokretanje testova.

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

Sistem prati standardnu definiciju strukture direktorijuma izvornog koda _Maven_ sistema za upravljanje zavisnostima. Više o njemu u #link(<buildsystem>)[pregledu sistema]. Po _Maven_-u, testovi se nalaze unutar `src/test/java` direktorijuma, a konfiguracioni parametri _JUnit_ biblioteke se nalaze unutar `src/test/resources` direktorijuma. Testovi su grupisani po istim modulima kao i glavni izvorni kod.

=== Testno okruženje

Da bi se postiglo korektno i unifikovano testiranje svih funkcionalnosti, potrebno je pružiti im odgovarajuće testno okruženje. Pošto većina funkcionalnosti zahteva rad sa datotekama, glavna dužnost testnog okruženja je da izoluje direktorijume gde će se ove datoteke nalaziti. Time se postiže da test _A_ koji kreira na primer tri tabele ne može da utiče na test _B_ koji kreira dve tabele gde se imena tabela poklapaju.

`TestUtils` je pomoćna klasa koja pruža implementaciju ove izolacije, ali pruža i dodatne pomoćne metode koje olakšavaju testiranje:
- provera postojanja datoteka,
- dobavljanje privatnih polja putem _Java_ refleksije.

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

=== Sistem izolacije direktorijuma na disku <disk-filesystem>

Prvi od od dva načina pokretanja testova je u okviru direktorijuma koji se nalaze na fizičkom disku. Prednosti ovog načina pokretanja su laki pregled generisanih datoteka zarad otklanjanja grešaka i nezahtevno pokretanje. Mana ovog načina pokretanja je brzina jer je pristup fizičkom disku spor.

Da bi se postigla izolacija testova i kroz iteracije pokretanja istih testova, potrebno je očistiti stare direktorijume. _JUnit_ omogućava konfiguraciju čistača, to jest funkcije koja se izvršava pre svih testova. `GlobalCleanup` klasa sadrži ovu logiku.

=== Sistem izolacije direktorijuma u radnoj memoriji

Drugi od dva načina pokretanja testova je u okviru direktorijuma koji se nalaze u radnoj memoriji. Pošto je _LBDB_ sistem kompatibilan sa `java.nio.file` _API_-jem, rukovođenje direktorijumima u radnoj memoriji se vrši preko _Jimfs_ biblioteke. Prednost ovog načina pokretanja je brzina testova. Mane ovog načina pokretanja su težak pristup datotekama zarad otklanjanja grešaka i velika potrošnja radne memorije.

U okviru `TestUtils` klase se podešava način pokretanja testova, gde je pokretanje u radnoj memoriji podrazumevano podešeno. `TestUtils` definiše jednu instancu `Jimfs` implementacije `Filesystem` klase koja se koristi za sve testove. Nema potrebe za čišćenjem preko `GlobalCleanup` klase jer se radna memorija sama čisti kada se proces u kom su pokrenuti testovi završi.
