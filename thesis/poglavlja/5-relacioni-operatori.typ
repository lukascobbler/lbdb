#import "../funkcije.typ": todo

= Relacioni operatori <relacioni-operatori>

_SQL_ programski jezik je jezik deklarativnog tipa. To znači da se preko njega specificira šta treba da se uradi sa podacima (dobavljanje, filtriranje, modifikacija, ...), ali za razliku od proceduralnih programskih jezika, ne specificira se i kako. Most između deklarativne prirode _SQL_ jezika i potrebe definisanja načina pristupa podacima je rešen implementacijom _relacione algebre_ @relaciona_alg. _LBDB_ sistem prevodi kod _SQL_ programskog jezika u stablo operatora relacione algebre.

Relacija $R$ je skup torki oblika ($d_1, d_2, ..., d_j$) gde za svaku komponentu $d_k$ torke $d_j$ važi $d_k in D_k$, gde je $D_k$ domen koji definiše skup svih dozvoljenih vrednosti za tu komponentu. U relacionim bazama podataka, tabela se modeluje kao relacija, dok operatori relacione algebre preslikavaju jednu ili više relacija u novu relaciju kao rezultat primenjene transformacije. Torke se mapiraju na slogove tabela.

== Virtuelna mašina

Virtuelna mašina _LBDB_ sistema predstavlja okruženje izvršavanja logičkih i aritmetičkih operacija i sastoji se od klasa koje modeluju komponente tih operacija.

=== Konstante

Sve vrednosti sa kojima relacioni operatori barataju predstavljene su `Constant` klasom. Ona omogućava sistemu da definiše generičko ponašanje za sve različite tipove podržane u sistemu. Relacioni operatori uvek vraćaju `Constant` objekte na svom izlazu. Implementira `Comparable<Constant>` interfejs zarad lakog poređenja.

#figure(
  image("../dijagrami/konstante.pdf", height: 28%),
  caption: [
    Hijerarhija konstanti
  ],
)<fig:hijerarhija_konstanti>

Interfejs konstante je definisan _sealed interface_ _Java_ konstruktom, zbog njegove odlične kompatibilnosti sa `swtich` sintaksom. Konkretne konstante su predstavljene _Java_ _record_ strukturom. `NullConstant` nema nikakve podatke instance, jer su sve `NULL` vrednosti identične u sistemu, pa se svugde koristi ista instanca.

=== Izrazi <izrazi>

Sve aritmetičke operacije koje sistem evaluira su predstavljene `Expression` klasom. Evaluacija izraza uvek proizvodi `Constant` objekte. Izrazi se sastoje od proizvoljne kombinacije aritmetičkih operatora (`+`, `-`, `*`, `/`, `^`), zagrada, konstanti i identifikatora kolona tabele. Interfejs izraza je definisan _sealed interface_ _Java_ konstruktom, zbog njegove odlične kompatibilnosti sa `swtich` sintaksom. Konkretni izrazi su predstavljeni _Java_ _record_ strukturom.

#figure(
  image("../dijagrami/izrazi.pdf", height: 52%),
  caption: [
    Hijerarhija izraza
  ],
)<fig:hijerarhija_izraza>

Evaluacija izraza predstavlja proces računanja konstante koja predstavlja vrednost tog izraza za neki slog tabele.

`BinaryArithmeticExpression` je izraz koji u sebi sadrži izraze i omogućava kreiranje stabla izraza, gde se prvo evaluiraju njegovi članovi, pa onda on. `UnaryArithmeticExpression` ima istu funkciju, ali za prefiksne operatore (`+`, `-`) i ima samo jedan član.

`WildcardExpression` suštinski ne predstavlja izraz, ali zbog načina #link(<parsiranje_izraza>)[parsiranja izraza], tretira se kao jedan. Služi kao zamenski član (eng. _placeholder_) svih kolona upitanih tabela. Evaluacija je zabranjena operacija i baca izuzetak.

`FieldNameExpression` je izraz koji identifikuje virtuelnu kolonu neke tabele upita, sa opcionim preimenovanjem tabele i evaluira se na vrednost te kolone. `ConstantExpression` se evaluira direktno na konstantu za koju je vezan i nezavisan je od tabela.

=== Članovi

