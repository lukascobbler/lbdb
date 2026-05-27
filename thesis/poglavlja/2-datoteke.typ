#import "../funkcije.typ": todo

= Upravljanje datotekama <datoteke>

Upravljanje datotekama se vrši kroz više slojeva u sistemu, gde je svaki sloj odgovoran za organizaciju datoteka na različitom apstrakcionom nivou. Osnovna premisa je da sve operacije sa datotekama to jest diskom moraju da se izvršavaju u jedinicama blokova, jer je operativni sistem a i hardver (disk) optimizovan za rad sa njima @simpledb. Zbog toga što je blok najmanja jedinica interakcije sa diskom i datotekama, sva čitanja, pisanja i modifikacije podataka se rade zajedno sa celim blokom gde se ti podaci nalaze, a ne direktno.

== Upravljanje blokovima

Sistem za upravljanje blokovima je primarno zadužen za dobavljanje bloka sa diska gde se nalaze traženi podaci i za korektno zapisivanje _log_ podataka. Svaki blok ima svoj unikatni identifikator, koji je predstavljen Java _record_ strukturom. Svaki blok je vezan za datoteku i ima svoju blok poziciju u toj datoteci.

#figure(
  ```java
  public record BlockId(String filename, int blockNum) { }
  ```,
  caption: [
    Identifikator bloka
  ],
)<fig:blok_id>

=== Interfejs ka _file_ sistemu operativnog sistema

Najniži nivo apstrakcije predstavlja menadžer datoteka (`FileManager` klasa) koji ima funkciju interfejsa ka _file_ sistemu operativnog sistema i nema predstavu šta se nalazi u samim datotekama LBDB sistema. Menadžer datoteka čuva pokazivače na sve datoteke kojima sistem upravlja i omogućava višenitni bezbedan pristup istim, upotrebom Java `synchronized` ključne reči. Višenitni bezbedan pristup omogućava sistemu da podrži više različitih klijenata u isto vreme, ali nije dovoljan samo na ovom sloju, već je #link(<bezbedan_visenitni_pristup>)[detaljno obrađen] u okviru transakcija.

Moguće je podesiti sistem da koristi proizvoljnu veličinu jednog bloka, zavisno od prirode podataka kojima će baza podataka biti popunjena i to je glavni #link(<fig:lbdbsettings>)[parametar] menadžera datoteka.

=== Stranice

Stranica (eng. _page_) predstavlja sirove bajtove jednog bloka učitane u radnu memoriju. Iako nije nužno potrebno, sve vrednosti iz stranice zajedno sa njihovim tipom se pretvaraju u ekvivalentne Java objekte (koristeći `ByteBuffer` standardnu Java klasu) da bi se omogućio pristup operacijama iz Java standardne biblioteke za te tipove. Kada menadžer datoteka dobavlja određeni blok, bajtovi tog bloka se smeštaju u radnu memoriju u objekat stranice. Stranice su na apstrakcionom nivou ispod sistema baferovanja i koriste se kao potpora tog sistema.

Sistem podržava sledeće proste i kompozitne tipove podataka: _String_, _Boolean_ i _Integer_. Kod prostih tipova, iz stranice se samo čitaju njihovi bajtovi i pretvaraju u određeni Java objekat tog tipa, dok kod kompozitnih tipova kao što je _String_ potrebno je znati dužinu, same bajtove i kodiranje da bi se konstruisao Java objekat _String_ tipa.

=== Upravljanje _log_ datotekom

Jedan _log_ je niz bajtova čije interpretiranje ukazuje na to kako se promenila neka vrednost u nekom bloku.

_Log_ datoteka (čija je lokacija #link(<fig:lbdbsettings>)[podesiva]) je specijalna vrsta datoteke u kojoj se čuva niz _log_-ova. Na apstrakcionom nivou upravljanja blokovima, nije bitan sadržaj ove datoteke, već samo algoritmi neophodni za korektno prolaženje kroz nju i algoritmi za dodavanje novih _log_-ova. Ovi algoritmi se nalaze u menadžeru _log_-ova (`LogManager` klasa) i _log_ iteratoru (`LogIterator` klasa).

_Log_ datoteka je struktuirana tako da se noviji _log_-ovi nalaze u blokovima bližim kraju datoteke, a unutar jednog bloka _log_ fajla noviji _log_-ovi se nalaze pri početku bloka. Ako nema mesta da se upiše nov _log_, prelazi se u sledeći blok _log_ fajla.

Menadžer _log_-ova obezbeđuje algoritme upravljanja _log_ datotekom tako što čuva stranicu poslednjeg bloka _log_ fajla. Upravljanje _log_ datotekom podrazumeva dodavanje novih _log_-ova, upis _log_-ova na disk i arhiviranje _log_ datoteke.

Iterator _log_-ova obezbeđuje čitanje _log_ datoteke u korektnom redosledu i implementiran je pomoću Java `Iterator` interfejsa.

#figure(
  image("../dijagrami/log_fajl_izgled.pdf", width: 91%),
  caption: [
    Izgled _log_ datoteke i smer iteracije
  ],
)<fig:log_fajl>

