#import "../funkcije.typ": todo

= Zaključak <zakljucak>

== Zbog čega

Sistemi za upravljanje bazama podataka (SUBP) su me interesovali od druge godine osnovnih akademskih studija, nakon slušanja predmeta "Baze podataka". SUBP-ovi su osnova većine informacionih sistema koji se koriste u današnjici i razumevanje samo _SQL_ jezika nije dovoljno da bi se shvatilo njihovo interno funkcionisanje. Zbog njihove kompleksnosti, shvatio sam da bi ih najbolje razumeo tako što implementiram svoj SUBP sistem. Naučio sam mnogo iz oblasti upravljanja datotekama, izolacije podataka i obrade deklarativnih programskih jezika kao što je _SQL_.

== Ograničenja sistema

Iako je _LBDB_ SUBP funkcionalan i pruža zadovoljavajuću podršku _SQL_ jezika, za neke delove implementacije se može reći da im fali još dorade. Odlučio sam da napravim presek kod ovih stvari, da bi postigao funkcionalnu implementaciju u doglednom vremenskom periodu, makar ona ne bila savršena.

=== Implementiran je samo podskup _SQL_-a

Specifikacija _SQL_ jezika definisana _ISO/IEC 9075_ standardom#footnote[https://en.wikipedia.org/wiki/ISO/IEC_9075] pokriva veliku količinu naredbi. _LBDB_ sistem implementira samo najosnovnije naredbe potrebne za primitivno upravljanje tabelama i slogovima.

Argumentacija ne implementiranja raznih grupa naredbi je sledeća:
- Iako se brisanje tabela svodi na brisanje slogova u kataloškim tabelama i brisanje datoteka tih tabela, sistem oporavka i potvrde transakcija nije dovoljno napredan da prati promene koje se dešavaju van bafera: kreiranje i brisanje datoteka. Na primer, ako se u istoj transakciji kreira i obriše tabela, nakon potvrde transakcije datoteka tabele će ostati na disku. Ovo se dešava jer proces potvrde zapisuje sve bafere (sekcija @undo_only_recovery), a bar jedan bafer će biti dodeljen novoj obrisanoj datoteci i njegovo pisanje na disk će uvek rekreirati datoteku.

  Dodavanje novih blokova na kraju datoteka je iste prirode kao i kreiranje i brisanje datoteka, ali poništavanje te akcije je lakše implementirati pošto će datoteka i dalje postojati nakon oporavka.
- Ne postoje operacije nad celim bazama podataka (`CREATE DATABASE ...`) jer nisu neophodne za funkcionisanje implementacije relacionog modela.
- Ne postoje pogledi (eng. _views_). Iako u _Database Design And Implementation_ @simpledb knjizi postoji opis implementacije pogleda, odlučio sam da ih izbacim jer se nisu slagali sa svim semantičkim proverama planera. Potrebno je produbiti i eventualno promeniti načine na koji planer proverava upite da bi se lako proveravali i pogledi.
- `GROUP BY`, `DISTINCT`, `ORDER BY` i ostali delovi `SELECT` naredbe koji zahtevaju agregaciju podataka nisu podržani jer ne postoji implementacija materijalizovanog procesovanja. _Database Design And Implementation_ @simpledb u poglavlju 13 opisuje materijalizovano procesovanje, pa ću ga istražiti za sledeću iteraciju _LBDB_ sistema.
- Ostatak _SQL_ jezika je previše kompleksan za implementaciju u okviru ovakvog projekta, ali je vredno istražiti ga da bi se shvatilo kako moderni SUBP-ovi funkcionišu @big_book.

=== Ograničenje na slogove fiksne dužine <slogovi-fiksne-duzine>

_LBDB_ sistem za upravljanje datotekama i slogovima ograničava slogove na fiksnu, unapred definisanu veličinu i ređa ih serijski jedne do drugih. Prednost ovog načina upravljanja slogovima je jednostavnost implementacije i lako računanje pozicija slogova (što dalje omogućava lako brisanje, umetanje, ...). Uz neke promene (omogućavanje _NULL_ vrednosti), preuzet je direktno iz _Database Design And Implementation_ @simpledb knjige.

Prezentuju se dve velike mane:
- čuvanje vrednosti različitih dužina u okviru iste kolone (_String_, to jest _*VAR*__CHAR_ tip) je nemoguće, jer je uvek potrebno znati unapred veličinu svih vrednosti. Da bi se mogle smestiti sve vrednosti do te dužine, finalna veličina kolone će uvek biti maksimalna.
- ukupna veličina sloga (u bajtovima) ne sme da bude veća od sistemski definisane veličine jednog bloka (isto u bajtovima) jer slogovi ne mogu da se prostiru kroz više od jednog bloka.

_Slotted Page Architecture_ (_SPA_) predstavlja arhitekturu koja rešava ove probleme i moderni SUBP-ovi je intenzivno koriste#footnote[https://www.postgresql.org/docs/current/storage-page-layout.html]. Koncepti na koje se oslanja su prvi put uvedeni u okviru _SystemR_ _RSS_ (_Relational Storage System_) sistema @slotted_pages.

