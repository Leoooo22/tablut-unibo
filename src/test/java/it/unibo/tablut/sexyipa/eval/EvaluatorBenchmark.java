package it.unibo.tablut.sexyipa.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import it.unibo.tablut.sexyipa.engine.BoardAshton;
import it.unibo.tablut.sexyipa.search.AlphaBetaSearcher;

/**
 * Confronto di velocità tra {@link SimpleEvaluator} e {@link AshtonEvaluator}.
 * Non gira con la build normale. Per lanciarlo:
 * <pre>
 * SEXYIPA_BENCH=1 ./gradlew test --tests '*EvaluatorBenchmark' --rerun
 * </pre>
 * Variabile opzionale SEXYIPA_BENCH_MS: tempo per ogni ricerca in millisecondi (default 2000).
 * Il risultato è stampato e salvato in build/reports/eval-benchmark.txt.
 */
@EnabledIfEnvironmentVariable(named = "SEXYIPA_BENCH", matches = "1")
class EvaluatorBenchmark {

    private static final int POSITIONS = 5000;
    private static final int WARMUP_ROUNDS = 30;
    private static final int MEASURED_ROUNDS = 100;
    private static final int SEARCH_POSITIONS = 5;

    /** Impedisce alla JIT di eliminare le chiamate il cui risultato non viene usato. */
    private static long sink;

    @Test
    void compareEvaluators() throws IOException {
        long searchMs = Long.parseLong(System.getenv().getOrDefault("SEXYIPA_BENCH_MS", "2000"));
        Evaluator simple = new SimpleEvaluator();
        Evaluator ashton = new AshtonEvaluator();

        List<BoardAshton> positions = Positions.random(POSITIONS, 42);
        StringBuilder out = new StringBuilder();
        out.append("== Valutazione pura (").append(POSITIONS).append(" posizioni casuali) ==\n");
        double simpleNs = nsPerEval(simple, positions);
        double ashtonNs = nsPerEval(ashton, positions);
        out.append(String.format(Locale.ROOT, "SimpleEvaluator : %8.1f ns/valutazione%n", simpleNs));
        out.append(String.format(Locale.ROOT, "AshtonEvaluator : %8.1f ns/valutazione  (%.1fx)%n",
                ashtonNs, ashtonNs / simpleNs));

        out.append("\n== Costo dei singoli termini di AshtonEvaluator ==\n");
        out.append(termLine("openRoutes", positions, b -> AshtonEvaluator.openRoutes(b, b.kingPos())));
        out.append(termLine("kingCapturable", positions,
                b -> AshtonEvaluator.kingCapturable(b, b.kingPos()) ? 1 : 0));
        out.append(termLine("preEscapeSquares", positions, b -> AshtonEvaluator.preEscapeSquares(b, b.kingPos())));
        out.append(termLine("hostileAdjacent", positions, b -> AshtonEvaluator.hostileAdjacent(b, b.kingPos())));
        out.append(termLine("blackNearKing", positions, b -> AshtonEvaluator.blacksNearKing(b, b.kingPos())));
        out.append(termLine("guardSquares", positions, AshtonEvaluator::guardSquares));

        out.append("\n== Ricerca alpha-beta, ").append(searchMs).append(" ms per posizione ==\n");
        out.append(String.format(Locale.ROOT, "%-10s %-16s %6s %14s %12s%n",
                "posizione", "evaluator", "prof.", "nodi", "nodi/s"));
        List<BoardAshton> searchSet = new ArrayList<>();
        searchSet.add(new BoardAshton());
        searchSet.addAll(Positions.random(SEARCH_POSITIONS - 1, 7));
        for (int i = 0; i < searchSet.size(); i++) {
            String name = i == 0 ? "iniziale" : "casuale " + i;
            out.append(searchLine(name, "SimpleEvaluator", simple, searchSet.get(i), searchMs));
            out.append(searchLine(name, "AshtonEvaluator", ashton, searchSet.get(i), searchMs));
        }
        out.append("\n(sink ").append(sink).append(")\n");

        String report = out.toString();
        System.out.println(report);
        Path file = Path.of("build", "reports", "eval-benchmark.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report);
    }

    private static double nsPerEval(Evaluator e, List<BoardAshton> positions) {
        for (int r = 0; r < WARMUP_ROUNDS; r++) {
            runRound(e, positions);
        }
        long start = System.nanoTime();
        for (int r = 0; r < MEASURED_ROUNDS; r++) {
            runRound(e, positions);
        }
        long elapsed = System.nanoTime() - start;
        return (double) elapsed / ((long) MEASURED_ROUNDS * positions.size());
    }

    private static String termLine(String name, List<BoardAshton> positions, Evaluator term) {
        return String.format(Locale.ROOT, "%-17s: %8.1f ns%n", name, nsPerEval(term, positions));
    }

    private static void runRound(Evaluator e, List<BoardAshton> positions) {
        long acc = 0;
        for (BoardAshton b : positions) {
            acc += e.evaluate(b);
        }
        sink += acc;
    }

    private static String searchLine(String position, String name, Evaluator e, BoardAshton start, long ms) {
        BoardAshton b = Positions.withTurn(start, start.whiteToMove()); // copia: ogni ricerca parte pulita
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(e);
        long t0 = System.nanoTime();
        int move = searcher.bestMove(b, t0 + ms * 1_000_000L);
        double seconds = (System.nanoTime() - t0) / 1e9;
        sink += move;
        return String.format(Locale.ROOT, "%-10s %-16s %6d %14d %12.0f%n",
                position, name, searcher.completedDepth(), searcher.nodes(), searcher.nodes() / seconds);
    }
}
