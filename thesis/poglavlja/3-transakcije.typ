#import "../funkcije.typ": todo

= Upravljanje transakcijama <transakcije>

Transakcije su osnovni mehanizam za garantovanje različitih osobina otpornosti sistema za upravljanje relacionim bazama podataka. Omogućavaju sistemu da vrši atomične operacije, da se uvek održi u konzistentnom stanju, da izoluje nezavisne konkurentne operacije i da garantuje perzistentnost podataka. Generalno, ove osobine se zovu _ACID_ osobine (eng. _atomicity, consistency, isolation, durability_). #todo("citirati acid")

Svaka operacija u sistemu mora biti izvršena u okviru jedne transakcije, ali se jedna transakcija može sastojati i od više operacija koje se sve moraju ili uspešno izvšiti ili se moraju sve poništiti. Svaka transakcija ima početak i kraj. Kraj može biti potvrda (eng. _commit_) ili poništavanje (eng. _rollback_). Ako je transakcija uspešno potvrđena, od tog trenutka pa na dalje sve izvršene promene moraju biti odmah vidljive drugim transakcijama.

== Realizacija transakcionih mehanizama u sistemu

Vrednost je niz bajtova (određenog tipa), koja dobija semantilki značaj tek na apstrakcionim nivoima iznad nivoa transakcija, naime na nivou struktuiranja blokova u slogove. Na nivou transakcija, vrednosti nemaju semantičko značenje, ali da bi sistem obezbedio visok stepen usklađenosti sa _ACID_ osobinama, operacije nad vrednostima se obavljaju isključivo kroz transakcije koje enkapsuliraju svu potrebnu logiku tih osobina.

=== Transakcije kao glavno mesto pristupa vrednostima <pristup_vrednostima_u_transakcijama>

Na nivou slogova, operacije se izvršavaju u celinama jednog sloga, ali gradivni elementi tih slogova su ipak vrednosti. Stranica slogova upravlja pozicijama vrednosti, a sam pristup njima delegira transakciji za koju je vezana.

Pošto blokovi gde se vrednosti nalaze moraju biti u memoriji da bi se njima pristupalo, potrebno je učitati ih u sistemske bafere. Podsistem za upravljanje baferima je svestan samo načina mapiranja blokova na bafere, ali nema nikakve garancije o redosledu pristupa, ne čuva od konfliktujućih operacija i ne prati istoriju izmena. To je posao transakcija.

Svi baferi u radnoj memoriji sistema se grupišu u transakcije, tako što svaka transakcija čuva listu svojih bafera i radi operacije samo nad njima. Bafer ulazi u listu neke transakcije tako što ga transakcija pinuje, i time označava da se sadržaj tog bafera ne sme vratiti na disk dok transakcija ne završi sa njim (dok ga ne unpinuje). Stranica slogova i transakcija rade u paru: stranica slogova zna kojem bloku želi da pristupi, a transakcija zna kako da obezbedi da operacije nad baferom tog bloka ispoštuju _ACID_ osobine.

=== Sistem za oporavak

Sistem za oporavak je podsistem u okviru transakcija koji sadrži prvi deo logike zbog kojeg se sav pristup vrednostima radi kroz transakcije. Primarno se brine o oporavku sistema, ali se brine i o poništavanju operacija jedne transakcije (eng. _rollback_). Obuhvata _ACD_ (eng. _atomicity, consistency, durability_) osobine.

LBDB sistem je softversko rešenje koje radi u kontekstu nekog hardvera i operativnog sistema. Svaki od slojeva na koje se LBDB sistem oslanja, ima mogućnost da zakaže zbog nekog faktora koji je izvan kontrole LBDB sistema. Primeri su nestajanje struje, ubijanje LBDB serverskog procesa ili neuspešno pisanje na disk. Ako se u trenutku zakazivanja izvršava neka operacija koja menja podatke u sistemu, ti podaci će biti izgubljeni, a sistem će biti ostavljen u nekonzistentnom stanju. Korišćenje sistema koji je u nekonzistentnom stanju može dovesti do lošeg tumačenja podataka, ali može biti i opasno u pojedinim situacijama. Zbog ovoga se uvodi podsistem koji oporavlja sistem od nekonzistentnog stanja.

Konzistentno stanje se može definisati sa sledeće dve osobine: #todo("citirati simpledb")
- sve nedovršene transakcije su poništene,
- sve modifikacije potvrđenih (eng _commited_) transakcija moraju biti na disku