U okviru _SPA_, vrednosti slogova, pa ni sami slogovi, nemaju predefinisanu poziciju. Blokovi se isto mapiraju na stranice. Jedna stranica se sastoji od dve komponente: zaglavlje i vrednosti. U zaglavlju stoje specijalni brojevi koji označavaju pozicije (eng. _slots_) vrednosti. Zaglavlje se uvek nalazi na početku stranice i raste u desno, a vrednosti se uvek nalaze na kraju stranice i rastu u levo. Stranica se smatra popunjenim ako se zaglavlje preklopi sa vrednostima.

Ako se stranica popuni na taj način da svi slogovi koji joj logički pripadaju nemaju dovoljno prostora za sve svoje vrednosti, toj stranici se dodeljuje stranica prelivanja (eng. _overflow page_) i time se izbegava ograničenje veličine sloga na veličinu bloka. Pozicija stranice prelivanja se isto nalazi u zaglavlju. Moguće je uvezivati više stranica prelivanja. Ako se stranica popuni i treba da se doda novi slog koji joj ne pripada logički, pravi se nova stranica umesto stranice prelivanja.

U zaglavlju takođe stoji i pozicija sledećeg logičkog sloga. Slogovi se identifikuju preko _slot_ vrednosti pa je reorganizacija, brisanje i dodavanje slogova moguća manipulacijom samo _slot_ vrednosti. Ovo znatno olakšava probleme stvorene stranicama prelivanja, jer se slogovi ne prate preko fizičke pozicije, već preko logičke pozicije. Rupe i fragmentacija stranica se rešavaju kompakcijom (_PostgreSQL_ `VACUUM` naredba#footnote[https://www.postgresql.org/docs/current/sql-vacuum.html]).

=== Sporo računanje statističkih metapodataka <ogranicenje-stat-podataka>

Statistički metapodaci sistema se čuvaju u memoriji i pristup njima je brz. Problemi ovog načina su to što se ne skalira efikasno sa brojem tabela i to što se računanje metapodataka mora raditi iznova (pri svakom pokretanju sistema, na svakih $100$ poziva). Bolji pristup je čuvanje statističkih metapodataka u zasebnim kataloškim tabelama. _LBDB_ ne podržava ovaj način zato što je:
- održavanje ažurnosti tih tabela zahtevno
- čitanje iz tih tabela potrebno raditi brzo što dalje zahteva implementaciju _read uncommitted_ izolacionog nivoa transakcija

Moderni SUBP-ovi implementiraju i ceo deo spomenutog SQL standarda u kom su definisani specijalni pogledi i tabele metapodataka @simpledb. Pored toga postoje i kompleksni histogrami koji sadrže razne podatke iz više tabela i dodatno ubrzavaju upite @histogrami.

=== Nedostajuća implementacija indeksnih struktura podataka

Indeksi predstavljaju specijalnu strukturu podataka koja omogućava znatno bržu pretragu podataka koji se nalaze u njima. Realizuju se preko jedne od _BTree_ varijanti ili kao _Hash_ indeks.

Iako su indeksi krucijalni za efikasan SUBP, odlučio sam da ih ne implementiram jer želim da im se posvetim u okviru sledeće iteracije _LBDB_ sistema.

Ipak, podržano je kreiranje metapodataka vezanih za indekse u okviru menadžera metapodataka, ali smatram da ovo nije vredno spominjati van ovog poglavlja jer ne utiče na dalji sistem.

=== Neoptimalni planer <neoptimalni-planer>

Planer _LBDB_ sistema ne koristi skoro nijednu naprednu tehniku planiranja. Brzina izvršavanja upita dosta zavisi od redosleda tabela u upitu, uslov filtriranja se primenjuje nakon ulančavanja svih tabela umesto izolovano po tabeli, selektivnost i procene broja _NULL_ i jedinstvenih vrednosti se ne koriste, itd.

_Database Design And Implementation_ @simpledb poglavlja 14 i 15 opisuju implementaciju efikasnijeg planera koji intenzivno koristi _RBO_ tehnike planiranja, ali je ovo ostavljeno za sledeću iteraciju _LBDB_ sistema.

=== Ulančavanje članova predikata je moguće samo konjunkcijom <samo-and>

Predikati se mogu sastojati samo od članova ulančanih logičkom operacijom konjunkcije (`AND`). Negacija izraza (`NOT`) i ulančavanje operacijom disjunkcije (`OR`) nisu podržani iako je lako dodati obradu logičkih operacija jer nisam bio siguran kako se uklapaju u napredne tehnike planiranja. Kada završim sa istraživanjem naprednog planera, biće mi lakše da ubacim i nedostajuće logičke operacije. Uz njih, proširiću i `PartialEvaluator` zarad korektne redukcije.
