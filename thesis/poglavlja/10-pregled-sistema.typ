#import "../funkcije.typ": todo

= Pregled sistema <pregled-sistema>

...

== Pokretanje sistema

#todo("objasniti build sistem sa odvojenim jarovima")

#todo("objasniti pokretanje testova")

== Rukovođenje zavisnostima <rukovodjenje-zavisnostima>

#todo("objasniti da sistem koristi maven, opisati ukratko sve zavisnosti")

=== Zavisnosti servera

=== Zavisnosti klijenta

== Sistemska konfiguracija

U okviru sistema postoji i konfiguraciona klasa `LBDBSettings` preko koje je moguće postaviti parametre izbora algoritama ili vrednosti za određene operacije. Podrazumevane vrednosti su dobar izbor za nenadgledanu inicijalizaciju sistema i mogu se videti u sledećem bloku koda:

#figure(
  ```java
  public class LBDBSettings {
      public boolean UNDO_ONLY_RECOVERY = true;
      public int BLOCK_SIZE = 4096;
      public int BUFFER_POOL_SIZE = 128;
      public BufferStrategy bufferStrategy = BufferStrategy.LRU;
      public String LOG_FILE = "log_file";
      public QueryPlannerType queryPlannerType = QueryPlannerType.BETTER;
      public UpdatePlannerType updatePlannerType = UpdatePlannerType.BASIC;
  }
  ```,
  caption: [
    Kod sistemske klase `LBDBSettings`
  ],
)<fig:lbdbsettings>

Redom, parametri označavaju:
1. #link(<alg_oporavka>)[algoritam oporavke] sistema,
2. veličina jednog bloka u bajtovima gde jedan blok predstavlja najmanju jedinicu interakcije sa diskom,
3. količina bafera sa kojim sistem raspolaže,
4. strategija izbora bafera koji će biti smenjen,
5. putanja do datoteke gde se čuvaju podaci potrebni za oporavak sistema i poništavanje transakcija,
6. implementacija planera za operacije upita; podržana samo `BETTER` implementacija,
7. implementacija planera za operacije modifikacije; podržana samo `BASIC` implementacija.

== Pokretanje testova na _GitHub_ platformi

#todo("implementiran je CI koji pokreće testove na linux i windows masinama koristeci in memory file sistem")

== Primer funkcionisanja celokupnog sistema

#todo("napraviti dijagram sekvence koji se brine o tome sta zove sta, za ceo sistem")
