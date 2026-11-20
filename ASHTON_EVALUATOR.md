# AshtonEvaluator — logica della funzione di valutazione

Questo documento spiega come funziona `AshtonEvaluator`, la funzione di valutazione pensata per sostituire `SimpleEvaluator` nel player SexyIpa (regole Ashton).

File coinvolti:

| File | Contenuto |
|---|---|
| `src/main/java/it/unibo/tablut/sexyipa/eval/AshtonEvaluator.java` | L'evaluator |
| `src/main/java/it/unibo/tablut/sexyipa/eval/Weights.java` | Tutti i pesi, in un unico punto |
| `src/test/java/it/unibo/tablut/sexyipa/eval/AshtonEvaluatorTest.java` | Test di correttezza |
| `src/test/java/it/unibo/tablut/sexyipa/eval/EvaluatorBenchmark.java` | Test di velocità (si avvia a mano) |
| `src/test/java/it/unibo/tablut/sexyipa/eval/Positions.java` | Funzioni di supporto per test e benchmark |

---

## 1. Vincoli da rispettare

L'evaluator implementa l'interfaccia `Evaluator` già esistente e usa solo:
- i metodi di lettura di `Board` (`cell`, `kingPos`, `whiteCount`, `blackCount`, `whiteToMove`);
- le tabelle statiche di `Tables`.

Non richiede modifiche a `engine/`, `search/` o `client/`.

I vincoli imposti dalla ricerca (`AlphaBetaSearcher`) sono:

1. **Il punteggio è dal punto di vista di chi muove** (convenzione negamax). Internamente l'evaluator calcola il punteggio per il Bianco (`whiteScore`), poi `evaluate` cambia il segno se muove il Nero.
2. **Il punteggio resta lontano dal valore di vittoria.** La ricerca usa `WIN = 1.000.000` per le vittorie certe e smette di approfondire quando un punteggio supera `WIN − 128`. I valori più alti dell'evaluator sono intorno a 50.000, quindi non vengono mai scambiati per una vittoria certa.
3. **L'evaluator non alloca memoria** durante la valutazione: viene chiamato su ogni foglia della ricerca. Tutte le strutture di supporto sono calcolate una volta sola, all'avvio, in blocchi `static`.

---

## 2. Idea di fondo: guardare chi muove

Il punto debole di una ricerca a profondità limitata è l'**effetto orizzonte**. Se una posizione si decide alla mossa successiva, ma quella mossa cade appena oltre la profondità raggiunta, la ricerca non la vede. Esempi: il re ha una strada libera verso un'uscita, oppure il Nero può catturare il re.

Per questo l'evaluator lavora in due fasi:

1. **Fase tattica.** Controlla se la posizione è già decisa alla prossima mossa, tenendo conto di **chi deve muovere**. In quel caso restituisce subito un punteggio molto alto o molto basso.
2. **Fase posizionale.** Se nulla è deciso, somma alcuni termini che descrivono quanto la posizione è favorevole.

`SimpleEvaluator` non distingue chi muove. `AshtonEvaluator` sì: "il re ha una via libera" vale quasi una vittoria se muove il Bianco, ma molto meno se muove il Nero, che può ancora chiuderla.

---

## 3. Fase tattica

Le situazioni vengono controllate in quest'ordine (punteggi dal lato del Bianco):

| Situazione | Punteggio | Motivo |
|---|---|---|
| Muove il Bianco e il re ha almeno una via di fuga libera | `+escapeNow` (+50.000) | Il re raggiunge l'uscita con la prossima mossa |
| Muove il Nero e può catturare il re con una mossa | `−kingCapturableNow` (−50.000) | Il Nero vince con la prossima mossa |
| Muove il Nero e il re ha 2 o più vie libere | `+doubleRoute` (+40.000) | Il Nero può chiuderne una sola: una casella non può stare su due direzioni diverse a partire dal re |

A ciascuno di questi valori si somma il materiale, così due posizioni entrambe "decise" restano ordinate in modo sensato.

L'ordine conta. Se muove il Nero e può catturare il re, la cattura viene prima delle vie di fuga: il Nero vince subito.

