#import "../funkcije.typ": todo

= Klijentsko-serverska arhitektura <klijent-server>

Postoje dva glavna načina kako sistem upravljanja relacionim bazama podataka može raditi: lokalno, bez mrežne infrastrukture (eng. _embedded connection_) i kao server.

Karakteristike sistema u lokalnom režimu rada:
- radi u istom procesu operativnog sistema kao i aplikacija koja ga koristi,
- samo jedna aplikacija može da koristi tu instancu sistema,
- ne zahteva mrežnu konekciju,
- zahteva manje resursa,
- ponaša se kao biblioteka koja poznaje unutrašnjosti relacionog modela podataka.

Karakteristike sistema u serverskom režimu rada:
- radi kao zaseban proces operativnog sistema,
- više različitih aplikacija i korisnika može da pristupi toj instanci sistema,
- zahteva mrežnu konekciju,
- zahteva više resursa,
- ponaša se kao pružilac usluge baratanja relacionim modelom podataka.

_LBDB_ sistem podržava samo serverski režim rada.

== Komunikacija sa klijentima

Kod serverskog režima rada, predpostavlja se da je klijent na udaljenom računaru i nema pristup nikakvim resursima računara na kom se pokreće server. Posledica ovog je da sva komunikacija mora da se vrši kroz mrežu, preko nekog protokola komunikacije.

=== Odgovori sistema <response>

Sve vrste odgovora koje server može dati za neku naredbu koju je klijent zadao predstavljene su _Java_ _record_ konstruktom i implementiraju `Response` _sealed interface_.

- `QuerySet` predstavlja odgovor na _read-only_ naredbe, čiji je rezultat skup slogova. Jedan slog je predstavljen listom konstanti. Da bi sistem znao kako da pošalje slogove preko mreže, a kasnije i kako da napravi tabelarni prikaz, potrebno je poslati i propratnu šemu koja opisuje poslate slogove.
- `EmptySet` predstavlja odgovor na modifikacione naredbe. Sadrži samo podatak o tome na koliko slogova je uticano.
- `ErrorResponse` se vraća klijentu kada se desi greška prilikom parsiranja, prilikom planiranja ili prilikom izvršavanja naredbe (deljenje sa nulom, ...).

#figure(
  image("../dijagrami/response.pdf", width: 40%),
  caption: [
    Struktura `Response` hijerarhije
  ],
)<fig:struktura_planera>

=== Protokol serijalizacije <protokol>

Preko mreže je moguće slati samo niz bajtova. Pošto `Response` objekti nisu podrazumevano predstavljeni nizovima bajtova, za njih se mora definisati način serijalizacije i deserijalizacije u i iz niza bajtova.

Protokol serijalizacije `Response` objekata je inspirisan _RESP_ (_REdis Serialization Protocol_) #footnote[https://redis.io/docs/latest/develop/reference/protocol-spec/] protokolom. Svakom tipu se dodeljuje jedan bajt koji ga jedinstveno predstavlja. `'*'` za `QuerySet`, `'_'` za `EmptySet` i `'-'` za `ErrorResponse`.

Brojčane vrednosti se pretvaraju u bajtove `byte` tipa koji se sastoji od jednog bajta, u bajtove `short` tipa koji se sastoji od $2$ bajta ili u bajtove `int` tipa koji se sastoji od $4$ bajta. `String` vrednosti se pretvaraju u bajtove preko _UTF-8_ (_Unicode Transformation Format_) kodiranja.

`EmptySet` i `ErrorResponse` je trivijalno serijalizovati i deserijalizovati, jer se sastoje od samo jedne vrednosti.

`QuerySet` sadrži dosta vrednosti koje imaju specifičan format, pa je algoritam serijalizacije kompleksniji (svaka vrednost je podrazumevano zapisana u bajtovima):
- zapisuje se jedinstveni identifikator `QuerySet` tipa: `'*'`
- zapisuje se količina kolona šeme rezultujuće tabele kao `short`
- za svaku kolonu se zapisuje:
  - dužina bajtova imena kao `short`
  - ime kolone
  - tip kolone kao `short`, ako je tip _VARCHAR_ zapisuje se i njegova dužina kao `int`
  - da li je kolona _nullable_, kao `byte`
