#import "../funkcije.typ": todo

= Planiranje <planiranje>

Planer i planovi čine podsistem planiranja koji je jedan od tri glavna podsistema _LBDB_ sistema #link(<sistem_za_obradu_upita>)[obrade upita]. U okviru životnog ciklusa obrade jedne _SQL_ naredbe, podsistem planiranja se nalazi između podsistema za parsiranje i stabla relacionih operatora.

== Struktura planova u sistemu

Za svaki relacioni operator se definiše njegov plan, a za svako stablo relacionih operatora se definiše stablo planova. Plan relacionog operatora opisuje šemu nakon primene operatora i omogućava računanje #link(<statisticki-metapodaci>)[statističkih metapodataka] za rezultujuću virtuelnu tabelu. Ovi statistički metapodaci su jedan deo podataka koje algoritmi za konstrukciju stabla planova koriste za efikasan rad.

Statističke metapodatke koje plan može da izračuna su isti kao i statistički metapodaci koji se prate za fizičke tabele, a oni su:
- broj blokova potrebnih za prolazak kroz sve slogove,
- broj slogova,
- broj jedinstvenih vrednosti za svaku kolonu,
- broj _NULL_ vrednosti za svaku kolonu.
Izračunati statistički podaci #link(<table-plan>)[nisu 100% precizni], ali bez obzira na to, pomažu algoritmima konstrukcije planova (planerima).

=== Hijerarhija implementacije planova

Najopštija podela planova je na one koji samo čitaju podatke (eng. _read-only_) i na one koji mogu da modifikuju podatke, po #link(<hijerarhija_rel_op>)[hijerarhiji relacionih operatora]. Za razliku od hijerarhije relacionih operatora, ne postoji hijerarhija podrazumevanih implementacija jer klase planova nemaju toliko zajedničkih osobina. Podela na _read-only_ i modifikacione planove je odrađena preko _generics_ _Java_ konstrukta, umesto deljenja glavnog interfejsa na dva podtipa.

#figure(
  image("../dijagrami/hijerarhija_planova.pdf"),
  caption: [
    Hijerarhija implementacije planova
  ],
)<fig:hijerarhija_planova>

==== `Plan`

`Plan` interfejs definiše operacije neophodne za računanje svih statističkih podataka, dobijanje šeme rezultujuće tabele, pretvaranje stabla planova u stablo relacionih operatora i dobijanje slogova za #link(<explain>)[automatski opis plana].

==== `TablePlan` <table-plan>

`TablePlan` opisuje konkretnu fizičku tabelu, umesto da vrši transformacije virtuelne tabele. Izlazna šema je jednaka šemi fizičke tabele. Izvlači statističke podatke direktno iz menadžera metapodataka za tabelu za koju je vezan. Statistički podaci #link(<racunanje-statistike>)[nisu ažurni], ali pružaju dovoljno dobru statistiku za potrebe _LBDB_ sistema. Izračunati statistički metapodaci svih planova u stablu planova eventualno zavise od vrednosti statističkih metapodataka ovog plana. Može da se koristi i u kontekstima modifikujućih stabala operatora i u kontekstima _read-only_ stabala operatora i zbog toga postoji i `TableReadOnlyPlan` varijanta koja ima istu funkciju.

==== `DummyTablePlan` <dummy_table_plan>

`DummyTablePlan` opisuje virtuelnu tabelu koja se sastoji od jednog sloga u upitima koji ne rade sa fizičkim tabelama. Izlazna šema se određuje na osnovu konstantnih vrednosti u upitu, a statistički podaci su precizni jer se lako računaju pošto je broj konkretnih vrednosti jako mali.

==== `SelectPlan`

`SelectPlan` opisuje virtuelnu tabelu nakon primene uslova filtriranja. Izlazna šema je jednaka šemi podređenog plana. Broj blokova ostaje nepromenjen jer da bi znali koji sve slogovi ispunjavaju uslov filtera, potrebno je proći kroz sve slogove, pa sa time i kroz sve blokove podređenog plana.

Redukcioni faktor predstavlja za koliko puta će broj slogova na izlazu biti smanjen. Računa se na osnovu uslova filtriranja, koji je predstavljen #link(<predikati>)[predikatom]. Svaki član predikata predstavlja jedan deo filtera i ima svoj redukcioni faktor. Pošto sistem podržava samo ulančavanje članova `AND` logičkim operatorom, redukcioni faktor predikata se računa kao proizvod svih redukcionih faktora članova od kog se predikat sastoji.

