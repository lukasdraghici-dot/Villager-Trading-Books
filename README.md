# Villager Books (Fabric, Minecraft 1.21.11, nur Client)

* Bessere Handelsübersicht neben dem Villager-Fenster: alle Trades als Zeile mit Preis -> Ergebnis,
  Klartext-Name der Verzauberung (z.B. "Reparatur"), Mending-Bücher grün + Stern markiert,
  ausverkaufte Trades grau, Preis grün = Rabatt, rot = teurer als Basispreis.
* Taste B (änderbar unter Steuerung > Villager-Bücher): zeigt per HUD die Bücher des Villagers,
  den du ansiehst (oder des zuletzt gehandelten). Der Villager muss einmal geöffnet worden sein.

## Bauen
Benötigt JDK 21.
    ./gradlew build        (Windows: gradlew.bat build)
Jar liegt danach in build/libs/villagerbooks-1.0.0.jar (nicht die -sources.jar).

## Installieren
Fabric Loader + Fabric API für 1.21.11 installieren, Jar in den mods-Ordner.
Auf dem Server muss nichts installiert werden.
