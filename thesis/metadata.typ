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

// TODO: Текст задатка добијате од ментора. Заменити доле #lorem(100) са текстом задатка.
#let zadatak = [
  #lorem(100)
]

// TODO: Датум одбране и чланове комисије добијате од ментора
#let datum_odbrane = "01.01.2025"
#let komisija_predsednik = "Петар Петровић"
#let komisija_predsednik_zvanje = "ванредни професор"
#let komisija_clan = "Марко Марковић"
#let komisija_clan_zvanje = "доцент"

// На енглеском уписати чланове на латиници
#let komisija_predsednik_eng = "Petar Petrović"
#let komisija_clan_eng = "Marko Marković"
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