Algoritam računanja redukcionog faktora jednog člana je predstavljen na #link(<fig:racunanje_redukcionog_faktora>)[dijagramu]. Pošto se broj slogova nakon filtriranja dobija deljenjem broja slogova podređenog plana i redukcionog faktora, specijalni slučajevi se mogu predstaviti različitim konstantama.

Specijalni slučaj nejednakosti je predstavljen konstantom _NEJEDNAKOSTI_ koja ima vrednost $3.0$ i označava procenjenu vrednost redukcije u slučaju korišćenja operacija nejednakosti.

Specijalni slučaj kada postoje prekompleksni izrazi je predstavljen konstantom _KOMPLEKSNO_ koja ima vrednost $10.0$ i označava procenjenu vrednost redukcije u slučaju postojanja izraza koji ima ili više od dve kolone ili izraza koji kombinuje kolone sa operacijama poređenja na netrivijalan način.

Slučaj kada se ni jedan slog ne podudara sa članom je predstavljen konstantom maksimalne vrednosti `Double` tipa i označava maksimalnu redukciju. Slučaj kada svi slogovi podudaraju neki član je predstavljen konstantom $1.0$ i predstavlja odsustvo redukcije.

Izbor vrednosti ovih konstanti je opisan u _SystemR_ istraživačkom papiru o putanjama pristupa @systemR.

#figure(
  image("../dijagrami/racunanje_redukcionog_faktora.pdf", height: 76%),
  caption: [
    _Flowchart_ računice redukcionog faktora člana
  ],
)<fig:racunanje_redukcionog_faktora>

Procena broja jedinstvenih vrednosti za izlaznu kolonu vrši se analizom predikata i pronalaženjem uslova jednakosti i on je jednak:
- $0$, ukoliko predikat izjednačava traženu kolonu sa dve ili više različitih konstanti. U ovom slučaju, uslov je kontradiktoran i ni jedan slog neće zadovoljiti filter, pa samim tim neće biti ni jedinstvenih vrednosti,
- $1$, ukoliko predikat izjednačava traženu kolonu sa tačno jednom konstantom. Svi slogovi koji prođu filter imaće istu vrednost za tu kolonu,
- minimumu između broja jedinstvenih vrednosti te kolone i svih kolona sa kojima je izjednačena, ukoliko kolona nije izjednačena ni sa jednom konstantom, ali jeste sa jednom ili više drugih kolona. U ovom slučaju, broj jedinstvenih vrednosti tražene kolone ne može biti veći od njenog originalnog broja jedinstvenih vrednosti iz podređenog plana, ali ne može biti veći ni od broja jedinstvenih vrednosti najrestriktivnije kolone sa kojom je izjednačena.

Procena broja _NULL_ vrednosti za svaku izlaznu kolonu vrši se analizom predikata i njegovog odnosa prema _NULL_ konstantama i on je jednak:
- ukupnom broju izlaznih slogova ovog plana, ukoliko predikat eksplicitno izjednačava traženu kolonu sa _NULL_ vrednošću, a izlazna šema dozvoljava _NULL_ vrednosti za tu kolonu. U ovom slučaju, svi slogovi koji prođu filter imaće _NULL_ vrednost,
- $0$, ukoliko predikat izjednačava traženu kolonu sa NULL vrednošću, ali izlazna šema ne dozvoljava _NULL_ vrednosti (nije _nullable_). U ovom slučaju, uslov je nemoguće ispuniti,
- $0$, ukoliko predikat sadrži uslov koji eksplicitno isključuje _NULL_ vrednosti za traženu kolonu. Svi slogovi sa _NULL_ vrednostima će pasti na ovakvom filteru,
- proporciji broju _NULL_ vrednosti iz podređenog plana, ukoliko predikat ne spominje NULL konstantu u vezi sa traženom kolonom. U ovom slučaju, pretpostavlja se da uslov filtera ravnomerno smanjuje ukupan broj slogova i broj _NULL_ vrednosti, pa se broj _NULL_ vrednosti iz podređenog plana deli sa faktorom redukcije celokupnog predikata.

Može da se koristi i u kontekstima modifikujućih stabala operatora i u kontekstima _read-only_ stabala operatora i zbog toga postoji i `SelectReadOnlyPlan` varijanta koja ima istu funkciju.

==== `ExtendProjectPlan`

`ExtendProjectPlan` opisuje virtuelnu tabelu sa svim projektovanim kolonama. Izlazna šema ima sve dodate kolone, a iz nje su izbrisane neprojektovane kolone. S obzirom da operacija projekcije ne dodaje nove slogove, broj blokova i broj slogova ostaju nepromenjeni i direktno se preuzimaju od podređenog plana.

