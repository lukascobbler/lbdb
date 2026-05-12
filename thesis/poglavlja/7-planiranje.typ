#import "../funkcije.typ": todo

= Planiranje <planiranje>

Većina operacija definisanih SQL standardom zahteva propratno stablo relacionih operatora. Konstrukcija i provera tih stabala je posao podsistema planiranja, to jest planera.

Glavna podela planera u relacionim bazama podataka je na planere koji prate striktna pravila pravljenja planova (_rule-based planner_, _RBO_) i planere koji rade sa procenjenim cenama individualnih operatora (eng. _cost-based planner_, _CBO_). Raniji sistemi upravljanja bazama podataka poput _INGRES_ sistema su koristili _RBO_ planere @ingres_rbo, dok moderni sistemi koriste _CBO_ planere #footnote[https://www.postgresql.org/docs/current/planner-optimizer.html]#super(",") #footnote[https://www.postgresql.org/docs/current/planner-stats-details.html] koji su postali popularni nakon _System R_ istraživačkog papira @systemR.

== Struktura planova u sistemu

Za svaki relacioni operator se definiše njegov plan, a za svako stablo relacionih operatora se definiše stablo planova. Plan relacionog operatora opisuje šemu nakon primene operatora i omogućava računanje #link(<statisticki-metapodaci>)[statističkih metapodataka] za rezultujuću virtuelnu tabelu.

Statističke metapodatke koje plan može da izračuna su isti kao i statistički metapodaci koji se prate za fizičke tabele, a oni su:
- broj blokova potrebnih za prolazak kroz sve slogove,
- broj slogova,
- broj jedinstvenih vrednosti za svaku kolonu,
- broj _NULL_ vrednosti za svaku kolonu.

Ekvivalentna stabla relacionih operatora su ona koja generišu identične skupove rezultata. Planer konstruiše različite planove koji odgovaraju ovim stablima, a zatim, na osnovu statističkih metapodataka, procenjuje cenu njihovog izvršavanja. Eliminacijom skupih planova, bira onaj sa optimalnim vremenom izvršavanja. Izračunati statistički podaci #link(<table-plan>)[nisu 100% precizni], ali bez obzira na to, pomažu pri eliminaciji skupih planova.

=== Hijerarhija implementacije planova

Najopštija podela planova je na one koji samo čitaju podatke (eng. _read-only_) i na one koji mogu da modifikuju podatke, po #link(<hijerarhija_rel_op>)[hijerarhiji relacionih operatora]. Za razliku od hijerarhije relacionih operatora, ne postoji hijerarhija podrazumevanih implementacija jer klase planova nemaju toliko zajedničkih osobina. Podela na _read-only_ i modifikacione planove je odrađena preko _generics_
#figure(
  image("../dijagrami/hijerarhija_planova.pdf"),
  caption: [
    Hijerarhija implementacije planova
  ],
)<fig:hijerarhija_planova>

==== `Plan`

`Plan` interfejs definiše operacije neophone za računanje svih statističkih podataka, dobijanje šeme rezultujuće tabele i dobijanje slogova za #link(<explain>)[automatski opis plana].

==== `TablePlan` <table-plan>

`TablePlan` opisuje konkretnu fizičku tabelu, umesto da vrši transformacije virtuelne tabele. Izlazna šema je jednaka šemi fizičke tabele. Izvlači statističke podatke direktno iz menadžera metapodataka za tabelu za koju je vezan. Statistički podaci #link(<racunanje-statistike>)[nisu ažurni], ali pružaju dovoljno dobru statistiku za potrebe LBDB sistema. Izračunati statistički metapodaci svih planova u stablu planova eventualno zavise od vrednosti statističkih metapodataka ovog plana. Može da se koristi i u kontekstima modifikujućih stabala operatora i u kontekstima _read-only_ stabala operatora i zbog toga postoji i `TableReadOnlyPlan` varijanta koja ima istu funkciju.

==== `DummyTablePlan`

`DummyTablePlan` opisuje virtuelnu tabelu koja se sastoji od jednog sloga u upitima koji ne rade sa fizičkim tabelama. Izlazna šema se određuje na osnovu konstantnih vrednosti u upitu, a statistički podaci su precizni jer se lako računaju pošto je broj konkretnih vrednosti jako mali.

==== `SelectPlan`

`SelectPlan` opisuje virtuelnu tabelu nakon primene uslova filtriranja. Izlazna šema je jednaka šemi podređenog plana. Broj blokova ostaje nepromenjen jer da bi znali koji sve slogovi ispunjavaju uslov filtera, potrebno je proći kroz sve slogove, pa sa time i kroz sve blokove podređenog plana.

Redukcioni faktor predstavlja za koliko puta će broj slogova na izlazu biti smanjen. Računa se na osnovu uslova filtriranja, koji je predstavljen #link(<predikati>)[predikatom]. Svaki član predikata predstavlja jedan deo filtera i ima svoj redukcioni faktor. Pošto sistem podržava samo ulančavanje članova `AND` logičkim operatorom, redukcioni faktor predikata se računa kao proizvod svih redukcionih faktora članova od kog se predikat sastoji.

Algoritam računanja redukcionog faktora jednog člana je predstavljen na #link(<fig:racunanje_redukcionog_faktora>)[dijagramu]. Pošto se broj slogova nakon filtriranja dobija deljenjem broja slogova podređenog plana i redukcionog faktora, specijalni slučajevi se mogu predstaviti različitim konstantama.

Specijalni slučaj nejednakosti je predstavljen konstantom _NEJEDNAKOSTI_ koja ima vrednost $3.0$ i označava procenjenu vrednost redukcije u slučaju korišćenja operacija nejednakosti.

