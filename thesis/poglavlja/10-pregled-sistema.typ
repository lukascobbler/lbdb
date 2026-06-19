#import "../funkcije.typ": todo

= Pregled sistema <pregled-sistema>

U okviru ovog poglavlja su objašnjeni raznovrsni detalji sistema koji nisu vezani za same funkcionalnosti sistema.

== Izgradnja i pokretanje <buildsystem>

Sistem koristi _Maven_ za: automatizaciju kompilacije, izgradnju artefakata (aplikacija koje se pokreću) i za rukovođenje zavisnostima. U _Maven_ ekosistemu, izvorni kod prati striktno definisanu strukturu i nalazi se unutar `src/main` direktorijuma.

Za korektno funkcionisanje _Maven_ aplikacija, potrebno je definisati `pom.xml` datoteku u kojoj se nalaze sve neophodne instrukcije potrebne _Maven_-u.

Po instrukcijama `pom.xml` datoteke _LBDB_ sistema, klijentske aplikacije i serverska aplikacija se grade odvojeno, u tri različita artefakta. Ovo omogućava jednostavno odvojeno pokretanje. Nakon izgradnje, artefakti se mogu pronaći unutar `target` direktorijuma pod imenima: `LBDBServer.jar`, `LBDBClient.jar` i `BulkExecutor.jar`.

#figure(
  ```sh
  mvn clean package -Dmaven.test.skip=true
  ```,
  caption: [
    Izgradnja svih artefakata sistema, bez pokretanja testova
  ],
)<fig:build>

#figure(
  ```sh
  mvn test
  ```,
  caption: [
    Pokretanje svih testova u sistemu
  ],
)<fig:pokretanje_testova>

=== Rukovođenje zavisnostima

Klijentske aplikacije i serverska aplikacija dele kod za:
- protokol komunikacije,
- definiciju svih ključnih reči (zbog _auto complete_ funkcionalnosti klijentske aplikacije)
- definiciju konstanti,
- definiciju šeme i tipa vrednosti zbog korektnog ispisa.

Preostale zavisnosti i kod nisu deljeni.

==== Zavisnosti servera

Zavisnosti servera su sledeće:
- `datasketches-java` za _Java_ implementaciju _HyperLogLog_ strukture podataka#footnote[https://datasketches.apache.org/],
- `annotations` pruža dodatne _Java_ anotacije poput `@NotNull`#footnote[https://github.com/JetBrains/java-annotations].

==== Zavisnosti klijenta <zavisnosti-klijenta>

Zavisnosti običnog klijenta su sledeće:
- `jline-reader`, `jline-terminal` i `jline-terminal-jna` pružaju implementaciju terminala i omogućavaju sistemski agnostičnu podršku za _UTF-8_ ispis#footnote[https://github.com/jline/jline3],
- `net.java.dev.jna:jna` za pristup _native_ instrukcijama operativnog sistema#footnote[https://github.com/java-native-access/jna].

Zavisnosti `BulkExecutor` klijenta su iste kao i zavisnosti običnog klijenta, sa time da `jline` terminal nije iskorišćen.

==== Zavisnosti testnog okruženja

Ove zavisnosti se koriste u testnom okruženju i nisu deo artefakta:
- `junit-jupiter-engine` i `junit-jupiter-params` za pokretanje i definisanje testova,
- `jimfs` je implementacija sistema datoteka u radnoj memoriji#footnote[https://github.com/google/jimfs],
- `mockito-core` i `mockito-junit-jupiter` za pravljenje objekata koji imaju praznu implementaciju a neophodni su za pozivanje funkcija i metoda#footnote[https://github.com/mockito/mockito].

== Sistemska konfiguracija

U okviru sistema postoji i konfiguraciona klasa `LBDBSettings` preko koje je moguće postaviti parametre izbora algoritama ili vrednosti za određene operacije. Podrazumevane vrednosti su dobar izbor za nenadgledanu inicijalizaciju sistema i mogu se videti u sledećem bloku koda:

#figure(
  ```java
  public class LBDBSettings {
      public boolean UNDO_ONLY_RECOVERY = true;
      public int BLOCK_SIZE = 4096;
      public int BUFFER_POOL_SIZE = 128;
      public BufferStrategy bufferStrategy = BufferStrategy.LRU;
      public String LOG_FILE = "log_file";
      public QueryPlannerType queryPlannerType = QueryPlannerType.BETTER;
      public UpdatePlannerType updatePlannerType = UpdatePlannerType.BASIC;
  }
  ```,
  caption: [
    Kod sistemske klase `LBDBSettings`
  ],
)<fig:lbdbsettings>

#pagebreak()

Redom, parametri označavaju:
1. algoritam oporavka sistema,
2. veličina jednog bloka u bajtovima gde jedan blok predstavlja najmanju jedinicu interakcije sa diskom,
3. količina bafera sa kojim sistem raspolaže,
4. algoritam izbora bafera koji će biti smenjen,
5. putanja do datoteke gde se čuvaju podaci potrebni za oporavak sistema i poništavanje transakcija,
6. implementacija planera za operacije upita; podržana samo `BETTER` implementacija,
7. implementacija planera za operacije modifikacije; podržana samo `BASIC` implementacija.

== Integracija sa _GitHub_ platformom

_GitHub_#footnote[https://github.com/] platforma omogućava pokretanje testova (eng. _Continuous Integration_, _CI_) i izgradnju i objavljivanje aplikacija (eng. _Continuous Delivery_, _CD_) u okviru njenih servera. _LBDB_ sistem koristi ovu mogućnost i definiše specijalnu _GitHub_ datoteku za _CI_. U okviru nje se definiše _Windows_ i _Ubuntu Linux_ okruženje za testiranje. Rezultat testova stoji u okviru bedža u `README.md` datoteci koja se nalazi u _LBDB_ repozitorijumu.

== Primer funkcionisanja celokupnog sistema

Dijagram sekvence (slika @fig:sekvenca_ceo_sistem) predstavlja generalno ponašanje svih slojeva _LBDB_ sistema. Opisani su slučajevi za `SELECT` naredbu, za naredbe upravljanja životnim ciklusom transakcija i za naredbe modifikacije tabela. Specifičnosti poput algoritma pravljenja stabla planova ili algoritma poništavanja transakcija nisu obrađeni jer bi dijagram bio prevelik, a njihovo objašnjenje je svakako dato u poglavljima gde su definisani.

#pagebreak()

#figure(
  rotate(90deg, reflow: true)[
    #image("../dijagrami/sekvenca_celog_sistema.svg")
  ],
  caption: [
    Dijagram sekvence funkcionisanja celog sistema
  ],
)<fig:sekvenca_ceo_sistem>