==== Sistem _log_-ovanja

_Log_ podaci su u _log_ fajlu uvek poređani redom kojim su se izvršili u okviru jedne transakcije. Pisanjem _log_ podataka se postiže visoko granulirana istorija izmena. Osnova svakog algoritma oporavka je prolaženje kroz istoriju izmena počevši od kraja, da bi se te izmene poništile ili ponovo primenile tačnim redosledom.

U sistemu postoji dve glavne grupe operacija: operacije modifikacije vrednosti i operacije životnog ciklusa transakcija. Za svaku operaciju se definiše struktura _log_-a koja opisuje tu operaciju. Za operacije modifikacije vrednosti, u _log_-u stoje svi podaci neophodni da se ta operacija poništi ili ponovo primeni, dok u _log_-ovima operacija životnog ciklusa transakcija stoje svi neophodni podaci da se prati rad transakcija. 

Pošto u _log_-ovima operacija modifikacije vrednosti uvek stoje i stara i nova vrednost, poništavanje ili ponova primena operacije je trivijalna. Specijalna operacija modifikacije je operacija umetanja novog bloka na kraju datoteke, koja ne menja nikakvu vrednost ali je i dalje potrebno pratiti njene pozive zbog održavanja korektne veličine datoteka. Algoritam poništavanja umetanja novog bloka nije trivijalan jer nije samo zamena vrednosti, već je potrebno označiti bafer tog bloka kao nemodifikovan i skratiti datoteku za jedan blok. U _log_ datoteku se uvek zapisuje nov _log_ za pozvanu operaciju modifikacije pre sâme obrade te operacije.

Operacije životnog ciklusa transakcija je potrebno pratiti da bi sistem oporavke znao koje transakcije su se uspešno i neuspešno izvršile i na osnovu toga reagovati na operacije modifikacije. Marker kontrolne tačke je specijalni zapis koji označava kada nije potrebno dalje prolaziti kroz _log_ datoteku. Više o njemu u #link(<alg_oporavka>)[algoritmima oporavke sistemma].

Hijerarhija _log_ zapisa počinje od glavnog interfejsa _LogRecord_ koji definiše neophodne operacije koje će zvati algoritmi oporavke sistema. Operacije životnog ciklusa transakcija ostavljaju praznu implementaciju _undo_ i _redo_ metoda jer ne modifikuju podatke. Dodatno, svaki tip _log_ zapisa ima prateću statičku metodu _writeToLog_ koja enkapsulira logiku pisanja same strukture konkretnog _log_-a preko menadžera _log_-ova za neki identifikator transakcije.

#figure(
    image("../dijagrami/log_tipovi.pdf", width: 97%),
    caption: [
      Operacije, njihovi _log_ tipovi i struktura _log_ tipova
    ]
  )<fig:log_tipovi>

==== Algoritmi oporavka sistema <alg_oporavka>

Algoritam opopravka sistema je procedura koja prati korake definisane strategijom oporavka koju sistem koristi i zajedno sa podacima iz _log_ datoteke vraća sistem u konzistentno stanje. Svaki algoritam oporavka mora biti idempotentan, jer sistem može naglo prestati sa radom i dok je u procesu oporavljanja. Proces oporavka se vrši u okviru podizanja sistema.

Postoje tri generalna algoritma oporavke #todo("citirati simpledb"):
- ponovna primena uspešnih transakcija i poništavanje neuspešnih transakcija (eng. _undo redo recovery_),
- samo poništavanje neuspešnih transakcija (eng. _undo only recovery_),
- samo ponovna primena uspešnih transakcija (eng. _redo only recovery_).
Izbor algoritma oporavke utiče na to kada će sadržaj bafera biti upisan na disk.

U LBDB sistemu, implementirani su _undo redo_ i _undo only_ algoritmi oporavke i moguće je postaviti koji će sistem koristiti u okviru #link(<fig:lbdbsettings>)[sistemskih podešavanja].

===== _Undo redo recovery_

Algoritam koji radi oba dela operacije ne zahteva dodatne restrikcije na redosled upisa sadržaja bafera na disk. Algoritam se deli na dva koraka: faza ponišstavanja i faza ponovne primene.

Faza poništavanja (eng. _undo_):