- zapisuje se količina slogova
- za svaku vrednost svakog sloga se zapisuje:
  - $0$ ako je _NULL_, $1$ ako nije _NULL_ kao `short`
  - brojnu vrednost kao `int` ako je tip _INTEGER_, brojnu vrednost kao `byte` ako je tip _BOOLEAN_, dužina _String_-a kao `int` tip zajedno sa kodiranim _String_-om ako je tip _VARCHAR_

Na kraju serijalizacije se računa dužina bajtova i ona se stavlja pre svih bajtova, da bi se prilikom deserijalizacije znalo koliko bajtova da se pročita.

Algoritam deserijalizacije za sve tipove isto funkcioniše, ali u suprotnom smeru.

#figure(
  image("../dijagrami/protokol_paket.pdf"),
  caption: [
    Struktura `QuerySet` paketa
  ],
)<fig:queryset-paket>
Na slici plavo predstavlja ceo paket, zeleno predstavlja deo paketa koji se ponavlja za svaku kolonu šeme, crveno predstavlja deo paketa koji se ponavlja za svaki slog, a žuto predstavlja deo paketa koji se ponavlja za svaku vrednost sloga.

== Serverski sloj

Klasa `LBDBServer` sadrži `main` funkciju serverske aplikacije sistema. Čita i validira argumente komandne linije: port na kom će server raditi i putanja gde će se čuvati datoteke sistema. Pokreće instancu servera sa prosleđenim argumentima. Sva logika koja podržava serverske operacije se nalazi u `Server` klasi.

=== Rukovođenje klijentima

Prva funkcionalnost servera je obrada klijentskih konekcija i naredba koje one šalju. Prilikom pokretanja servera, instancira se serverski soket koji prihvata konekcije na određenom portu i otvara klijentski soket za svakog klijenta.

Pošto su klijenti nezavisni, ne bi trebalo da čekaju jedni na druge i zbog toga se svaki obrađuje u zasebnoj niti. Zarad lakšeg upravljanja nitima, postoji `ThreadPool` sa $8$ fiksnih, stalno postojećih niti koje čim završe sa obradom jednog zahteva ponovo postaju slobodne.

==== Obrada jednog klijenta <obrada-klijenta>

Obrada zahteva klijenata se vrši kroz `handleClient()` funkciju. Svaki klijentski soket se kodira u jedinstveni broj sesije, tako što se uzme heš vrednost njegove konekcije. Ovime se omogućava mapiranje klijenta na njegovu trenutnu transakciju. Ako broj sesije klijenta ne postoji u sistemu, dodeljuje se nova (prva) transakcija za tog klijenta.

#link(<sesije>)[Kao što je već spomenuto], klijentske transakcije mogu da rade u režimu gde se sastoje od jedne naredbe (_autocommit_) ili u režimu gde se sastoje od više naredbi. U režimu gde se transakcije sastoje od više naredbi, potrebno je početi ih sa `START TRANSACTION` naredbom, a završiti sa `COMMIT` ili `ROLLBACK` naredbama. Ovo je glavni razlog zašto je potrebno jedinstveno identifikovati sesije klijenata, da bi njihova transakcija mogla da perzistira kroz više naredbi.

U slučaju prekida konekcije, server će izvršiti `ROLLBACK` trenutne transakcije klijenta.

U slučaju da se server gasi, povezani klijenti mogu da pošalju samo naredbe koje završavaju transakcije, ali više o tome u opisu gašenja servera.

#figure(
  image("../dijagrami/sekvenca_obrade_klijenta.svg"),
  caption: [
    Dijagram sekvence obrade klijenta
  ],
)<fig:obrada_klijenta>

=== Pisanje kontrolnih tačaka

Druga funkcionalnost servera je određivanje kada (ali ne i kako) će mirna kontrolna tačka biti pisana. Prilikom pokretanja servera se startuje i nit koja na svakih $10$ minuta započinje pisanje mirne kontrolne tačke. #link(<quiescent_alg>)[Kao što je već napomenuto], da bi se mirna kontrolna tačka zapisala, nijedna transakcija ne sme biti aktivna u sistemu.

Kada prođe $10$ minuta od poslednjeg zapisa mirne kontrolne tačke, server poziva algoritam zapisa koji interno čeka da se sve transakcije završe i zaustavlja obradu novih.

=== Gašenje sistema