Procena broja jedinstvenih vrednosti za svaku izlaznu kolonu vrši se na osnovu složenosti izraza ili predikata koji tu kolonu definiše i on je jednak:
- $0$, ukoliko se traži procena za kolonu koja se ne nalazi u projekciji,
- $1$, ukoliko je izraz ili predikat konstanta (ne referencira ni jednu kolonu),
- $2$, ukoliko je u pitanju predikat, koji će najverovatnije imati obe moguće vrednosti,
- broju jedinstvenih vrednosti te kolone iz podređenog plana, ukoliko izraz referencira tačno jednu kolonu. Pretpostavka je da većina transformacija nad jednom kolonom (npr. aritmetičke operacije) zadržava sličnu distribuciju vrednosti,
- ukupnom broju slogova, ukoliko izraz referencira više od jedne kolone. U ovom slučaju, pretpostavlja se da kombinacija više polja rezultuje jedinstvenom vrednošću za svaki slog.

Procena broja _NULL_ vrednosti za svaku izlaznu kolonu vrši se na osnovu složenosti izraza ili predikata koji tu kolonu definiše i on je jednak:
- $0$, ukoliko se traži procena za kolonu koja se ne nalazi u projekciji,
- $0$, ukoliko je u pitanju predikat, jer se predikat može evaluirati samo na tačno i netačno,
- $0$, ukoliko šema garantuje da izraz ne može imati _NULL_ vrednosti (nije _nullable_),
- $0$ ukoliko je izraz jednak bilo kojoj konstanti sem _NULL_ konstante,
- $1$ ukoliko je izraz jednak _NULL_ konstanti,
- broju jedinstvenih vrednosti te kolone iz podređenog plana, ukoliko izraz referencira tačno jednu kolonu. Pretpostavka je da većina transformacija nad jednom kolonom zadržava sličnu distribuciju _NULL_ vrednosti,
- maksimalanom broju _NULL_ vrednosti među svim referenciranim kolonama iz podređenog plana, ukoliko izraz referencira više od jedne kolone. Pretpostavlja se da će izraz biti _NULL_ ukoliko je barem jedan od operanada _NULL_, pa je maksimalan broj _NULL_ vrednosti pesimistična procena.

==== `RenamePlan`

`RenamePlan` opisuje virtuelnu tabelu sa svim primenjenim preimenovanjima kolona. Izlazna šema sadrži sve kolone sa novim imenom i ni jednu kolonu sa starim imenom.
S obzirom da operacija preimenovanja ne dodaje nove slogove, broj blokova i broj slogova ostaju nepromenjeni i direktno se preuzimaju od podređenog plana.
Procena broja jedinstvenih vrednosti kolone je jednaka podređenom planu ukoliko se traži novo ime stare kolone ili ukoliko je kolona nepreimenovana, a jednaka je $0$ ako se traži staro ime preimenovane kolone. Procena broja _NULL_ vrednosti funkcioniše isto.

==== `ProductPlan`

`ProductPlan` opisuje virtuelnu tabelu koja je proizvod dve tabele. Izlazna šema sadrži sve kolone od obe tabele. Za demonstaciju računice broja blokova kod proizvoda dve tabele, definišemo $T_1$ i $T_2$:

#figure(
  {
    set par(justify: false)
    table(
      columns: (0.2fr, 0.4fr, 0.4fr, 0.4fr),
      align: (center, center),
      inset: 6pt,
      [$T_i$], [$text("B")(T_i)$], [$text("R")(T_i)$], [$text("RPB")(T_i)$],
      //
      [$T_1$], [$5$], [$1000$], [$frac(1000, 5) = 200$],
      //
      [$T_2$], [$100$], [$500$], [$frac(500, 100) = 5$],
    )
  },
  caption: [Primer karakteristika tabela za računanje broja blokova plana proizvoda],
)<tbl:product_rpb>

- $text("B")(T_i)$ predstavlja broj blokova neke tabele,
- $text("R")(T_i)$ predstavlja broj slogova neke tabele,
- $text("RPB")(T_i)$ predstavlja koliko slogova može da stane po jednom bloku za neku tabelu.

Ovaj primer se odnosi na rad sa konkretnim fizičkim tabelama, ali u generalnom slučaju, tabele nisu fizičke, već su predstavljene planovima sa kojima plan proizvoda barata.