- Za svaki log zapis (čitajući unazad od kraja _log_-a):
  - Ako je tekući zapis _commit_ zapis, dodaj tu transakciju u listu potvrđenih transakcija.
  - Ako je tekući zapis _rollback_ zapis, dodaj tu transakciju u listu poništenih transakcija.
  - Ako je tekući zapis _update_ zapis (modifikaciona operacija), i transakcija nije ni u listi potvrđenih ni u listi poništenih, vrati staru vrednost na zadatoj lokaciji.

Faza ponovne primene (eng. _redo_):

- Za svaki log zapis (čitajući unapred od početka _log_-a):
  - Ako je tekući zapis _update_ zapis i transakcija je u listi potvrđenih transakcija, upiši novu vrednost na zadatoj lokaciji.

Glavna prednost ovog algoritma je to što ne utiče na redosled pisanja bafera na disk, ali mana je to što je proces oporavka sporiji. Ako se predpostavi da su iznenadna gašenja sistema retka, prednosti ovog algoritma pobeđuju ostale algoritme.

===== _Undo only recovery_ <undo_only_recovery>

_Undo only recovery_ algoritam radi samo fazu poništavanja jer je siguran da su svi baferi _commited_ transakcija već zapisani na disku. Ovo se postiže modifikaicjom _commit_ algoritma tako da se prvo upisuje sadržaj bafera na disk pre pisanja _commit_ _log_-a. Glavna prednost ovog algoritma je brzina, jer se kroz _log_ fajl prolazi samo jednom, ali mana je to što _commit_ operacija postaje mnogo sporija.

===== _Redo only recovery_

_Redo only recovery_ algoritam radi samo fazu ponovne primene jer je siguran da baferi transakcija koje nisu zavšile sigurno nisu zapisani na disk. Ovo se postiže modifikacijom transakcija tako da su baferi u njihobvim listama pinovani dokle god se transakcija ne završi. Glavna prednost ovog algoritma je brzina jer se kroz _log_ fajl prolazi samo jednom, ali mana je to što baferi ostaju pinovani mnogo duže, drastično usporavajući sistem. 

===== Mirna kontrolna tačka

Što se sistem duže koristi, to će njegov _log_ fajl postajati obimniji jer uvek sadrži kompletnu istoriju izmena podataka. Prolazak kroz ceo _log_ fajl na prilikom svakog pokretanja sistema može trošiti nepotrebno mnogo resursa, a u jednom trenutku će i biti nemoguće.

Trenutak kada algoritam oporavka ne mora da čita _log_ fajl dublje se može okarakterisati sa dve osobine: #todo("citirati simpledb")
- svi prethodni _log_ zapisi su napisani od završenih transakcija (_undo_ faza algoritma)
- baferi tih transakcija su napisani na disk (_redo_ faza algoritma)

Kontrolna tačka predstavlja zapis u _log_ fajlu posle kojeg se ne mora čitati dalje jer je sistem provereno potvrdio da obe osobine važe. Sistem trivijalno ovo može da obezbedi nakon što je završio sa oporavkom, a algoritam koji ovo obezbeđuje u ostalim situacijama se može pronaći #link(<quiescent_alg>)[ovde]. LBDB implementira samo logiku za mirnu kontrolnu tačku (eng. _quiescent checkpoint_), ali postoji i nemirna kontrolna tačka (eng _nonquiescent checkpoint_).

Nakon oporavka, umesto pisanja kontrolne tačke, sistem radi arhiviranje _log_ datoteke. Arhiviranje je ekvivalentna operacija, a omogućava preglednije održavanje sistema.

==== Algoritam poništavanje transakcije

Sistem radi poništavanje transakcije tako što prolazi kroz _log_ datoteku od kraja ka početku i poništava svaku modifikacionu operaciju te transakcije, a staje sa prolaskom _log_ datoteke kada naiđe na operaciju koja označava početak te transakcije.

=== Bezbedan višenitni pristup <bezbedan_visenitni_pristup>

Sistem za bezbedan višenitni pristup je podsistem u okviru transakcija koji sadrži drugi deo logike zbog kojeg se sav pristup vrednostima radi kroz transakcije. Primarno se brine o rešavanju konflikta konkurentnog pristupa istim blokovima. Obuhvata _I_ (eng. _isolation_) osobinu.

