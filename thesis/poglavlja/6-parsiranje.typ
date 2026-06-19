#import "../funkcije.typ": todo

= Parsiranje <parsiranje>

Klijenti šalju _SQL_ naredbe u tekstualnom formatu, ali sistemu komad teksta nema nikakvo inherentno značenje. Podsistem za ekstrakciju informacija iz teksta naredbe se naziva parser.

Nije svaki komad teksta validna _SQL_ naredba, ali njegova validnost se može podeliti na dva sloja @simpledb:
- sintaksička validnost, gde sintaksa predstavlja skup pravila koja definišu moguće operacije po nekoj gramatici,
- semantička validnost, koja je ispunjena ako je neka operacija validna u kontekstu podataka koje koristi (imena tabela, imena kolona, ...)

Parsiranje konstruiše apstraktno sintaksičko stablo (eng. _abstract syntax tree_, _AST_) koje se mapira na podržane operacije i služi za proveru *samo* sintaksičke validnosti operacije. Provera semantičke validnosti je deo planera (sekcija @planer).

== Tokenizator

Blokovi teksta se sastoje od individualnih karaktera. Većina individualnih karaktera sadrži jako malo značenja kada se obrađuju nezavisno i zbog toga se uvodi sistem koji grupiše povezane karaktere. Skup grupisanih karaktera koji zajedno imaju visok stepen značenja se zovu tokeni. Karakteri koji se obrađuju sami su isto opisani kao tokeni, jer je bitno da je svaki token imenovan.

`Tokenizer` klasa sadrži logiku pretvaranja bloka teksta u tokene odgovarajućeg tipa i implementirana je preko _Java_ `Iterator` interfejsa. `Token` je definisan _sealed interface_ _Java_ konstruktom, zbog njegove odlične kompatibilnosti sa `switch` sintaksom. Svi tokeni u sistemu su grupisani u sledeće kategorije:
- ključne reči _SQL_ jezika,
- identifikatori,
- simboli,
- brojevi,
- _String_-ovi,
- nevalidni karakteri,
- `EOF` token.
Svaka kategorija tokena implementira `Token` interfejs i predstavljena je _record_ ili _enum_ _Java_ strukturom.

== Parser

Gramatika nekog jezika predstavlja skup pravila koje opisuju sve legalne komade teksta, koje neki sistem podržava. Sintaksičke kategorije su koncepti sa kojima gramatika barata. Predstavljaju čvorove sintakstičkog stabla i sadrže se od drugih sintaksičkih kategorija i tokena.

Vrsta parsiranja koja je implementirana se zove _recursive descent_ parsiranje. U _recursive descent_ parsiranju, gramatika se proverava od gore ka dole. Ulazna tačka je koren sintaksičkog stabla i za svako podstablo, to jest gramatičko pravilo, postoji funkcija koja obrađuje to pravilo. Funkcije se često pozivaju rekurzivno da bi obradili veće celine, pa je tako ovaj način parsiranja i dobio ime. Svaka funkcija obrade pravila, na bilo kom nivou, se mapira na jednu sintaksičku kategoriju.

=== Iskazi <statement>

Uspešno parsiranje nekog bloka teksta koji predstavlja _SQL_ operaciju rezultuje u iskazu, koji sadrži sve neophodne podatke da se ta operacija izvrši. Iskaz (`Statement` klasa) je definisan _sealed interface_ _Java_ konstruktom, zbog njegove odlične kompatibilnosti sa `switch` sintaksom. Svaki iskaz je predstavljen _Java_ _record_ strukturom i nasleđuje `Statement`.

=== Sintaksičke kategorije _SQL_ operacija

Funkcije koje generišu iskaze predstavljaju sintaksičke kategorije najvišeg apstrakcionog nivoa i grupisane su u različite _Java_ datoteke. Svaka datoteka sadrži sve sintaksičke kategorije nižeg apstrakcionog nivoa potrebne da se iskaz uspešno obradi.

==== `Parse`