Da bi se prošlo kroz svaki slog rezultujuće tabele, potrebno je da za se svaki slog leve tabele prođe kroz svaki slog desne tabele. Formula koja opisuje broj blokova potreban da se ovo izvrši je sledeća @simpledb:

$text("B")(T_r) = text("B")(T_l) + (text("R")(T_l) * text("B")(T_d))$

Ako stavimo konkretne vrednosti tabela $T_1$ i $T_2$ u ovu formulu, dobijamo različite rezultate u odnosu na to koja tabela je leva, a koja desna:

- ($T_l = T_1$, $T_d = T_2$) $=>$ $text("B")(T_r) = 5 + (1000 * 100) = 100005$
- ($T_l = T_2$, $T_d = T_1$) $=>$ $text("B")(T_r) = 100 + (500 * 5) = 2600$

Vidi se da ako stavimo da tabela $T_1$ bude desna, a $T_2$ leva, dobijamo manji  broj blokova rezultujuće tabele, a sa time i efikasniju operaciju proizvoda. Ekvivalentna formula @simpledb:

$text("B")(T_r) = text("B")(T_l) + (text("RPB")(T_l) * text("B")(T_l) * text("B")(T_d))$

daje bolji uvid zbog čega računica broja blokova rezultujuće tabele nije simetrična u odnosu na dve tabele koje učestvuju u proizvodu. Sabirak $text("RPB")(T_l) * text("B")(T_l) * text("B")(T_d)$ znatno više utiče na finalni rezultat u odnosu na $text("B")(T_l)$.

Što je slog manji, jedan blok može da ih sadrži više. U suprotnom, što je slog veći, jedan blok može da ih sadrži manje. U tabelama gde je slog veći, potrebno je pristupiti više blokova da bi se prošlo kroz isti broj slogova kao u tabelama gde je slog manji. U operacijama proizvoda bolje je staviti tabelu gde je slog veći (to jest gde je $text("RPB")$ manji) na levu stranu, a tabelu gde je slog manji (to jest gde je $text("RPB")$ veći) na desnu stranu jer se slogovima desne tabele pristupa znatno više nego slogovima leve tabele.

Broj slogova je proizvod broja slogova oba podređena plana, a broj jedinstvenih i broj _NULL_ vrednosti se prosleđuje podređenom planu u kom se nalazi tražena kolona.

==== `UnionAllPlan`

`UnionAllPlan` opisuje virtuelnu tabelu koja je zbir dve tabele. Izlazna šema je jednaka izlaznoj šemi levog podređenog plana. Broj blokova je zbir broja blokova oba podređena plana, a broj slogova je zbir broja slogova oba podređena plana. Procena broja jedinstvenih vrednosti kolone je jednaka zbiru procena jedinstvenih vrednosti oba podređena plana za tu kolonu. Procena _NULL_ vrednosti kolone je jednaka zbiru procena _NULL_ vrednosti oba podređena plana.

== Planer <planer>

Većina naredbi definisanih _SQL_ standardom zahteva propratno stablo relacionih operatora. Konstrukcija i analiza stabala je posao planera, ali pored toga planer vrši i proveru semantičke validnosti svih naredbi.

Glavna podela tehnika planiranja u relacionim bazama podataka je na tehnike praćenja striktnih pravila pravljenja planova (eng. _rule-based optimisation_, _RBO_; _heuristics-based optimisation_, _HBO_) i tehnike planiranja koji rade sa cenama (eng. _cost-based optimisation_, _CBO_). Cena predstavlja kombinaciju statističkih metapodataka relacionih operatora sa hardverskim osobinama koji ti relacioni operatori koriste.

Raniji sistemi upravljanja bazama podataka poput _INGRES_ sistema su koristili _RBO_ tehnike planiranja @ingres_rbo, dok moderni sistemi koriste _CBO_ tehnike planiranja #footnote[https://www.postgresql.org/docs/current/planner-optimizer.html]#super(",") #footnote[https://www.postgresql.org/docs/current/planner-stats-details.html] koje su postale popularne nakon _SystemR_ istraživačkog papira o putanjama pristupa @systemR.

Evolucija tehnika planiranja, koja se može videti kroz ovu glavnu podelu, postoji jer je kroz istoriju bilo potrebno obezbediti sve efikasnije planere koji rade sa sve većim skupovima podataka.

=== Evaluacija izraza tokom planiranja