Priroda višenitnih pristupa uvodi nedeterminističan redosled operacija koji implicira konflikte. Konflikt je kada rezultat dve operacije zavisi od njihovog redosleda izvršavanja. Postoje dve vrste konflikta: _write-write_ konflikt i _read-write_ konflikt. Kod _write-write_ konflikta, jedna operacija modifikuje neku vrednost, pa druga modifikuje istu vrednost. Kod _read-write_ konflikta, jedna operacija čita neku vrednost, a druga modifikuje istu tu vrednost. Konflikti ne mogu da se dese kod _read-read_ operacija ili ako operacije rade nad vrednostima koje se nalaze u različitim blokovima. #todo("citirati simpledb")

Sprečavanje konfliktujućih operacija u sistemu se postiže preko sistema katanaca, koji omogućavaju korektan redosled pristupa vrednostima za čitanje i pisanje. Protokol zaključavanja u LBDB sistemu definiše sledeća pravila rada sa katancima:
- pre čitanja vrednosti iz bloka, potrebno je steći _deljeni_ katanac za taj blok,
- pre pisanja vrednosti u blok, potrebno je steći _ekskluzivni_ katanac za taj blok,
- nakon završetka transakcije, potrebno je pustiti sve katance za sve blokove koje je ta transakcija zaključala.

Poštovanje ovakvog protokola zaključavanja uvek garantuje tačnost rada sa vrednostima, ali znatno smanjuje konkurentnost sistema. Povećanje konkurentnosti sistema se radi izborom izolacionog nivoa individualnih transakcija. Izolacioni nivoi transakcija povećavaju konkurentnost ali žrtvuju tačnost pročitanih vrednosti tako što upravljaju životni ciklus _deljenih_ katanca drugačije. #todo("citirati simpledb")

#figure(
  {
    set par(justify: false)
    table(
      columns: (1.3fr, 1.2fr, 1fr, 0.65fr),
      align: (center, center),
      inset: 8pt,
      [Izolacioni nivo], [Problemi], [Puštanje _deljenih_ katanaca], [_EOF_ marker],
      //
      [Serijalizujući\ (eng. _serializable_)],
      [ne postoje],
      [_deljeni_ katanci se drže do završetka transakcije],
      [postoji _deljeni_ katanac],
      //
      [Ponovno čitanje\ (eng. _repeatable read_)],
      [fantomske vrednosti],
      [_deljeni_ katanci se drže do završetka transakcije],
      [ne postoji _deljeni_ katanac],
      //
      [Čitanje samo potvrđenih vrednosti\ (eng. _read committed_)],
      [fantomske vrednosti,\ promenjene vrednosti],
      [_deljeni_ katanci se puštaju odmah nakon čitanja],
      [ne postoji _deljeni_ katanac],
      //
      [Čitanje i nepotvrđenih vrednosti\ (eng. _read uncommitted_)],
      [fantomske vrednosti,\ promenjene vrednosti,\ "prljava" čitanja],
      [ne koriste se _deljeni_ katanci],
      [ne postoji _deljeni_ katanac],
    )
  },
  caption: [Različiti izolacioni nivoi transakcija],
)<tbl:izolacioni_nivoi>

Različiti izolacioni nivoi se mogu implementirati i pomoću _MVCC_ šablona (eng. _multi version concurrency control_), ali on nije podržan u okviru LBDB sistema. #todo("citirati MVCC")

Izolacioni nivoi transakcija su koncipirani tako da svaki nivo izolacije rešava sve probleme nivoa ispod njega.

- Problem "prljavog" čitanja je kada transakcija _A_ može da čita vrednosti koja je transakcija _B_ izmenila, pre nego što je transakcija _B_ završila.
- Problem promenjivih vrednosti je kada transakcija _A_ pročita neku vrednost, transakcija _B_ je modifikuje, transakcija _A_ ponovo pročita istu vrednost i dobije vrednost koju je transakcija _B_ modifikovala umesto vrednosti koju je transakcija _A_ prvobitno pročitala.
- Problem fantomskih vrednosti je kada transakcija _A_ pročita sve vrednosti nekog blokovskog opsega, transakcija _B_ doda novu vrednost i proširi taj blokovski opseg sa novim blokom, i umesto da transakcija _A_ pri ponovnom čitanju svih vrednosti dobije istu listu prvobitnih vrednosti, dobije i novu vrednost iz novog bloka koju je transakcija _B_ dodala.