Svaki blok _log_ datoteke na nultoj poziciji sadrži četvorobajtni broj koji predstavlja lokaciju prvog _log_-a u tom bloku. Svaki _log_ se sastoji iz sadržaja i svoje dužine koja je isto četvorobajtni broj.

Prilikom dodavanja novog _log_-a, sistem izračunava _log_ sekvencu novog _log_-a (eng. _log sequence number_) koja unikatno identifikuje zapisan _log_ i može se koristiti u daljim podsistemima za forsiranje pisanja prethodnih _log_-ova u _log_ fajl i time garantovati redosled operacija.

== Upravljanje baferima

Čišćenje stranice iz memorije nakon završetka operacije koja ju je koristila može biti jako neefikasno jer se za ponovni pristup tom bloku mora odlaziti do diska, pogotovo u višekorisniškim kontekstima i situacijama kada se istim blokovima često pristupa. Zbog toga se uvodi koncept bafera (eng. _buffer_) koji, uz algoritme u menadžeru bafera (`BufferManager` klasa), omogućava stranicama da ostanu u memoriji prilagodljivu količinu vremena. Posledica ovog sistema je da direktan pristup podacima sa diska više nije moguć, već se sve operacije obavljaju kroz bafere.

Bafer je omotač oko stranice koji sadrži dodatne podatke koji se mogu iskoristiti za implementaciju raznih algoritama ubrzanja sistema:
- kada je učitan
- kada je bilo poslednje pristupanje
- koji je njegov redni broj u listi bafera
- koja transakcija ga je poslednji put modifikovala i _log_ sekvenca njene poslednje operacije
- koliko transakcija ga trenutno upotrebljavaju, kraće rečeno broj pinova

