#let format_strane = "iso-b5" // iso-b5 ili a4
#let naslov = "Управљање датотекама, трансакцијама и интерпретација упита у релационим базама података"
#let autor = "Лука Бурсаћ"

#let naslov_eng = "Management of files, transactions and query interpretation in relational databases"
#let autor_eng = "Luka Bursać"

#let indeks = "SV 22/2022"

#let mentor = "Бранко Милосављевић"
#let mentor_zvanje = "редовни професор"

#let studijski_program = "Софтверско инжењерство и информационе технологије"
#let stepen = "Основне академске студије"

#let godina = [#datetime.today().year()]

#let kljucne_reci = "Релационе базе података, трансакције, датотеке, слогови, упити"
#let apstrakt = [
  Имплементација једног система за управљање релационим
  базама података у програмском језику _Java_
]

#let kljucne_reci_eng = "Relational databases, transactions, files, records, queries"
#let apstrakt_eng = [
  Implementation of a system for relational
  database management written in _Java_
]

#let zadatak = [
  Пројектовати и имплементирати систем за управљање релационим базама података у програмском језику _Java_. Систем треба да обухвати управљање датотекама и блоковима на диску, баферовање страница у радној меморији, трансакционе механизме са гарантовањем _ACID_ особина, управљање метаподацима, као и парсирање, планирање и извршавање подскупа _SQL_ језика кроз стабло релационих оператора. Систем реализовати у клијентско-серверској архитектури са сопственим протоколом за мрежну комуникацију. Исправност решења проверити одговарајућим скупом аутоматизованих тестова.
]

#let datum_odbrane = "17.07.2026"
#let komisija_predsednik = "Горан Сладић"
#let komisija_predsednik_zvanje = "редовни професор"
#let komisija_clan = "Милан Стојков"
#let komisija_clan_zvanje = "доцент"

#let komisija_predsednik_eng = "Goran Sladić"
#let komisija_clan_eng = "Milan Stojkov"
#let mentor_eng = "Branko Milosavljević"

#let zvanje_eng = (
  "редовни професор": "full professor",
  "ванредни професор": "assoc. professor",
  "доцент": "asist. professor",
)
#let komisija_predsednik_zvanje_eng = zvanje_eng.at(komisija_predsednik_zvanje)
#let komisija_clan_zvanje_eng = zvanje_eng.at(komisija_clan_zvanje)
#let mentor_zvanje_eng = zvanje_eng.at(mentor_zvanje)


#let vrsta_rada = if stepen == "Мастер академске студије" {
  "Дипломски - мастер рад"
} else {
  "Дипломски - бечелор рад"
}

#let oblast = "Електротехничко и рачунарско инжењерство"
#let oblast_eng = "Electrical and Computer Engineering"
#let disciplina = "Примењене рачунарске науке и информатика"
#let disciplina_eng = "Applied computer science and informatics"

#import "funkcije.typ": *
#let fizicki_opis = physical()