Pošto je nivo granularnosti zaključavanja na nivou jednog bloka, fantomska čitanja se mogu desiti samo ako se na kraju neke datoteke doda nov blok. Sprečavanje ovoga se dešava specijalnim _deljenim_ katancem nad _EOF_ markerom. _EOF_ marker glumi blok koji tek treba da se doda na kraju neke datoteke, ali koji fizički ne postoji u datoteci.

Pomenuti izolacioni nivoa transakcija se odnose samo na operacije koje čitaju vrednosti. Operacije koje modifikuju vrednosti uvek moraju poštovati korektno dobijanje _ekskluzivnih_ katanaca. Transakcije na individualnom nivou mogu tolerisati neprecizne podatke, ali kada bi se dobijanje _ekskluzivnih_ zaobišlo, cela baza podataka bi postala neupotrebljiva.

Podsistem bezbednog višenitnog pristupa LBDB sistema implementira samo serijalizujući izolacioni nivo transakcija.

==== Katalog katanaca

Instanca klase `LockTable` je deljena za sve transakcije u sistemu. U njoj se realizuju mehanizmi praćenja postojanja katanaca za sve blokove.

_Deljeni_ katanac za neki blok je moguće steći bez obzira na postojanje drugih deljenih katanaca, ali _ekskluzivni_ katanac za neki blok je moguće steći samo kada ne postoji ni jedan drugi katanac bilo koje vrste.

Ako se desi da transakcija nije uspela da zaključa željeni blok (bilo sa _deljenim_ ili _ekskluzivnim_ katancem) posle određenog vremenskog perioda, se vraća greška klijentu.

==== Upravljanje katancima na nivou individualnih transakcija

Menadžer konkurentnosti (`ConcurrencyManager` klasa) sadrži skup jednostavnih algoritama koji interaguju sa katalogom katanca da bi transakcijama na individualnom nivou omogućili upravljanje katancima. Svaka transakcija ima svoj menadžer konkurentnosti.

== Transakcije i klijenti <sesije>

Pošto svaka operacija u sistemu mora biti izvršena u okviru neke transakcije, klijentima LBDB sistema je potrebno dodeliti korektne transakcione objekte. Menadžer transakcija upravlja životnim ciklusom transakcija i vezuje ih za klijente. Menadžer transakcija je jedan od tri glavna podsistema LBDB sistema #link(<sistem_za_obradu_upita>)[obrade upita].

Nova transakcija počinje čim se prethodna transakcija za koju je klijent vezan završi. Prva transakcija se dodeljuje klijentu prilikom njegovog povezivanja na sistem #todo("citirati server"). Podrazumevani režim završetaka transakcija je automatsko završavanje (eng. _autocommit_) u kom se svaka transakcija sastoji od tačno jedne operacije. Drugi režim završetaka transakcija je manuelni režim, u kom se transakcija može sastojati od više operacija, i u tom slučaju kraj transakcije označava operacija potvrde ili poništavanja koja se dobija od klijenta.

Da bi se postiglo perzistiranje istog transakcionog objekta kroz više operacija, potrebno je pratiti sve aktuelne transakcione objekte u sistemu i unikatno definisati svakog klijenta pa uvek izvršavati operacije u okviru njegovog transakcionog objekta dokle god transakciji ne dođe kraj. Unikatni identifikator klijenta predstavlja njegova sesija. Na apstrakcionom nivou upravljanja transakcijama, sesija nije ništa više nego broj, ali na višim slojevima je potrebno generisati tu sesiju korektno. Detalji generisanja sesije se mogu pronaći ovde. #todo("citirati server")

=== Algoritam zapisa mirne kontrolne tačke <quiescent_alg>

Da bi mirna kontrolna tačka bila zapisana, ni jedna transakcija ne sme biti aktivna u sistemu, a ovo je evidentno zbog prve osobine mirne kontrolne tačke. Korektan zapis mirne kontrolne tačke se u okviru menadžera transakcija izvršava sledećim algoritmom: #todo("citirati simpledb")
- prestaje da prihvata nove transakcije od klijenata
- čeka da svi klijenti završe sa svojom transakcijom, bilo ona manuelna ili automatska
- zapisuje sve bafere na disk
- dodaje zapis mirne kontrolne tačke u _log_ datoteku
- ponovo prihvata nove transakcije od klijenata

Menadžer transakcija je jedini koji je svestan činjenice da li su sve transakcije završene, jer ih prati, pa je algoritam zapisa mirne kontrolne tačke implementiran u okviru njega.