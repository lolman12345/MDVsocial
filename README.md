# MDVSocial 1.2.2

Plugin social modular para MDVCRAFT.

## Incluye

- Menus modulares en `plugins/MDVSocial/Menus/`.
- Titulos cosmeticos y rangos visuales.
- Placeholders de titulo para PlaceholderAPI.
- Puentes para GUIs externas.
- Sistema base de cartas/correo interno.
- Responder cartas directamente desde la lectura.
- Las cartas oficiales de MDVCRAFT no pueden bloquearse.

## Sistema de cartas

Comandos:

- `/correo` abre el menu de correo.
- `/carta buzon` abre el buzon.
- `/carta enviar <jugador> <mensaje>` envia una carta.
- `/carta bloquear <jugador>` bloquea cartas de ese jugador.
- `/carta desbloquear <jugador>` desbloquea cartas de ese jugador.
- `/carta bloqueados` muestra bloqueados.
- `/carta cancelar` cancela una escritura por chat.

Acciones para menus modulares:

- `OPEN_MAILBOX`
- `START_MAIL_SEND`
- `START_MAIL_BLOCK`
- `START_MAIL_UNBLOCK`
- `REPLY_MAIL`

Datos:

- `player-data.yml` guarda titulos.
- `mail-data.yml` guarda cartas y bloqueos.

Las cartas expiran automaticamente segun `mail.expire-after-days`.