#figure(
  image("../dijagrami/parsiranje/parse.svg", height: 26%),
  caption: [
    Gramatika `Parse` sintaksičke kategorije
  ],
)<fig:parse>

`Parse` predstavlja glavnu sintatičku kategoriju i grupiše sve ostale sintaksičke kategorije. Omogućava i `EXPLAIN` naredbu, koja generiše opis naredbe koja će se izvršiti. Iskazi upravljanja životnim ciklusima transakcija se isto parsiraju ovde jer su previše jednostavni da bi se pravila posebna sintaksička kategorija za njih. Vraća `Statement` objekat.

==== `ParseUpdate`

#figure(
  image("../dijagrami/parsiranje/parse_update.svg"),
  caption: [
    Gramatika `ParseUpdate` sintaksičke kategorije
  ],
)<fig:parse_update>

`ParseUpdate` predstavlja naredbu modifikovanja podataka neke tabele. Može sadržati proizvoljan broj novih dodela vrednosti, ali svako polje u svim dodelama vrednosti mora postojati u referenciranoj tabeli. Moguće je modifikovati samo neke slogove, a ne sve, tako što se definiše uslov pretrage. Vraća `UpdateStatement` objekat.

==== `ParseSelect` <parse_select>

#figure(
  image("../dijagrami/parsiranje/parse_select.svg", height: 52%),
  caption: [
    Gramatika `ParseSelect` sintaksičke kategorije
  ],
)<fig:parse_select>

`ParseSelect` predstavlja naredbu upita (eng. _query_) podataka. Može sadržati proizvoljan broj projektovanih kolona, gde je svaka kolona predstavljena kao bilo šta što može da se evaluira i kojoj se može dodeliti novo ime (uz `AS` ključnu reč). Podržava filtriranje na osnovu uslova pretrage. Upit može biti nad pravim tabelama ili nad virtuelnom tabelom koja sadrži jedan slog. Ulančavanje tabela se može raditi na dva načina: samo navođenje tabela odvojene zarezom ili preko `JOIN` ključne reči gde se uslov ulančavanja upisuje odmah. Uslov ulančavanja napisan u `JOIN` sekciji se samo dodaje na uslov pretrage, umesto da predstavlja neki specijalan način ulančavanja. Vraća `SelectStatement` objekat.

==== `ParseInsert`

#figure(
  image("../dijagrami/parsiranje/parse_insert.svg", height: 32%),
  caption: [
    Gramatika `ParseInsert` sintaksičke kategorije
  ],
)<fig:parse_insert>

`ParseInsert` predstavlja naredbu umetanja novih slogova u neku tabelu. Lista polja ne mora biti definisana, uzima se podrazumevani redosled koji je napravljen tokom kreiranja te tabele. Izrazi moraju biti konstantni, to jest njihova evaluacija ne sme zavisiti od vrednosti koje ne mogu da se izračunaju bez pristupa tabelama. Vraća `InsertStatement` objekat.

==== `ParseDelete`

#figure(
  image("../dijagrami/parsiranje/parse_delete.svg", height: 10%),
  caption: [
    Gramatika `ParseDelete` sintaksičke kategorije
  ],
)<fig:parse_delete>

`ParseDelete` predstavlja naredbu brisanja slogova iz neke tabele. Moguće je obrisati samo slogove koji ispunjavaju neki uslov, tako što se definiše uslov pretrage. Vraća `DeleteStatement` objekat.

==== `ParseCreateTable`

#figure(
  image("../dijagrami/parsiranje/parse_create_table.svg", height: 27%),
  caption: [
    Gramatika `ParseCreateTable` sintaksičke kategorije
  ],
)<fig:parse_create_table>

`ParseCreateTable` predstavlja naredbu kreiranja nove tabele. Tabela može sadržati maksimalno 31 polje (sekcija @primena_strukture_na_blok). Polje može biti jedno od tipova podržanih u sistemu (figura @fig:tip), a za _String_ (_VARCHAR_) tip se mora definisati i maksimalna dužina, koja mora biti konstantan izraz. Ograničenje da slogovi za neku kolonu ne smeju imati _NULL_ vrednosti je opciono i definiše se nakon tipa kolone. Vraća `CreateTableStatement` objekat.