Treća funkcionalnost je bezbedno gašenje sistema. Bezbedno gašenje se inicira slanjem `SIGINT` signala na `Unix` operativnim sistemima ili slanjem `CTRL_C` (ili sličnog) signala na `Windows` operativnom sistemu. Najčešće, ovo se mapira na gašenje prozora gde je server pokrenut, ili rađenjem `CTRL + C` prečice.

Bezbedno gašenje se sastoji iz tri koraka:
- prestajanje prihvatanja novih naredbi, sem naredbi završetka transakcije,
- čekanje da se završe sve transakcije,
- zapisivanje mirne kontrolne tačke.

Nakon što je gašenje inicirano, ulazi se u `drain` mod, gde se ne prihvataju konekcije novih klijenata, ali starim klijentima je dozvoljeno da završe svoje transakcije preko `COMMIT` ili `ROLLBACK` naredbi. U `drain` modu, samo ove naredbe su dozvoljene. Ako je server započeo pisanje mirne kontrolne tačke kada je zahtev za gašenje pokrenut, neće se pisati još jedna.

#figure(
  image("../dijagrami/sekvenca_gasenja_servera.svg"),
  caption: [
    Dijagram sekvence bezbednog gašenja servera
  ],
)<fig:gasenje_servera>

== Klijentski sloj

Klasa `LBDBClient` sadrži `main` funkciju klijentske aplikacije sistema. Čita i validira argument komandne linije: port na kojem se nalazi server na koji se klijent povezuje. Sva logika slanja naredbi se nalazi u ovoj klasi.

Klijentska aplikacija pruža korisnicima terminal gde se naredbe mogu upisivati. Terminal podržava automatsko završavanje ključnih reči (eng. _auto complete_) pritiskom `TAB` tastera i navigaciju istorije komandi. Implementaciju ovih stvari podržava #link(<zavisnosti-klijenta>)[_JLine_] zavisnost. Terminal prati i koliko vremena se izvršavala svaka naredba.

Paketi koje šalje serveru su serijalizovani tako da prvo stoji dužina teksta naredbe u bajtovima, a zatim i kodirani tekst naredbe. Pakete koje prima od servera deserijalizuje tako što prvo čita prva $4$ bajta koja predstavljaju dužinu paketa u bajtovima, a zatim koristi algoritam deserijalizacije koji je opisan u #link(<protokol>)[protokolu].

Podržava ispis sve tri vrste `Response` objekata, gde se `ErrorResponse` i `EmptySet` trivijalno prikazuju jer sadrže samo jednu vrednost. `QuerySet` objekti su tabele i njihovo prikazivanje se radi algoritmom štampanja tabela.

=== Štampanje tabela <stampac-tabela>

Algoritam štampanja tabela ima zadatak da lepo formatira sva imena kolona i sve vrednosti svih slogova iz `QuerySet` objekta. U tom objektu stoji šema tabele, pa ovo nije zahtevan zadatak. Koristi `StringBuilder` za efikasno kreiranje _String_-ova. Za lep prikaz koristi specijalne _Unicode_ karaktere kao što su: `┌`, `─`, `┬`, `┐`, `┼`, `│`, `└`, `┴`, `┘`, `├`, `┤`.

#figure(
  ```text
  ┌─────────┬──────────────┬──────────┐
  │ tableid │ tablename    │ slotsize │
  ├─────────┼──────────────┼──────────┤
  │       2 │ tablecatalog │      112 │
  │       3 │ fieldcatalog │      121 │
  │       5 │ department   │      164 │
  │       6 │ professor    │      173 │
  │       7 │ student      │      173 │
  │       8 │ course       │      172 │
  │       9 │ enrollment   │       20 │
  └─────────┴──────────────┴──────────┘
  ```,
  caption: [Primer ispisane tabele `SELECT * FROM tablecatalog;` naredbe],
)<fig:stampanje_tabela>

=== Masovno pokretanje naredbi

_LBDB_ paket pruža još jednu vrstu klijentske aplikacije: `BulkExecutor`. Ova klijentska aplikacija funkcioniše slično kao i obična klijentska aplikacija, ali umesto pružanja interakcije sa sistemom preko terminala, redom izvršava sve _SQL_ naredbe iz neke datoteke. Ovo radi u ručno započetoj transakciji i ako bar jedna naredba ne uspe sa izvršavanjem, javlja grešku i vrši _rollback_. Korisna je za popunjavanje tabela ili za testiranje sistema.
