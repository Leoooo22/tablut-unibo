# SexyIpa — agente Tablut (regole Ashton)

## Clone

```bash
git clone --recurse-submodules <url-del-repo>
```

Se il repository è già stato clonato senza submodule: `git submodule update --init`.

## Build

Serve un JDK 17 (con versioni più recenti Gradle potrebbe non partire).

```bash
./gradlew build    # produce build/libs/SexyIpa.jar
```

## Esecuzione

```bash
java -jar build/libs/SexyIpa.jar <WHITE|BLACK> <TIMEOUT_SECONDI> <SERVER_IP>
```