Specijalni slučaj kada postoje prekompleksni izrazi je predstavljen konstantom _KOMPLEKSNO_ koja ima vrednost $10.0$ i označava procenjenu vrednost redukcije u slučaju postojanja izraza koji ima ili više od dve kolone ili izraza koji kombinuje kolone sa operacijama poređenja na netrivijalan način.

Slučaj kada se ni jedan slog ne podudara sa članom je predstavljen konstantom maksimalne vrednosti `Double` tipa i označava maksimalnu redukciju. Slučaj kada svi slogovi podudaraju neki član je predstavljen konstantom $1.0$ i predstavlja odsustvo redukcije.

Izbor vrednosti ovih konstanti je opisan u _System R_ istraživačkom papiru @systemR.

#figure(
  image("../dijagrami/racunanje_redukcionog_faktora.pdf"),
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

Procena broja jedinstvenih vrednosti za svaku izlaznu kolonu vrši se na osnovu složenosti izraza koji tu kolonu definiše i on je jednak:
- $0$, ukoliko se traži procena za kolonu koja se ne nalazi u projekciji,
- $1$, ukoliko je izraz konstanta (ne referencira ni jednu kolonu),
- broju jedinstvenih vrednosti te kolone iz podređenog plana, ukoliko izraz referencira tačno jednu kolonu. Pretpostavka je da većina transformacija nad jednom kolonom (npr. aritmetičke operacije) zadržava sličnu distribuciju vrednosti,
- ukupnom broju slogova, ukoliko izraz referencira više od jedne kolone. U ovom slučaju, pretpostavlja se da kombinacija više polja rezultuje jedinstvenom vrednošću za svaki slog.

Procena broja _NULL_ vrednosti za svaku izlaznu kolonu vrši se na osnovu složenosti izraza koji tu kolonu definiše i on je jednak:
- $0$, ukoliko se traži procena za kolonu koja se ne nalazi u projekciji,
- $0$, ukoliko šema garantuje da izraz ne može imati _NULL_ vrednosti (nije _nullable_),
- $0$ ukoliko je izraz jednak bilo kojoj konstanti sem _NULL_ konstante,
- $1$ ukoliko je izraz jednak _NULL_ konstanti,
- broju jedinstvenih vrednosti te kolone iz podređenog plana, ukoliko izraz referencira tačno jednu kolonu. Pretpostavka je da većina transformacija nad jednom kolonom zadržava sličnu distribuciju _NULL_ vrednosti,
- maksimalanom broju _NULL_ vrednosti među svim referenciranim kolonama iz podređenog plana, ukoliko izraz referencira više od jedne kolone. Pretpostavlja se da će izraz biti _NULL_ ukoliko je barem jedan od operanada _NULL_, pa je maksimalan broj _NULL_ vrednosti pesimistična procena.

==== `RenamePlan`

`RenamePlan` opisuje virtuelnu tabelu sa svim primenjenim preimenovanjima kolona. Izlazna šema sadrži sve kolone sa novim imenom i ni jednu kolonu sa starim imenom.
S obzirom da operacija preimenovanja ne dodaje nove slogove, broj blokova i broj slogova ostaju nepromenjeni i direktno se preuzimaju od podređenog plana.
Procena broja jedinstvenih vrednosti kolone je jednaka podređenom planu ukoliko se traži novo ime stare kolone ili ukoliko je kolona nepreimenovana, a jednaka je `0` ako se traži staro ime preimenovane kolone. Procena broja _NULL_ vrednosti funkcioniše isto.

==== `ProductPlan`

`ProductPlan` opisuje virtuelnu tabelu koja je proizvod dve tabele. Izlazna šema sadrži sve kolone od obe tabele.

Za demonstaciju računice broja blokova kod proizvoda dve tabele, definišemo $T_1$ i $T_2$:

#figure(
  {
    set par(justify: false)
    table(
      columns: (0.2fr, 0.4fr, 0.4fr, 0.4fr),
      align: (center, center),
      inset: 8pt,
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

Ovaj primer se odnosi na rad sa konkretnim fizičkim tabelama, ali u generalnom slučaju, tabele nisu fizičke, već su predstavljene podređenim planovima sa kojima plan proizvoda barata.

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

dve stvari: provera validnosti, konstrukcija efikasnog plana izvrsavanja

=== Ulazna tačka kreiranja i izvršavanja planova

`planner` klasa, o cemu se sve brine i omogucava jdbc podrsku

=== Parcijalna evaluacija izraza

partial evaluator

=== Planiranje _query_ komandi

sta je query, query se mapira na SELECT, queryplanner interfejs

==== Osnovni algoritam planiranja _SELECT_ komandi

#todo("objasniti kako se postize join dve tabele koje imaju istoimene kolone")

#todo("dijagrami planiranja iz koda")

#todo("ne implementira nikakvu cost based optimizaciju")

=== Planiranje modifikacionih komandi

koje su modifikacione komande, updateplanner interfejs

==== Algoritam planiranja _INSERT_ komandi

==== Algoritam planiranja _UPDATE_ komandi

==== Algoritam planiranja _DELETE_ komandi

==== Algoritam planiranja _CREATE TABLE_ komandi

#todo("specijalna vrsta operacije koja ne zahteva stablo rel. op. vec samo modifikuje tabele metapodataka")

=== Automatsko generisanje opisa planova <explain>

#todo("pomenuti da explain postoji")

#todo(
  "objasniti poentu planiranja i da se planovi dobijaju od statementa, objasniti podelu na query i update plannere, objasniti da oba implementiraju interfejs i da kada statement dodje do logike planiranja, da je sigurno proveren, staviti dijagrame izlaznih planova, objasniti da je ovo najobicniji planner i da nije najefikasniji, ali je matematicki tacan",
)
