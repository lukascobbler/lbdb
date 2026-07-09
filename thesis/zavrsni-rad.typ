#import "metadata.typ": *
#set page(paper: format_strane, margin: (y: 2.5cm, inside: 2cm, outside: 1.5cm))
#include "naslovna.typ"
#pagebreak()
#pagebreak()
#include "zadatak.typ"
#pagebreak()
#pagebreak()
#include "kljucna.typ"
#pagebreak()
#pagebreak()
#include "sukob-interesa.typ"
#pagebreak()
#pagebreak()
#include "posveta.typ"
#pagebreak()
#pagebreak()

#set text(lang: "sr")

#set document(title: naslov, author: autor)
#set heading(numbering: "1.1")
#set text(font: "Liberation Serif", size: 11pt)
#set par(justify: true)
#show link: set text(blue)
#show cite: set text(blue)
#show ref: set text(blue)
#show heading: set text(hyphenate: false)

#show figure.where(
  kind: table,
): set figure.caption(position: top)
#show figure.where(kind: raw): set figure(supplement: [Листинг])
#show figure.where(kind: image): set figure(supplement: [Слика])
#show figure.where(kind: table): set figure(supplement: [Табела])
#set ref(supplement: none)

#import "@preview/hydra:0.6.2": hydra

#show heading.where(level: 1): it => {
  pagebreak(to: "odd", weak: true)
  set block(spacing: 8pt)
  if heading.numbering != none {
    text("Глава " + counter(heading).display(), size: 22pt)
  }
  set par(justify: false)
  line(length: 100%)
  rect(align(right + horizon, text(it.body, size: 22pt)), fill: white, width: 100%)
  line(length: 100%)
  v(1em)
}

#outline(title: [Садржај], depth: 4)

#set page(header: context {
  if not (query(heading.where(level: 1)).any(h => h.location().page() == here().page())) {
    if calc.odd(here().page()) {
      align(right, emph(hydra(1)))
    } else {
      align(left, emph(hydra(2)))
    }
    line(length: 100%)
  }
})

#pagebreak(to: "odd", weak: false)
#set heading(numbering: "1.1")
#set page(numbering: "1")
#counter(page).update(1)

#include "poglavlja/1-uvod.typ"
#include "poglavlja/2-datoteke.typ"
#include "poglavlja/3-transakcije.typ"
#include "poglavlja/4-metapodaci.typ"
#include "poglavlja/5-relacioni-operatori.typ"
#include "poglavlja/6-parsiranje.typ"
#include "poglavlja/7-planiranje.typ"
#include "poglavlja/8-klijent-server.typ"
#include "poglavlja/9-testovi.typ"
#include "poglavlja/10-pregled-sistema.typ"
#include "poglavlja/11-zakljucak.typ"

#set heading(numbering: none)
#show outline: set heading(outlined: true)
#context {
  if query(figure.where(kind: image)).len() > 0 [
    = Списак слика
    <spisak-slika>
    #outline(title: none, target: figure.where(kind: image))
  ]

  if query(figure.where(kind: image)).len() > 0 [
    = Списак листинга
    <spisak-listinga>
    #outline(title: none, target: figure.where(kind: raw))
  ]

  if query(figure.where(kind: table)).len() > 0 [
    = Списак табела
    <spisak-tabela>
    #outline(title: none, target: figure.where(kind: table))
  ]
}


#show figure: it => {
  set text(size: 9pt)
  set block(breakable: true)
  set table(
    columns: (1fr, 4fr),
    align: left,
    inset: 8pt,
    stroke: 0pt,
  )
  it
}

#include "poglavlja/dodatak 1 - skracenice.typ"
#include "poglavlja/dodatak 2 - pojmovi.typ"

#show "Available at:": "Доступно на: "
#bibliography(title: [Литература], "literatura.bib")
#checkbib()

#todos()