Se muove il Bianco e il re ha una via libera, l'evaluator esce prima di calcolare se il re è catturabile. È un'ottimizzazione: in quel caso la cattura non conta.

### 3.1 Via di fuga libera (`openRoute`, `openRoutes`)

Una direzione del re è una via di fuga libera se tutte le caselle fino al bordo sono vuote e nessuna è un campo o il castello. In quel caso l'ultima casella è sul bordo e non è un campo, cioè è un'uscita.

**Gli angoli.** Il motore marca come uscite anche i 4 angoli (`Tables`), mentre la slide 5 della presentazione no. Per questo evaluator la differenza non conta. Una linea dal re finisce su un angolo solo se il re è già sul bordo, cioè ha già vinto. Inoltre le caselle accanto a ogni angolo sono tutte uscite, quindi in una partita in corso il re non arriva mai su un angolo.

### 3.2 Re catturabile con una mossa (`kingCapturable`)

Le regole sono le stesse di `BoardAshton.kingCaptured`, applicate "una mossa prima":

- **Re sul castello o accanto al castello** (`CASTLE_AREA`): servono neri su tutti i lati diversi dal castello (4 sul castello, 3 accanto). Il re è catturabile se manca **esattamente un** nero, quella casella è vuota e un nero può arrivarci con una mossa. Se su uno di quei lati c'è un bianco, la cattura è impossibile.
- **Altrove:** per ciascuno dei due assi (verticale e orizzontale), un lato è ostile (un nero oppure un campo) e sul lato opposto c'è una casella libera che un nero può raggiungere.

La casella libera **può essere un campo vuoto**. Un nero ancora dentro il proprio campo può spostarsi su un'altra casella dello stesso campo, accanto al re, e catturarlo. Questo caso è stato scoperto dal test che confronta l'evaluator con il motore (sezione 7) ed è coperto da un test dedicato.

### 3.3 Un nero può raggiungere una casella? (`blackCanReach`)

Partendo dalla casella di destinazione, si percorrono le 4 linee verso l'esterno fino al primo pezzo. Se è un nero, e la mossa inversa sarebbe legale, la casella è raggiungibile. Le regole sono le stesse di `BoardAshton.generateMoves`:
- il castello blocca il passaggio;
- un nero può attraversare caselle di campo, compresa quella d'arrivo, solo se parte dallo **stesso gruppo di campo**. Un nero che è già uscito dal suo campo non può più entrare in nessun campo.

---

## 4. Fase posizionale

Se la fase tattica non si conclude, il punteggio del Bianco è la somma di questi termini (pesi in `Weights.DEFAULT`; scala: un soldato nero vale 100):

| Termine | Peso | Calcolo |
|---|---|---|
| Materiale | +200 per soldato bianco, −100 per soldato nero | Rapporto 2:1, perché i neri sono il doppio (16 contro 8) |
| Una sola via libera, muove il Nero | +300 (`singleRoute`) | Il Nero è costretto a usare la mossa per chiuderla |
| Re minacciato, muove il Bianco | −400 (`kingThreatened`) | Il Bianco deve rispondere alla minaccia, ma può ancora farlo |
| Caselle da cui il re avrebbe una via libera | +150 ciascuna, massimo 4 (`preEscapeSquare`, `preEscapeCap`) | Sezione 4.1 |
| Pezzi ostili adiacenti al re | −60 ciascuno (`hostileAdjacent`) | Un nero conta sempre; un campo conta solo se il re è fuori dall'area del castello, perché solo lì il campo partecipa alla cattura |
| Neri a distanza 2 dal re | −25 ciascuno (`blackNearKing`) | Due caselle in linea retta oppure una in diagonale: misura quanto il re è accerchiato |
| Neri sulle caselle di guardia | −80 ciascuno (`guardSquare`) | Sezione 4.2 |

### 4.1 Caselle da cui il re avrebbe una via libera (`preEscapeSquares`)