Član (eng. _term_) enkapsulira logiku za poređenje konstanti dva evaluirana izraza. Poređenje dve konstante zavisi od operatora poređenja koji mogu biti `=`, `!=`, `>`, `>=`, `<`, `<=`, `IS`, `IS NOT`. Razlika između `=` i `IS` (isto tako i između `!=` i `IS NOT`) je u tome kako se ponašaju sa _NULL_ vrednostima. `IS` omogućava poređenje sa _NULL_ vrednostima, dok `=` to ne podržava. Evaluacija člana nad nekim relacionim operatorom, za razliku od evaluacije izraza, vraća samo da li član važi ili ne.

=== Predikati <predikati>

Predikati ulančavaju članove logičkim operatorima. Evaluacija predikata nad nekim relacionim operatorom funkcioniše isto kao i evaluacija jednog člana, ali između tih članova stoje različite logičke operacije. _LBDB_ sistem podržava samo `AND` logičke operatore između članova i ovo je jedna od glavnih #link(<samo-and>)[ograničenja sistema].

=== Generalizacija evaluacije <evaluatable-interfejs>

Izrazi i predikati implementiraju `Evaluatable` interfejs koji definiše sve operacije potrebne za njihovu evaluaciju i kasniju #link(<planer>)[proveru validnosti]. Ovaj interfejs omogućava sistemu da se ne brine o tome šta se evaluira, već samo o konstanti koju će dobiti, što dalje omogućava da se vrednosti #link(<operator_projekcije>)[projektovanih kolona] dobijaju i preko evaluacije izraza i preko evaluacije predikata.

#figure(
  ```java
  public interface Evaluatable {
      Constant evaluate(Scan scan);
      boolean isConstant();
      DatabaseType type(Schema schema);
      int length(Schema schema);
      boolean isNullable(Schema schema);
      Set<String> getFields();
      boolean hasWildCard();
      Evaluatable qualify(Map<String, String> aliases);
  }
  ```,
  caption: [
    `Evaluatable` interfejs
  ],
)<fig:evaluatable>

== Struktura relacionih operatora u sistemu

Za izvršavanje naredbi definisanih _SQL_ jezikom, često je potrebno primeniti više relacionih operatora. Primena više relacionih operatora se radi njihovim ulančavanjem u stablovsku strukturu podataka i zbog ovoga se kaže da sistem izvršava "stablo" relacionih operatora.

=== Pajplajnovano procesovanje

Relacioni operatori podržani u sistemu imaju dve zajedničke osobine @simpledb:
- generišu slogove jedan po jedan
- ne čuvaju generisane slogove i ne čuvaju nikakve međurezultate

Zahtev za izvršenje neke operacije nad stablom operatora počinje od korena stabla, koji formira rezultat uz pomoć čvorova ispod njega, ali nekad i direktno. Ovim načinom, zahtev prolazi kroz celo stablo operatora. Vraća se greška klijentu ukoliko ni jedan čvor nije uspeo da formira rezultat zbog greške prilikom izvršavanja.

Kombinacija dve navedene osobine uz delegaciju operacija se zove pajplajnovano procesovanje (eng. _pipelined processing_). Korišćenje pajplajnovanog procesovanja u mnogim scenarijima ne dodaje nikakvno dodatno _U/I_ opterećenje, pa ga je pogodno koristiti.

Prednost pajplajnovanog procesovanja što ne čuva međurezultate je upravo i njegova mana za određene operacije. Materijalizovano procesovanje (eng. _materialization_) omogućava i čuvanje međurezultata pa može rešiti ovu manu, ali dolazi sa svojim problemima. Takođe, neke operacije poput grupisane agregacije, vraćanje samo jedinstvenih slogova i proizvoljno sortiranje nije moguće obaviti bez materijalizovanog procesovanja. Potrebno je koristiti oba načina procesovanja u sistemu za najbolje rezultate, ali _LBDB_ sistem implementira samo pajplajnovano procesovanje.

=== Hijerarhija implementacije relacionih operatora <hijerarhija_rel_op>

Najopštija podela relacionih operatora je na one koji samo čitaju podatke (eng. _read-only_) i na one koji mogu da modifikuju podatke. Ova distinkcija je napravljena da se obezbedi sigurnost od pogrešne primene operatora u vremenu kompajliranja koda (eng. _compile time_). Hijerarhija podrazumevanih implementacija se sastoji od raznih kosturskih implementacija koje se koriste za lako definisanje novog relacionog operatora. Ostale klase van hijerarhije podrazumevanih implementacija predstavljaju različite relacione operatore podržane u sistemu i objašnjene su ispod.

