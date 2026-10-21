# MissileWars-Advanced-Map-Voting
Ein verbessertes Map Voting für MissileWars.

Addon für [MissileWars](https://github.com/RedstoneFuture/missilewars) (gebaut für MissileWars 5.4.0-Dev_26.1 auf Paper 26.2 mit Java 25).
Erweitert das Map-Voting um mehrere Votes pro Spieler und fügt `/mw forcemap` hinzu. MissileWars selbst bleibt unverändert.

## Befehle
| Befehl | Recht | Beschreibung |
|---|---|---|
| `/mw vote <map>` | `mw.vote` | Einen Vote geben (Original-Befehl, neue Logik) |
| `/mw voteall <map>` | `mw.vote` | Alle übrigen Votes auf die Map (braucht `mw.vote.stack`) |
| `/mw unvote [map]` | `mw.vote` | Einen Vote entfernen; ohne Map alle |
| `/mw clearvotes` | `mw.vote` | Alle eigenen Votes entfernen |
| `/mw forcemap <map> [spiel]` | `mw.forcemap` | Map festlegen (nur in der Lobby, auch nach dem Voting) |
| `/mw mapvotingreload` | `mw.reload` | config.yml neu laden |

## Rechte
- `mw.vote.amount.<n>` – Anzahl der Votes (Standard 1, maximal `max-votes-limit`)
- `mw.vote.stack` – mehrere Votes auf dieselbe Map
- `mw.forcemap` – Map erzwingen (Standard: OP)

## Vote-Menü
- Linksklick: Vote geben · Shift+Linksklick: alle übrigen Votes geben
- Rechtsklick: Vote entfernen · Shift+Rechtsklick: alle Votes entfernen
- Barriere unten: alle Votes entfernen

## Bauen
1. Die MissileWars-Jar als `libs/MissileWars.jar` ablegen.
2. In `pom.xml` die `paper-api`-Version an deinen Server anpassen.
3. `mvn package` → `target/MissileWarsMapVoting-<version>.jar`

## Funktionsweise
MissileWars bietet keine Addon-API. Das Addon ersetzt per Reflection das `MapVoting`-Objekt jedes Spiels und das `MapVoteMenu` jedes Spielers durch eigene Unterklassen und registriert seine Befehle im ACF-CommandManager von MissileWars.