Il re non ha ancora una via libera, ma si chiede: "con una mossa, posso arrivare in una casella da cui avrei una via libera?". Per ogni casella che il re raggiunge con una mossa (esclusa un'uscita), si controllano le due direzioni **perpendicolari** a quella di arrivo. La direzione di arrivo non conta: se fosse libera, il re avrebbe già una via diretta.

Più caselle di questo tipo ci sono, più è difficile per il Nero coprirle tutte. Il conteggio è limitato a 4 per evitare che domini il punteggio. È una versione semplificata e più veloce della *King Distance to Victory* di OpenTafl.

### 4.2 Caselle di guardia

Attorno all'angolo a1 le uscite sono b1, c1, a2 e a3. Un nero in **b3** chiude la colonna b e la riga 3; un nero in **c2** chiude la colonna c e la riga 2. In due, chiudono tutte e 4 le uscite di quell'angolo. Per simmetria, le 8 caselle di guardia sono:

```
b3 c2   g2 h3   b7 c8   g8 h7
```

Ogni nero su una di queste caselle abbassa il punteggio del Bianco.

### 4.3 Cosa non c'è più rispetto a SimpleEvaluator

Il termine "distanza del re dal bordo" è stato tolto. Lo sostituiscono le vie libere e le caselle da cui il re avrebbe una via libera, che misurano meglio la stessa cosa: non conta la distanza dal bordo, ma se la strada è libera.

---

## 5. Da dove vengono i pesi

**I pesi sono scelti a mano: non sono tarati su partite né calcolati da dati.** Origine di ciascuno:

| Peso | Origine |
|---|---|
| 50.000 / 40.000 (tattici) | Ragionamento sulle regole: devono essere molto grandi, ma lontani da `WIN` |
| 200 / 100 (materiale) | Rapporto 2:1 ripreso da PyTablut (+20/−10) e da `SimpleEvaluator` |
| 60 (pezzi ostili adiacenti) | Semplificazione dei −20/−30/−50 di PyTablut, che li differenzia in base alla posizione del re |
| 150 / 25 / 80 / 300 / 400 | Scelti da noi; le idee vengono da OpenTafl (KDV), Fasulo (accerchiamento, pesi per casella) e dalla geometria delle uscite |

Fonti consultate:
- PyTablut, strategia: https://bardreamaster.github.io/pytablut/development/strategy.html
- OpenTafl, King Distance to Victory: https://soapbox.manywords.press/?p=4182
- MatteoFasulo/Tablut, euristiche: https://matteofasulo.github.io/Tablut/2-heuristics

I pesi stanno tutti in `Weights` (un `record`) proprio per poterli tarare senza toccare la logica. Per esempio:

```java
new AshtonEvaluator(new Weights(200, 100, 50_000, 50_000, 40_000, 300, 400, 150, 4, 60, 25, 80));
```

---

## 6. Ottimizzazioni

L'evaluator viene chiamato su ogni foglia della ricerca, quindi il suo costo si paga in profondità raggiunta. Le ottimizzazioni sfruttano il fatto che la geometria della scacchiera non cambia mai:

- **`ROUTE_CANDIDATE[cella][direzione]`**: indica se una linea arriva al bordo senza campi né castello. Le linee che non sono candidate non diventeranno mai vie di fuga e si scartano senza leggere la scacchiera.
- **`STOP_LIMIT[cella][direzione]`**: quante caselle della linea precedono il primo campo o il castello. Oltre quel punto il re non può arrivare.
- **`PRE_ESCAPE_CANDIDATE[cella][direzione][j]`**: indica se la j-esima casella della linea può avere una via laterale. Le caselle che non possono si saltano.
- **`RING2`** e **`GUARDS`**: le caselle a distanza 2 da ogni cella e le 8 caselle di guardia, precalcolate.
- **Uscita anticipata**: se muove il Bianco e il re ha una via libera, non si calcola se il re è catturabile.

Misure su Apple M1 (i tempi variano del 20–30% tra un'esecuzione e l'altra):

| | Prima delle ottimizzazioni | Dopo |
|---|---|---|
| Tempo per valutazione | ~259 ns | ~155–208 ns |
| `SimpleEvaluator`, per confronto | ~10 ns | ~10 ns |
| Nodi al secondo in ricerca, rispetto a `SimpleEvaluator` | circa ⅓ | circa ½ |

Costo dei singoli termini dopo le ottimizzazioni: caselle da cui il re avrebbe una via libera ~100 ns, re catturabile ~25–29 ns, vie libere ~15–18 ns, gli altri ~11–22 ns ciascuno. Il termine sulle caselle da cui il re avrebbe una via libera è quindi circa due terzi del costo totale, ed è il primo candidato da eliminare se le partite mostrassero che non vale il suo costo.

In ricerca, con 2 secondi per posizione, `AshtonEvaluator` raggiunge la stessa profondità di `SimpleEvaluator` o un livello in meno.

---

## 7. Come è stato verificato

### Test di correttezza (`AshtonEvaluatorTest`, 12 test)

- **Posizioni costruite a mano:**
  - via libera con il Bianco al tratto;
  - cattura del re contro un campo, sul castello e dentro un campo;
  - un nero che non può attraversare i campi;
  - il conteggio delle caselle da cui il re avrebbe una via libera;
  - le caselle di guardia;
  - il cambio di segno al cambio di turno.
  
  Dove serve, lo scenario viene confermato giocando la mossa sul motore (`BoardAshton`) e controllando che il risultato sia davvero `BLACK_WINS`.
- **Confronto con il motore su posizioni casuali** (migliaia di posizioni generate con mosse casuali e seed fisso):
  - "il re ha una via libera" deve coincidere con "esiste una mossa legale del re su un'uscita";
  - "il re è catturabile" deve coincidere con "esiste una mossa legale del nero che porta a `BLACK_WINS`".
  
  Così l'evaluator non può divergere dalle regole del motore senza che un test fallisca.
- **Simmetria:** applicando alla scacchiera una qualsiasi delle 8 simmetrie del quadrato (rotazioni e riflessioni), il punteggio non cambia.
- **Limiti:** su posizioni casuali il punteggio resta sotto ±100.000 in valore assoluto.

### Test di velocità (`EvaluatorBenchmark`)

Non gira con la build normale. Per avviarlo:

```bash
SEXYIPA_BENCH=1 ./gradlew test --tests '*EvaluatorBenchmark' --rerun
```

Misura il tempo per valutazione dei due evaluator, il costo di ogni termine e profondità e nodi al secondo della ricerca vera. Il risultato è salvato in `build/reports/eval-benchmark.txt`. La variabile opzionale `SEXYIPA_BENCH_MS` imposta i millisecondi per ogni ricerca (default 2000).

### Partite contro il server dei docenti

In locale, con il server e il client casuale di `TablutCompetition` e un timeout di 5 s per mossa, il player con `AshtonEvaluator` ha vinto sia da Bianco sia da Nero. Il server non ha segnalato mosse illegali, errori o timeout.

---

## 8. Come attivarlo nel player

Per ora il player usa ancora `SimpleEvaluator`. Per passare al nuovo evaluator bisogna cambiare una riga in `client/SexyIpaClient.java`:

```java
Evaluator evaluator = new AshtonEvaluator();   // al posto di new SimpleEvaluator()
```

e aggiungere l'import `it.unibo.tablut.sexyipa.eval.AshtonEvaluator`.

---

## 9. Limiti noti e lavoro futuro

- **Non è ancora dimostrato che giochi meglio di `SimpleEvaluator`.** Contro il giocatore casuale vincono entrambi in poche mosse. Servono partite dirette tra i due evaluator, a colori alternati.
- **I pesi non sono tarati.** Dopo le partite di confronto si possono regolare uno alla volta, oppure con un algoritmo genetico che fa giocare tra loro diverse combinazioni di `Weights`.
- **Il pareggio per ripetizione non è considerato.** È una regola del server (stesso stato due volte = pareggio), ma va gestita nella ricerca e nel client, non nell'evaluator.
- **Costo:** si perde circa un livello di profondità in alcune posizioni. Se le partite mostrassero che non conviene, il primo termine da togliere è quello sulle caselle da cui il re avrebbe una via libera.