#figure(
  image("../dijagrami/hijerarhija_relacionih_operatora.pdf"),
  caption: [
    Hijerarhija implementacije relacionih operatora
  ],
)<fig:hijerarhija_skenova>

==== `Scan`

`Scan` apstraktna klasa definiše operacije neophone za prolazak kroz sve vrednosti rezultujuće virtuelne tabele. Svaki relacioni operator implementira bar ove operacije. Vraćanje vrednosti se radi isključivo kroz `Constant` objekte. `Scan` se može, ali ne mora, mapirati na fizičku tabelu. Implementira `AutoCloseable` interfejs koji omogućava _RAII_ (eng. _Resource Acquisition Is Initialization_) šablon, ali ne pruža sâmu logiku oslobađanja resursa.

==== `UpdateScan`

Rezultujuće virtuelne tabele relacionih operatora koji dozvoljavaju modifikaciju vrednosti se moraju mapirati na fizičke tabele, jer nema smisla menjati virtuelne vrednosti. To znači da za svaki virtuelni slog $r$ u stablu  modifikujućih operatora, mora da postoji $r'$ koji ima identičnu strukturu, poziciju i vrednosti u datoteci tabele. `UpdateScan` klasa definiše operacije promene vrednosti kolona nekog sloga, umetanja novog sloga i brisanja sloga.

==== `UnaryScan`

`UnaryScan` sadrži podrazumevane implementacije proizvoljnog relacionog operatora koji transformiše *jednu* relaciju, to jest jednu tabelu. Svaki poziv podrazumevane implementacije je samo prosleđen operatoru ispod. Ovakva struktura omogućava da operatori koji nasleđuju ovu klasu redefinišu samo metode koje su potrebne za funkcionisanje tog operatora i izbegava se dupliranje istog koda.

==== `UnaryUpdateScan`

`UnaryUpdateScan` sadrži podrazumevane implementacije proizvoljnog relacionog operatora koji može da modifikuje vrednosti fizičke tabele. Funkcioniše isto kao i `UnaryScan` i ima i sve podrazumevane implementacije iz njega.

==== `BinaryScan`

`BinaryScan` sadrži podrazumevane implementacije proizvoljnog relacionog operatora koji transformiše *dve* relacije, to jest dve tabele. Pošto operatori koji rade nad dve tabele nemaju toliko međusobnih preklapanja, `BinaryScan` implementira samo `close()` metodu koja oslobađa resurse oba deteta.

==== `DiffSchemaJoinContextScan`

`DiffSchemaJoinContextScan` ima sličnu ulogu kao `BinaryScan`, ali samo za operatore koji vrše multiplikativno objedinjavanje dve tabele i enkapsulira korektan pristup kolonama iz dve šeme koje nemaju preklapanje.

==== `TableScan` <table_sken>

`TableScan` nije pravi relacioni operator zato što njegova implementacija ne radi transformacije tabele, već pruža logiku za dobavljanje i modifikaciju fizičkih vrednosti. Služi kao omotač oko objekata stranice slogova i zadužena za konstruisanje novih povezanih objekata stranice slogova. Pošto pruža inicijalne vrednosti koje će dalje biti transformisane, uvek se nalazi na dnu stabla relacionih operatora.

==== `DummyTableScan` <dummy_table_sken>

`DummyTableScan` nije pravi relacioni operator zato što njegova implementacija ne radi transformacije tabele, već pruža logiku za dobavljanje i navigaciju jedinog sloga virtuelne tabele koja se konstruiše u _SQL_ upitima koji ne rade ni sa jednom fizičkom tabelom.

==== `SelectScan` <operator_selekcije>

`SelectScan` implementira operator generalizovane selekcije $sigma_phi (R)$ iz relacione algebre, gde je $sigma$ ime selekcionog operatora, $phi$ je formula filtera, a $R$ je relacija nad kojom se operator primenjuje. Redefiniše samo metode za prolazak kroz slogove, jer je to jedino neophodno da se ukinu slogovi koji ne prolaze filter. Sadrži `Predicate` objekat pomoću kog filtrira. Može da se koristi i u kontekstima modifikujućih stabala operatora i u kontekstima _read-only_ stabala operatora i zbog toga postoji i `SelectReadOnlyScan` varijanta koja ima istu funkciju.

==== `ExtendProjectScan` <operator_projekcije>

