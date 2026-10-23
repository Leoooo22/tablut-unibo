#!/bin/bash
# Uso: ./runmyplayer.sh <WHITE|BLACK> <TIMEOUT_SECONDI> <SERVER_IP>
cd /home/tablut/tablut || exit 1
exec java -Xmx6g -jar SexyIpa.jar "$1" "$2" "$3"