Evaluacija izraza i predikata u stablu relacionih operatora je najskuplje mesto evaluacije, jer se operacije izvršavaju u okviru virtuelne mašine sistema, gde se ne koriste procesorske instrukcije direktno. `PartialEvaluator` pruža obradu operacija u trenutku planiranja, što znatno povećava performansu upita jer se trivijalne operacije ne izvršavaju za svaki slog.

Trivijalne operacije koje se redukuju su: aritmetičke operacije koje ne transformišu podatke, aritmetičke operacije između dve konstante, operacije poređenja koje su uvek tačne i u slučaju da postoji kontradiktorna operacija poređenja ona skraćuje (eng. _short-circuit_) ceo predikat, čineći ga uvek netačnim bez obzira na njegove ostale komponente.

=== Ulazna tačka kreiranja i izvršavanja planova <planner-klasa>

Svaka _SQL_ naredba, koja je prvobitno niz karaktera, se prosleđuje `Planner` klasi, koja je dalje obrađuje. Klasa `Planner` definiše dve grupe funkcija koje su prilagođene različitim _API_ (_Application Programming Interface_) interfejsima. Obe grupe funkcija znaju da barataju sa podsistemom parsiranja, koji pretvara niz karaktera u #link(<statement>)[`Statement` objekat]. Grupe se sastoje od funkcija:
- `createQueryPlan` i `executeUpdate` koje su prilagođene _JDBC_ (_Java Database Connectivity_) _API_ interfejsu. _JDBC_ definiše generičko ponašanje za interakciju sa sistemima za upravljanje bazama podataka (ne postoji konkretna implementacija za _LBDB_, ali definisanjem ovih metoda ju je lako dodati). `createQueryPlan` kreira plan za _read-only_ naredbu, ali ga ne izvršava, dok se `executeUpdate` oslanja na to da su modifikacione naredbe dizajnirane da se odmah izvrše i vraća broj promenjenih slogova,
- `execute` koja je prilagođena #link(<klijent-server>)[klijentsko serverskoj arhitekturi] _LBDB_ sistema, u okviru koje se brine o automatskom ili manuelnom potvrđivanju transakcija, kreiranju i izvršavanju plana. Vraća neki #link(<response>)[`Response`] objekat, koji enkapsulira sve moguće vrste odgovora na neku naredbu.

#figure(
  image("../dijagrami/struktura_planera.pdf"),
  caption: [
    Struktura planera
  ],
)<fig:struktura_planera>

=== Planiranje _read-only_ naredbi

Kao što je već spominjano u tekstu, _SQL_ naredbe se dele na _read-only_ i modifikacione. Glavni primer _read-only_ naredbe je `SELECT` naredba, koja služi za struktuirano upitivanje (eng. _query_) baze podataka.

Svaka `SELECT` naredba prvo mora proći semantičku proveru pre pravljenja sâmog plana. Semantička provera se sastoji od sledećih koraka:
- provera postojanja fizičkih tabela spomenutih u naredbi
- proširenje zamenskih članova na konkretne kolone
- provera da se zamenski članovi ne koriste u izrazima
- provera postojanja kolona pomenutih u projekcijama i predikatu
- provera dvosmislenih imena kolona (u slučaju da dve tabele imaju isti naziv kolone i ne može da se trivijalno skonta koja se koristi)
- provera da li aritmetičke operacije mogu da se izvrše za tip kolone
- provera da li kolone u unijama imaju iste tipove

`QueryPlanner` apstraktna klasa pruža implementaciju semantičke provere, a konkretni algoritmi planiranja `SELECT` naredbe koji je nasleđuju mogu da podrazumevaju da su naredbe koje dobiju sigurno semantički validne. `QueryPlanner` takođe redukuje sve izraze i predikat pomoću `PartialEvaluator` klase.

==== Algoritam planiranja `SELECT` naredbi

`BetterQueryPlanner` klasa nasleđuje `QueryPlanner` i predstavlja implementaciju osnovnog planera koji podržava sve alternative `SELECT` naredbe predstavljene u #link(<parse_select>)[njenoj gramatici].

Stablo relacionih operatora je korektno (eng. _sound_), ako svi slogovi koje ono proizvodi ispunjavaju sve uslove relacionih operacija definisanih nekom `SELECT` naredbom. Stablo planova, koje se prevodi u stablo relacionih operatora, koje `BetterQueryPlanner` konstruiše je uvek korektno.

Problem `BetterQueryPlanner` implementacije je niska efikasnost konstruisanih stabala planova, jer ne koristi ni tehnike _RBO_ planiranja, ni tehnike _CBO_ planiranja, već izvršava samo minimalni skup koraka koji su neophodni da se obezbedi korektnost.