`ExtendProjectScan` implementira operator projekcije $Pi_(a_1, ..., a_n) (R)$ iz relacione algebre, gde je $Pi$ ime projekcionog operatora, a $a_n$ predstavlja izraz čija evaluacija proizvodi vrednost komponente $n$. Operator projekcije koji klasa implementira nije striktno operator projekcije formalno definisan u relacionoj algebri, zato što dozvoljava kreiranje novih kolona sa izrazima koji će biti izražunati u trenutku izvršavanja stabla, a ne povučene direktno iz fizičke tabele. Dozvoljava i dodeljivanje proizvoljnog imena izrazima kolona, ali se to ne treba pomešati sa operatorom preimenovanja. Redefiniše metode dobavljanja vrednosti i provere postojanja kolone nekog imena.

==== `RenameScan`

`RenameScan` implementira operator preimenovanja $rho_(a_n slash b_n) (R)$ iz relacione algebre, gde je $rho$ ime operatora preimenovanja, a $b_n$ predstavlja novo ime komponente $a_n$. Razlikuje se od formalne definicije operatora preimenovanja iz relacione algebre jer dozvoljava $n$ preimenovanja od jednom da bi se izbeglo ulančavanje istih operatora. Bitno je napomenuti da operator preimenovanja omogućava pristup koloni sa imenom $a$ kroz ime $b$, za razliku od operatora projekcije koji samo dodeljuje ime nekom izrazu. Redefiniše metode dobavljanja vrednosti i provere postojanja kolone nekog imena.

==== `ProductScan`

`ProductScan` implementira operaciju dekartovog proizvoda relacija $R$ i $S$: $R times S$. Rezultujuća relacija predstavlja virtuelnu tabelu koja ima $|R| * |S|$ torki, a svaka torka se sastoji od svih komponenti obe relacije. Operacija dekartovog proizvoda je multiplikativna, a `ProductScan` zahteva da tabele nemaju kolone sa istim imenom, pa se koristi `DiffSchemaJoinContextScan`. Iteracija kroz rezultujuću tabelu nakon `ProductScan` operatora se vrši tako što se za svaki slog prve tabele, prolazi kroz sve slogove druge tabele. Redefiniše metode dobavljanja vrednosti, provere postojanja kolone nekog imena i sve navigacione metode.

==== `UnionAllScan`

`UnionAllScan` implementira operaciju kreiranja relacije koje sadrži sve torke relacija $R$ i $S$. Ne briše duplikate. Zahteva da su komponente torki obe relacije istog tipa i da obe relacije imaju isti broj komponenata po torki. Po _SQL_ standardu, kolonama druge tabele se pristupa po imenima prve. Operacija unije je aditivna. Iteracija kroz rezultujuću tabelu nakon `UnionAllScan` operatora se vrši tako što se prvo prolazi kroz sve slogove prve tabele, pa se prolazi kroz sve slogove druge tabele. Redefiniše metode dobavljanja vrednosti, provere postojanja kolone nekog imena i sve navigacione metode.

=== Primeri stabla relacionih operatora

Sledeći primeri pokazuju kako pozivi metoda putuju kroz stablo relacionih operatora. Primeri ne oslikavaju stabla relacionih operatora koje bi _LBDB_ sistem napravio, već služe da pokažu pajplajnovano procesovanje i kako se `Scan` objekti oslanjaju jedni na druge.

==== Primer poziva `getValue()` metode

Prate se koraci poziva `getValue()` metode, za virtuelnu kolonu `"StudentId"`. Union operator, koji je skroz na vrhu, spaja dve tabele: tabelu koja modeluje zastareli način vođenja evidencije i virtuelnu tabelu koja je rezultat podstabla operatora koji projektuju iste kolone kao i u tabeli zastarele evidencije. `ExtendProjectScan` je preimenovao `"SSId"` u `"StudentId"`. Uz `TableScan` i `ProductScan` operatore stoji i uprošćena šema.

#figure(
  image("../dijagrami/primer_stabla_relacionih_operatora.pdf", height: 54%),
  caption: [
    Primer poziva `getValue()` metode u konkretnom stablu relacionih operatora
  ],
)<fig:getValue_primer>

==== Primer poziva `next()` metode

Ovaj primer koristi isto relaciono stablo kao i prethodni primer. Pomoću dijagrama sekvence se opisuje procedura iteracije kroz stablo sa svim vrstama operatora koji menjaju podrazumevanu iteraciju.

#figure(
  image("../dijagrami/sekvenca_iteracije_stabla_rel_op.svg"),
  caption: [
    Dijagram sekvence `next()` metode u konkretnom stablu relacionih operatora
  ],
)<fig:getValue_primer>