==== `ParsePredicate`

#figure(
  image("../dijagrami/parsiranje/parse_predicate.svg", height: 34%),
  caption: [
    Gramatika `ParsePredicate` sintaksičke kategorije
  ],
)<fig:parse_predicate>

`ParsePredicate` je specijalna vrsta sintaksičke kategorije koja ne proizvodi iskaz, već služi za kreiranje sintaksičkih stabala predikata. Parsiraju se izrazi i operacije poređenja od kojih se članovi sastoje, a zatim se ulančavaju članovi da bi se formirao predikat. Vraća `Predicate` objekat. Ne podržava članove bez operatora poređenja.

==== `ParseExpression` <parsiranje_izraza>

#figure(
  image("../dijagrami/parsiranje/parse_expression.svg", height: 94.5%),
  caption: [
    Gramatika `ParseExpression` sintaksičke kategorije
  ],
)<fig:parse_expression>

`ParseExpression` je specijalna vrsta sintaksičke kategorije koja ne proizvodi iskaz, već služi za kreiranje sintaksičkih stabala izraza. Parsiranje izraza je urađeno specijalnom tehnikom _recursive descent_ parsiranja koja se zove _Pratt parsing_ @pratt_parsing. _Pratt parsing_ definiše tehnike obrade prioriteta operacija, zagrada, prepoznavanja identifikatora i zamenskih članova. Vraća `Expression` objekat.

Prvo se parsira prefiksni izraz, koji može biti literal različitog tipa, identifikator, zamenski član, izraz sa unarnom operacijom ili izraz u zagradama. Rezultat ovog koraka postaje početni levi operand. Zatim se ulazi u petlju koja se izvršava sve dok je prioritet sledećeg (infiksnog, $+$, $-$, $*$, $\/$, $\^$) operatora strogo veći od trenutnog prioriteta.

Unutar petlje, operator se konzumira, a desni operand se dobija rekurzivnim pozivom funkcije za parsiranje izraza kojoj se prosleđuje prioritet tog novog operatora (ili prioritet umanjen za jedan, ukoliko je operacija desno-asocijativna, poput stepenovanja). Od levog operanda, operatora i desnog operanda kreira se novo stablo binarnog izraza, koje zatim postaje novi levi operand za narednu iteraciju petlje.

U isečku koda ispod se mogu videti različiti prioriteti operacija na osnovu tokena koji ih opisuju. Token `STAR` predstavlja i operator zamenskog člana, pa se zove `STAR` umesto `MULTIPLY`. Prefiksne operacije imaju najveći prioritet.

#figure(
  ```java
  private static final int PREFIX_PRECEDENCE = 100;

  private int getPrecedence(Token opToken) {
      return switch (opToken) {
          case SymbolToken.CARET -> 30;
          case SymbolToken.STAR, SymbolToken.DIVIDE -> 20;
          case SymbolToken.PLUS, SymbolToken.MINUS -> 10;
          default -> 0;
      };
  }
  ```,
  caption: [
    Prioriteti aritmetičkih operacija u sistemu
  ],
)<fig:prioriteti_pratt>

==== `ParseEvaluatable`

#figure(
  image("../dijagrami/parsiranje/parse_evaluatable.svg", height: 46%),
  caption: [
    Gramatika `ParseEvaluatable` sintaksičke kategorije
  ],
)<fig:parse_evaluatable>

`ParseEvaluatable` je specijalna vrsta sintaksičke kategorije koja ne proizvodi iskaz, već služi za agnostično kreiranje objekata koji se mogu evaluirati na vrednost. Liči na `ParsePredicate`, ali sa dodatnom mogućnošću da prepozna kada izraz nije deo člana ili predikata. Vraća objekat koji implementira `Evaluatable` interfejs (sekcija @evaluatable-interfejs). Ne podržava članove bez operatora poređenja.
