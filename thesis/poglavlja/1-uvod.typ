#import "../funkcije.typ": todo

= Uvod <uvod>

== O sistemu

LBDB je višekorisniški sistem sa transakcijama za upravljanje relacionim bazama podataka. Njegova svrha je da prima komande dobijene od klijenata, intepretira ih po standardu SQL
programskog jezika i da vrati rezultat operacije tim klijentima. Rezultat može biti broj pogođenih redova za slučaj modifikacionih operacija ili rezultujuća tabela za slučaj upita. Zarad efikasnog interpretiranja, potrebno je obezbediti algoritme i strukture podataka za sledeće module: upravljanje datotekama, rad sa transakcijama, rad sa metapodacima, parsiranje upita, pravljenje planova izvršavanja upita, izvršavanje upita i za mrežnu komunikaciju.
Moduli ovog sistema su raspoređeni tako da se svaki brine o jednoj grupi algoritama i struktura podataka kroz koju upit prolazi.

#figure(
  image("../dijagrami/uprosceni_sistem.pdf", height: 54%),
  caption: [
    Uprošćena arhitektura sistema
  ],
)<fig:arh_sistema>

Svaka komponenta iz uprošćene arhitekture sistema je posebno detaljno objašnjena u nastavku, a uz te komponente se dodatno objašnjavaju i transakcije, koje su isprepletene kroz ceo sistem. Sličan dijagram koji opisuje celu arhitekturu sistema, ali mnogo detaljnije, se može pronaći ovde. #todo("citirati detaljan dijagram u pregledu sistema")

Osnovna struktura i algoritmi su izvedeni iz knjige _Database Design And Implementation_ #todo("citirati simpledb"), a njihova unapređenja su deo ovog rada. Knjiga definiše zadatke na kraju svakog modula i ti zadaci su osnova za unapređivanje sistema. Detaljan spisak urađenih zadataka i njihovih beleški se može pronaći u okviru repozitorijuma #todo("citirati repozitorijum").

== Klijentsko serverska arhitektura

`LBDBServer` definiše ulaznu tačku servera. Zahteva port na kome će se server pokrenuti i direktorijum baze podataka. Obrađuje klijente koji se povezuju na njega preko `LBDBClient` klijentske ulazne tačke.

== Sistem za obradu upita <sistem_za_obradu_upita>

`LBDB` klasa predstavlja najviši apstrakcioni nivo sistema obrade upita na koji se server oslanja. Služi za orkestraciju glavnih podsistema: menadžer metapodataka, menadžer tranzakcija i planer. Takođe, klasa `LBDB` je odgovorna za inicijalizaciju i oporavljanje sistema od neočekivanog gašenja, za određeni direktorijum baze podataka. #todo("citirati podsisteme")