Algoritam planiranja se može videti na #link(<fig:algoritam_planiranja>)[dijagramu ispod].

#figure(
  image("../dijagrami/planer_algoritam_dijagram.pdf"),
  caption: [_Flowchart_ dijagram `BetterQueryPlanner` algoritma planiranja],
)<fig:algoritam_planiranja>

Kreira finalno stablo planova kroz četiri funkcije koje zovu jedne druge, imaju rastući prioritet i zadužene su za različite operacije:
- `createPlan` funkcija je ulazna tačka algoritma, definisana u `QueryPlanner` klasi i ima zaduženje kreiranja unija više `SELECT` naredbi, u slučaju postojanja `UNION ALL` gramatičke alternative.

  Unije `SELECT` naredbi zahtevaju da se kolonama svake `SELECT` naredbe pristupa pomoću imena datih u prvoj `SELECT` naredbi. Zbog ovoga se dodaje operator preimenovanja ispred svakog podstabla planova svake naredbe u uniji, sem prve. Operacija unije ima najmanji prioritet, pa se poslednja izvršava.

  Ukoliko ne postoji `UNION ALL` gramatička alternativa, funkcija vraća podstablo planova jedine `SELECT` naredbe.

  #figure(
    image("../dijagrami/primeri_stabla_planova/unija.pdf", width: 66%),
    caption: [Primer stabla planova nakon unije 3 naredbe],
  ) <fig:primer_plan_unija>

- `createSingleSelectionPlan` funkcija obezbeđuje korektnost filtriranja i projekcije.

  Dodaje operator filtriranja samo ako filter postoji; dodaje nove virtuelne kolone i briše kolone koje nisu spomenute.

  #figure(
    image("../dijagrami/primeri_stabla_planova/filtriranje_projekcija.pdf", width: 20%),
    caption: [Primer stabla planova nakon filtriranja i projekcije],
  ) <fig:primer_plan_filter_projekcija>

- `getDataSourcePlan` funkcija se brine o tome odakle će doći slogovi i ima dve putanje izvršavanja.

  Prva putanja izvršavanja se dešava kada se u `SELECT` naredbi ne spominje ni jedna fizička tabela, već se radi upit virtuelne tabele koja ima jedan slog koji se sastoji samo od konstanti. U tom slučaju, samo vraća jedan jedini #link(<dummy_table_plan>)[`DummyTablePlan`] plan čvor.

  Druga putanja izvršavanja se dešava kada se spominje jedna ili više fizičkih tabela. Ako se spominje jedna fizička tabela, njen plan biva vraćen. Ako se spominje više od jedne fizičke tabele, potrebno je uraditi operaciju proizvoda. Proizvod tabela se vrši tako što se prva spomenuta tabela proglasi da bude početna, pa se prolazi kroz sve ostale spomenute tabele i ponavlja se postupak: kreiraju se dva proizvod plana, jedan gde je dosadašnje podstablo planova na levom mestu, a plan sledeće tabele na desnom i jedan gde je redosled obrnut; plan koji ima manje pristupa blokovima se uzima kao sledeći koren podstabla planova i postupak se izvršava dok se ne prođe kroz sve pomenute tabele. Time se dobija oformljeno podstablo planova gde su proizvodi tabela zadovoljeni. Ovakva provera broja pristupanih blokova nije optimalna, ali može pomoći u otklanjanju veoma neefikasnih stabala planova i predstavlja jedino mesto gde se primenjuje _RBO_ tehnika planiranja.

  #figure(
    image("../dijagrami/primeri_stabla_planova/proizvod.pdf", width: 66%),
    caption: [Primer stabla planova nakon proizvoda 3 tabele],
  ) <fig:primer_plan_proizvod>

- `fullyQualifiedTablePlan` obezbeđuje davanje punokvalifikujućih imena kolonama fizičkih tabela.

  U slučajevima gde se vrši proizvod dve (ili više) tabele, postoji mogućnost da te dve tabele imaju istoimenu kolonu. Ovo je čest slučaj jer povećava čitljivost upita i strukture tabela, pa je za njega potrebno pružiti adekvatnu podršku.

  U prethodnoj funkciji koja vrši proizvode, rečeno je da se direktno barata sa planovima tabela. Ovo nije precizno, jer se ne barata direktno sa tabelama, već sa podstablom planova koje predstavlja tabelu sa punokvalifikovanim imenama kolona. Kvalifikacija imena kolona se vrši preko operatora preimenovanja, tako što se pre samog imena kolone doda ime tabele i tačka (`ime_kolone` postaje `ime_tabele.ime_kolone`). Dozvoljava i preimenovanje tabela u slučaju da vršimo proizvod dve (ili više) iste tabele.

  Ako se proizvod radi samo sa punokvalifikovanim tabelama, ne postoji mogućnost da sistem ne može da prepozna kojoj tabeli kolona pripada, sem ako korisnik nije zadao semantički neispravnu naredbu.

  #figure(
    image("../dijagrami/primeri_stabla_planova/kvalifikacija_tabele.pdf", width: 20%),
    caption: [Primer stabla planova nakon kvalifikacije kolona tabele],
  ) <fig:primer_kvalifikacija>

