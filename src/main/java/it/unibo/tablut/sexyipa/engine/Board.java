package it.unibo.tablut.sexyipa.engine;

/**
 * Indice cella = riga * 9 + colonna; mossa = from * 81 + to.
 */
public interface Board {

    int ONGOING = 0;
    int WHITE_WINS = 1;
    int BLACK_WINS = 2;

    // Valori delle celle restituiti da cell()
    byte EMPTY = 0;
    byte WHITE = 1;
    byte BLACK = 2;
    byte KING = 3;

    int generateMoves(int[] out); // riempie out, restituisce il numero di mosse
    void makeMove(int move);      // applica mossa e catture
    void undoMove();              // annulla l'ultima mossa
    int result();                 // ONGOING, WHITE_WINS, BLACK_WINS
    boolean whiteToMove();
    long hash();                  // Zobrist

    // accesso in sola lettura per la valutazione
    byte cell(int index);
    int kingPos();
    int whiteCount();             // soldati bianchi, re escluso
    int blackCount();
}