Pošto je količina radne memorije ograničena, i količina bafera u sistemu isto mora biti ograničena. Menadžer bafera ima listu (#link(<fig:lbdbsettings>)[određene veličine]) bafera sa kojima raspolaže i potrebno je da poveća stepen iskorišćenosti bafera iz te liste što je više moguće. U idealnom, hipotetičkom scenariju, menadžer bafera bi predvideo budućnost i znao kojim baferima bi se sledeće pristupalo i izbacio iz memorije one koji su vremenski najdalje od pristupa. Ovaj scenario je očigledno nemoguć, pa je potrebno iskoristiti algoritme koji najbolje "predviđaju budućnost" na osnovu realnih podataka. Kada se bafer izbaci iz memorije, njegov sadržaj se piše u blok za koji je vezan i na taj način se podaci perzistiraju. Postoje i #link(<undo_only_recovery>)[druge situacije] kada se sadržaj bafera odmah zapisuje na disk.

Da bi se bafer izbacio iz memorije, ne sme da bude deo ni jedne aktuelne transakcije, to jest broj pinva mu mora biti nula. Postoje dva scenarija kada stranica koja do sad nije bila u memoriji treba da se mapira na bafer:

- U slučaju da ne postoji ni jedan bafer sa nula pinova, nova stranica čeka određeni vremenski period da se oslobodi neki bafer i ako se ni jedan bafer ne oslobodi, vraća se greška klijentu.
- U slučaju da postoji više bafera sa nula pinova, potrebno je izabrati koji će biti izbačen iz radne memorije pomoću algoritma izbora.

=== Algoritmi izbora smene bafera <algoritmi-smene-bafera>

U opticaju je nekoliko algoritama @simpledb za izbor bafera koji će biti smenjen i opcije su prikazane u okviru `BufferStrategy` enumeracije (#link(<fig:lbdbsettings>)[podesivo]):

#figure(
  ```java
  public enum BufferStrategy {
      NAIVE, FIFO, LRU, CLOCK,
      FIRST_UNMODIFIED, LRM
  }
  ```,
  caption: [
    Razičiti algoritmi izbora smene bafera
  ],
)<fig:strategije_bafera>

==== _Naive_

Naivni algoritam, kako mu i ime kaže, ne razmišlja mnogo o baferu kojeg će smeniti već samo uzima prvi bafer koji ima nula pinova. Očekivane loše performanse jer je velika šansa da će se smeniti bafer koji će se uskoro opet koristiti. #todo("citirati performanse svih alg")

==== _FIFO_

_FIFO_ (eng. _first in first out_) algoritam smenjuje bafer koji je najranije ušao u listu bafera. Performanse su bolje od naivnog algoritma ali _FIFO_ algoritam pati od toga da će zameniti i jako često korišćene bafere iako su najranije ušli u sistem, na primer baferi gde se čuvaju blokovi metapodataka sistema.

==== _LRU_

_LRU_ (eng. _least recently used_) algoritam smenjuje bafer koji je najdavnije korišćen. Performanse su odlične jer ako bafer dugo nije korišćen, verovatno se neće još dugo ni koristiti.

==== _Clock_

_Clock_ algoritam smenjuje prvi bafer koji ima nula pinova, ali pretragu počinje od prethodnog smenjenog bafera, formirajući krug ili sat. Performansa je okej jer je šansa da je bitan bafer pinovan velika, pa se on preskače kada se prolazi kroz krug.

==== _First unmodified_

_First unmodified_ algoritam smenjuje prvi bafer koji pronađe da nije modifikovan i da ima nula pinova ili ako je svaki modifikovan, prvi koji ima nula pinova. Performansa može biti bolja od naivnog algoritma, ali može se desiti da modifikovan bafer neće dugo biti korišćen pa je onda to bacanje bafera.

==== _LRM_

_LRM_ (eng. _least recently modified_) algoritam smenjuje bafer koji ima nula pinova i koji je poslednje izmenjen, to jest bafer sa najmanjim brojem _log_ sekvence. Predpostavka je da modifikovani bafer neće ponovo biti korišćen neko vreme jer je transakcija već završila. Performansa deluje okej, ali je algoritam dosta nepredvidiv.

== Slogovi

Podsistem za upravljanje baferima je generalan i ne pruža nikakvu strukturu podataka unutar samih bafera, to jest blokova. Na nivou relacione baze podataka, najmanja jedinica interakcije nije jedan blok, već jedan slog neke tabele i potrebno je blokove organizovati tako da se to omogući.

=== Struktuiranje

Da bi se podržalo kreiranje perzistentne strukture jednog sloga nove tabele, potrebno je definisati mehanizme kojima će klijenti opisivati tu strukturu. Na apstrakcionom nivou upravljanja datotekama, sistem se samo brine o tome da je teoretska i fizička struktura ispoštovana, a perzistiranje i samo kreiranje strukture je zadatak viših podsistema.

==== Šema <sema>

Svaki slog jedne tabele se sastoji od istih metapodataka, to jest istih kolona. Svaka kolona se opisuje svojim tipom, svojom dužinom na disku i tome da li može sadržati _NULL_ vrednosti.

#figure(
  ```java
  public record FieldInfo(
      DatabaseType type, int runtimeLength, boolean nullable
  ) { }
  ```,
  caption: [
    Opis jedne kolone
  ],
)<fig:kolona>

Svaki od tipova je definisan u SQL Java standardnoj biblioteci, ali korišćenje tih vrednosti direktno može dovesti do nekompletnosti na raznim mestima gde su tipovi korišćeni u sistemu, pa je zbog toga uvedena enumeracija koja striktno definiše podržane tipove, zajedno sa njihovom podrazumevanom dužinom u bajtovima. Tip _VARCHAR_, to jest _String_ nema podrazumevanu dužinu jer je različita za svako polje. Dodatno postoji i _NULL_ tip koji označava nemanje vrednosti za to polje.

#figure(
  ```java
  import java.sql.Types;
  public enum DatabaseType {
      INT(Types.INTEGER, 4),
      BOOLEAN(Types.BOOLEAN, 1),
      VARCHAR(Types.VARCHAR, -1),
      NULL(Types.NULL, 0);

      public final int sqlType, length;
      DatabaseType(int sqlType, int length) {
        this.sqlType = sqlType; this.length = length;
      }
  }
  ```,
  caption: [
    Podržani tipovi kolona
  ],
)<fig:tip>

Šema jedne tabele je skup podataka o kolonama te tabele zajedno sa imenima tih kolona. Bitno je napomenuti da tabele u svojoj osnovnoj definiciji predstavljaju podatke koje se nalaze u datotekama, ali to nije uvek slučaj. Postoje i virtuelne tabele koje su rezultati upita i mogu, ali ne moraju da se direktno mapiraju na tabele koje se nalaze u datotekama. Moguće je kombinovati više tabela u jednu tabelu i #link(<operator_projekcije>)[dodati virtuelne kolone] koje se ne nalaze u datoteci već su njihove vrednosti izvedene na osnovu neke kalkulacije.