#figure(
  image("../dijagrami/primeri_stabla_planova/celo_stablo.pdf", width: 88%),
  caption: [Primer kompletnog stabla planova],
) <fig:primer_kvalifikacija>

==== Automatsko generisanje opisa planova <explain>

Drugi primer _read-only_ naredbe je `EXPLAIN` naredba. Njena uloga u sistemu je tabelarno ispisivanje kompletnih stabla planova. `EXPLAIN` naredba se poziva tako što se doda ključna reč `EXPLAIN` ispred `SELECT` naredbe. Nije podržana za modifikacione naredbe.

`EXPLAIN` naredba je implementirana tako da generiše slogove koji prate šemu specijalne tabele koja ima sledeće kolone: ime relacionog operatora, procena kroz koliko blokova će taj relacioni operator proći da generiše sve slogove, procena broja slogova i specijalni detalji. Svaki slog predstavlja čvor rezultujućeg stabla relacionih operatora. Iako je poenta naredbe tabelarni prikaz stabla, naredba samo generiše ove slogove i ne brine se o #link(<stampac-tabela>)[formatiranju tabele].

#figure(
  ```text
  ┌────────────────────┬────────────┬─────────────┬─────────────────────────┐
  │ Scan               │ Block est. │ Record est. │ Details                 │
  ├────────────────────┼────────────┼─────────────┼─────────────────────────┤
  │ ExtendProjectScan  │          5 │          52 │                         │
  │ └─ SelectScan      │          5 │          52 │ student.isactive = TRUE │
  │    └─ RenameScan   │          5 │         103 │                         │
  │       └─ TableScan │          5 │         103 │ 'student'               │
  └────────────────────┴────────────┴─────────────┴─────────────────────────┘
  ```,
  caption: [Primer rezultujuće tabele `EXPLAIN` naredbe],
)<fig:explain_naredba>

=== Planiranje modifikacionih naredbi

Modifikacione naredbe su razne, a `UpdatePlanner` ima istu ulogu za njih, kao što `QueryPlanner` ima za `SELECT` naredbu, a to je samo semantička provera. Konkretni algoritmi planiranja modifikacionih naredbi mogu da podrazumevaju da je naredba semantički validna i da su izrazi i predikati redukovani pomoću `PartialEvaluator` klase.
Za svaku modifikacionu naredbu su opisani koraci za semantičku proveru.

`INSERT` naredba služi za umetanje novih slogova. Semantička provera `INSERT` naredbi se sastoji od sledećih koraka:
- da li postoji fizička tabela u koju se umeću novi slogovi,
- da li se broj kolona novih slogova podudara sa brojem definisanim u šemi tabele,
- provera tipova kolona novih slogova sa tipovima kolona definisanih u šemi tabele,
- da li su dozvoljene _NULL_ vrednosti za kolone gde je vrednost novih slogova _NULL_.

`UPDATE` naredba služi za ažuriranje vrednosti postojećih slogova na osnovu nekog uslova filtriranja. Semantička provera `UPDATE` naredbi se sastoji od sledećih koraka:
- da li postoji fizička tabela čiji se slogovi ažuriraju,
- da li postoje kolone pomenute u predikatu i izrazima ažuriranja,
- da li su dozvoljene _NULL_ vrednosti za kolone gde je nova vrednost _NULL_,
- provera tipova kolona ažuriranih slogova sa tipovima kolona definisanih u šemi tabele.

`DELETE` naredba služi za brisanje postojećih slogova na osnovu nekog uslova filtriranja. Semantička provera `DELETE` naredbi se sastoji od sledećih koraka:
- da li postoji fizička tabela čiji se slogovi brišu,
- da li postoje kolone pomenute u predikatu.

