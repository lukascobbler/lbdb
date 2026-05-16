#import "../funkcije.typ": todo

= Metapodaci <metapodaci>

Metapodaci su podaci koji opisuju druge podatke. Iako su podaci struktuirani u okviru slogova (glava @datoteke), sistem im ne može pristupiti ako se ne pobrine o perzistiranju te strukture. Praćenje distribucije vrednosti je korisno prilikom pravljenja efikasnog načina dobavljanja slogova. Podaci koji definišu strukturu slogova i podaci o distribuciji vrednosti su primeri metapodataka kojima sistem barata.

== Kataloške tabele <kataloske-tabele>

Metapodatake koje sistem čuva da bi omogućio rad sa tabelama su podaci o postojećim tabelama i kako kolone tih tabela izgledaju. Ti podaci se čuvaju u okviru sistemskih tabela i one se nazivaju kataloške tabele. Kataloška tabela _tablecatalog_ čuva podatke o postojećim tabelama, dok kataloška tabela _fieldcatalog_ čuva podatke o fizičkoj strukturi sloga neke tabele. Vrednosti ovih tabela su pohranjene iz #link(<raspored_polja>)[rasporeda polja] i #link(<sema>)[šeme] koju on sadrži.

#figure(
  {
    set par(justify: false)
    table(
      columns: (0.5fr, 0.3fr, 1.3fr),
      align: (center, center),
      inset: 8pt,
      [Kolona], [Tip], [Opis],
      //
      [_tableid_], [_Integer_], [unikatni identifikator tabele],
      //
      [_tablename_], [_String_], [ime tabele],
      //
      [_slotsize_], [_Integer_], [veličina jednog sloga te tabele u bajtovima],
    )
  },
  caption: [Šema _tablecatalog_ tabele],
)<tbl:tablecatalog>

#figure(
  {
    set par(justify: false)
    table(
      columns: (0.5fr, 0.3fr, 1.3fr),
      align: (center, center),
      inset: 8pt,
      [Kolona], [Tip], [Opis],
      //
      [_type_], [_Integer_], [tip kolone zapisan kao numerička vrednost],
      //
      [_runtimelength_], [_Integer_], [maksimalna fizička dužina vrednosti ovog polja],
      //
      [_offset_], [_Integer_], [pozicija vrednosti te kolone],
      //
      [_tableid_], [_Integer_], [unikatni identifikator tabele u kojoj se kolona nalazi],
      //
      [_fieldname_], [_String_], [ime kolone],
      //
      [_nullable_], [_Boolean_], [da li vrednosti kolone mogu biti _NULL_ vrednost],
    )
  },
  caption: [Šema _fieldcatalog_ tabele],
)<tbl:fieldcatalog>

Kataloške tabele se kreiraju prilikom inicijalizacije sistema. Bitno je napomenuti da se kataloške tabele perzistiraju na isti način kao i sve ostale tabele u sistemu, što znači da će one sadržati i slogove koje opisuju njih sâme. Time što se kataloške tabele perzistiraju isto kao i korisničke, sistemskim tabelama se može pristupiti putem standardnih mehanizama #link(<relacioni-operatori>)[relacionih operatora].

Svi identifikatori u sistemu (imena kolona, tabela, ...) se implicitno konvertuju tako da sadrže samo mala slova.

== Statistički podaci <statisticki-metapodaci>

Pristup istim slogovima tabela se često može izvršiti na više različitih načina, ali neki načini mogu biti znatno manje efikasni od ostalih. Apstrakcioni nivo upravljanja metapodacima je dužan da obezbedi statističke metapodatke koji pomažu pri proceni vremena izvršavanja određenih načina pristupa. Sâm posao konstruisanja efikasnog načina pristupa je briga #link(<planiranje>)[podsistema planiranja].

Statistički metapodaci neke tabele uključuju:
- broj blokova tabele
- broj slogova u tabeli
- broj različitih vrednosti kolona tabele
- broj _NULL_ vrednosti kolona tabele

=== Računanje statističkih podataka <racunanje-statistike>

Prilikom inicijalizacije sistema, računaju se statistički metapodaci za svaku tabelu u sistemu, a svakih 100 poziva dobavljanja metapodataka za bilo koju tabelu se osvežavaju statistički metapodaci za sve tabele. Ovaj način osvežavanja nije idealan jer pauzira sistem dok se računanje statističkih metapodataka ne završi. #todo("citirati zakljucak za limitaciju sistema racunanja statistickih podataka")

Broj blokova tabele i broj slogova u tabeli se trivijalno dobijaju iteracijom kroz svaki slog.

Broj različitih vrednosti kolone tabele nije moguće izračunati precizno, jer je za to potrebno čuvanje svih jedinstvenih vrednosti te kolone u radnoj memoriji. Male nepreciznosti neće uticati na procenu vremena izvršavanja operacija, pa je iskorištena probabilistička struktura podataka _HyperLogLog_ @hll koja rešava _count distinct_ problem i ona ne čuva sve jedinstvene vrednosti u radnoj memoriji. Jedna takva struktura se dodeljuje za svaku kolonu.

Brojanje _NULL_ vrednosti kolona tabele se svodi na čuvanje prostog brojača za svaku kolonu.

== Pristup metapodacima <metadata-menadzer>

Menadžer metapodataka je glavno mesto pristupa svim ostalim metapodacima. Sastoji se iz menadžera metapodataka tabela i menadžera statističkih metapodataka. Menadžer metapodataka je jedan od tri glavna podsistema LBDB sistema #link(<sistem_za_obradu_upita>)[obrade upita].