==== Raspored polja <raspored_polja>

Šema predstavlja teoretski izgled jedne tabele, ali to nije dovoljno da bi se taj izgled perzistirao i mogao ponovo rekreirati. Zbog toga je potrebno uvesti mehanizam pamćenja i fizičkih karakteristika kolona tabele (postoji samo za nevirtuelne tabele). Taj mehanizam se realizuje preko rasporeda polja (eng. _layout_).

#figure(
  ```java
  public class Layout {
      private final Schema schema;
      private final Map<String, Integer> offsets;
      private final Map<String, Integer> fieldPositions;
      private final int recordSize;
  }
  ```,
  caption: [
    Fizičke karakteristike polja
  ],
)<fig:layout>

Za svaku kolonu postoji se pamte sledeće fizičke karakteristike: pozicija početka vrednosti te kolone, maksimalna dužina vrednosti te kolone i pozicija te kolone u šemi. Takođe, pamti se i celokupna dužina celog sloga. Kolone se identifikuju pomoću njihovog naziva.

== Primena strukture na blok <primena_strukture_na_blok>

Nakon definisanja fizičke strukture sloga tabele, potrebno je primeniti tu fizičku strukturu na blokove datoteka. U LBDB sistemu, jedan blok sadrži fiksni broj slogova koji su svi iz iste tabele i ne postoje vrednosti promenjive dužine. Ovo je jedna od glavnih ograničenja sistema. #todo("citirati zakljucak sa limitacijom sistema, fiksni nespanovani slogovi")

Pošto su svi slogovi iste dužine, $B/S$ slogova staje u jedan blok, gde $B$ predstavlja dužinu bloka u sistemu, $S$ predstavlja dužinu jednog sloga te tabele, a $B - S * floor(B/S)$ prostora ostaje neiskorišćeno (sve vrednosti su u bajtovima). Slogovi u blokovima čuvaju samo vrednosti kolona, ali ne i metapodatke tih kolona. Podsistem upravljanja datotekama se ne brine o metapodacima kolona, već za to postoji #link(<metapodaci>)[poseban podsistem] koji se nadograđuje na ovaj.

Ipak, u okviru jednog sloga se čuvaju metapodaci o tome koje vrednosti nisu prisutne, to jest imaju _NULL_ vrednost i to da li je slog obrisan. Rezerviše se četvorobajtno zaglavlje na početku svakog sloga i njegovi bitovi predstavljaju ove metapodatke. Da li je slog označen kao obrisan se predstavlja prvim bitom (O), dok ostalih 31 bitova (N#sub[i]) označavaju da li polje na toj poziciji ima _NULL_ vrednost. Zbog ovoga postoji ograničenje na broj polja po tabeli, maksimalno 31 polje.

#figure(
  image("../dijagrami/stanica_slogova_izgled.pdf"),
  caption: [
    Izgled jednog bloka struktuiranog sa slogovima
  ],
)<fig:izgled_bloka_sa_podacima>

=== Stranica slogova

`RecordPage` klasa enkapsulira svu logiku održavanja strukture individualnog bloka tako što pruža interfejs za postavljanje vrednosti samo na osnovu imena kolone i broja sloga u tom bloku. Takođe, pruža interfejs za postavljanje _NULL_ vrednosti i pretragu slobodnih ili zauzetih slogova u bloku za koji je povezana. Za pristup svim blokovima jedne tabele, potrebno je sukcesivno konstruisati objekte `RecordPage` klase, što je posao podsistema #link(<table_sken>)[relacionih operatora].

Bitno je napomenuti da logika stranice slogova za postavljanje vrednosti ne radi sâmo postavljanje vrednosti, već samo računa gde ta vrednost treba biti postavljena. Postavljanje vrednosti #link(<pristup_vrednostima_u_transakcijama>)[delegira] sistemu transakcija.

Kao što za blokove postoji #link(<fig:blok_id>)[unikatni identifikator], tako unikatni identifikator postoji i za slogove i sadrži u kom je bloku slog i na kojoj je poziciji unutar bloka:

#figure(
  ```java
  public record RecordId(int blockNum, int record) { }
  ```,
  caption: [
    Identifikator sloga
  ],
)<fig:slog_id>

Identifikatori slogova se koriste za direktan skok na neki slog.
