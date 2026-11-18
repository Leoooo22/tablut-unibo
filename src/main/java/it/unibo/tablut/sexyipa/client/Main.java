package it.unibo.tablut.sexyipa.client;

import java.util.Locale;

/**
 * Punto di ingresso: java -jar SexyIpa.jar <WHITE|BLACK> <TIMEOUT> <SERVER_IP>
 */
public final class Main {

    public static final String PLAYER_NAME = "SexyIpa";

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length < 3) {
            fail("servono 3 argomenti");
        }
        String role = args[0].trim().toUpperCase(Locale.ROOT);
        if (!role.equals("WHITE") && !role.equals("BLACK")) {
            fail("ruolo non valido: " + args[0]);
        }
        int timeout = 0;
        try {
            timeout = Integer.parseInt(args[1].trim());
        } catch (NumberFormatException e) {
            fail("timeout non intero: " + args[1]);
        }
        if (timeout < 1) {
            fail("timeout deve essere >= 1: " + args[1]);
        }
        String serverIp = args[2].trim();

        System.out.println(PLAYER_NAME + " " + role + " timeout=" + timeout + "s server=" + serverIp);

        try {
            SexyIpaClient client = new SexyIpaClient(role, PLAYER_NAME, timeout, serverIp);
            client.run();
        } catch (Exception e) {
            System.err.println("Errore fatale all'avvio del client:");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void fail(String reason) {
        System.err.println("Errore: " + reason);
        System.err.println("Uso: runmyplayer.sh <WHITE|BLACK> <TIMEOUT_SECONDI> <SERVER_IP>");
        System.exit(2);
    }
}