`CREATE TABLE` naredba služi za kreiranje novih tabela. Semantička provera `CREATE TABLE` naredbi se sastoji od sledećih koraka:
- da li tabela sa tim imenom već postoji,
- da li je veličina sloga prevazišla maksimum definisan #link(<slogovi-fiksne-duzine>)[ograničenjem na fiksne slogove].

==== Algoritam planiranja `INSERT` naredbe

Stablo planova za `INSERT` naredbe je uvek isto i sastoji se samo od jednog `TablePlan` čvora. Taj čvor se pretvara u svoj prateći relacioni operator nad kojim se vrše umetanja novih slogova. Vraća broj dodatih slogova.

Algoritam umetanja novog sloga implementiran u `TableScan` operatoru funkcioniše tako što traži prvo slobodno mesto za nov slog, ali počevši od pozicije trenutnog sloga tog `TableScan` objekta. Nakon što se operator inicijalizuje, pozicioniran je na početku tabele, to jest pre prvog sloga. Ovo znači da će umetanje prvog sloga u listi novih slogova uvek počinjati od početka. Prednost ovog pristupa je to što će obrisani slogovi brzo biti ponovo popunjeni, pa se prostor maksimalno dobro iskorišćava. Mana ovog pristupa je to što umetanje prvog novog sloga može da potraje, jer u najgorem slučaju mora da se prođe kroz sve slogove tabele da se pronađe prazno mesto. Drugi način implementacije algoritma je da se umetanje novih slogova uvek vrši od kraja. Prednost je konzistentno dobra brzina umetanja, jer se preskače pretraga za slobodno mesto. Mana je to što se sve više i više prostora baca na obrisane slogove.

Implementirano rešenje je kompromis ova dva algoritma, gde se za svaku tabelu pamti pozicija poslednje umetnutog sloga i novi slogovi se umeću od te pozicije. Pamćenje pozicija poslednje umetnutih slogova važi samo dok je sistem upaljen i resetuje se prilikom gašenja sistema. Zadržava prednost brzine umetanja, a nakon restarta sistema mesta obrisanih slogova mogu ponovo biti popunjena. Takođe, resetuje se pri poništavanju transakcije.

==== Algoritam planiranja `UPDATE` naredbe

Stablo planova za `UPDATE` naredbe se može sastojati samo od jednog `TablePlan` čvora, ali ispred njega može stojati i `SelectReadOnlyPlan` čvor u slučaju da slogovi koji trebaju biti ažurirani moraju da ispune neki uslov filtriranja. Ova dva (ili jedan) čvora se pretvaraju u svoje prateće relacione operatore koji znaju da postave nove vrednosti. Vraća broj ažuriranih slogova.

Za razliku od `INSERT` naredbe, `UPDATE` naredba može da sadrži izraze koji nisu konstantni, to jest koji pominju kolone tabele koja se ažurira.

==== Algoritam planiranja `DELETE` naredbe

Stablo planova za `DELETE` naredbe se može sastojati samo od jednog `TablePlan` čvora, ali ispred njega može stojati i `SelectReadOnlyPlan` čvor u slučaju da ne trebaju da se obrišu svi slogovi iz tabele već samo oni koji ispunjavaju uslov filtriranja. Vraća broj obrisanih slogova.

Brisanje nekog sloga samo označava mesto gde se taj slog nalazio kao slobodno za umetanje novog sloga, umesto da radi kompresiju datoteke i zapravo izvrši fizičko brisanje.

==== Algoritam planiranja `CREATE TABLE` naredbe

`CREATE TABLE` naredba je specijalna vrsta naredbe jer ne modifikuje slogove običnih tabela, već slogove #link(<kataloske-tabele>)[kataloških tabela]. Dodaje jedan slog u katalošku tabelu koja pamti sve postojeće tabele. Dodaje slog za svaku kolonu nove tabele u katalošku tabelu koja pamti sve postojeće kolone. Pošto ne utiče na slogove običnih tabela, uvek vraća nula za broj slogova na koje je uticala.

Menadžer metapodataka tabela interno konstruiše tri `TableScan` objekta: prvi koristi da proveri jedinstvenost imena nove tabele, drugi koristi da umetne slog za novu tabelu u `tablecatalog` katalošku tabelu, a treći koristi da umetne slogove novih kolona u `fieldcatalog` katalošku tabelu. Posledica ovakve implementacije je ta da `UpdatePlanner` apstraktna klasa zapravo ne vrši semantičku proveru pre pozivanja algoritma planiranja `CREATE TABLE` naredbe, nego reaguje na greške i transformiše ih, ako se dese.
