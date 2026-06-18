# MDVSocial 1.2.4

Plugin social modular para MDVCRAFT.

## Incluye

- Menús modulares en `plugins/MDVSocial/Menus/`.
- Títulos cosméticos y rangos visuales.
- Placeholders de título para PlaceholderAPI.
- Placeholders inteligentes de party para scoreboard.
- Puentes para GUIs externas.
- Menú configurable de opciones de amigo desde la lista de MMOCore.
- Sistema base de cartas/correo interno.
- Responder cartas directamente desde la lectura.
- Las cartas oficiales de MDVCRAFT no pueden bloquearse.

## Placeholders de título

- `%mdvsocial_title%`
- `%mdvsocial_title_colored%`
- `%mdvsocial_title_prefix%`
- `%mdvsocial_active_title%`
- `%mdvsocial_unlocked_titles%`

## Placeholders de party para scoreboard

Estos placeholders usan MMOCore si está activo. Si el jugador no está en party, las líneas visuales devuelven vacío para no mostrar la sección de grupo.

- `%mdvsocial_party_header%` → `Grupo: actual/max`
- `%mdvsocial_party_member_1%`
- `%mdvsocial_party_member_2%`
- `%mdvsocial_party_member_3%`
- `%mdvsocial_party_member_4%`
- `%mdvsocial_party_member_5%`
- `%mdvsocial_party_count%`
- `%mdvsocial_party_max%`
- `%mdvsocial_party_members%`
- `%mdvsocial_party_in_group%`
- `%mdvsocial_party_spacer%`

El máximo se lee desde:

```yaml
social-friend-options:
  party:
    max-members: 5
```

## Sistema de cartas

Comandos:

- `/correo` abre el menú de correo.
- `/carta buzon` abre el buzón.
- `/carta enviar <jugador> <mensaje>` envía una carta.
- `/carta bloquear <jugador>` bloquea cartas de ese jugador.
- `/carta desbloquear <jugador>` desbloquea cartas de ese jugador.
- `/carta bloqueados` muestra bloqueados.
- `/carta cancelar` cancela una escritura por chat.

Acciones para menús modulares:

- `OPEN_MAILBOX`
- `START_MAIL_SEND`
- `START_MAIL_SEND_TARGET`
- `START_MAIL_BLOCK`
- `START_MAIL_UNBLOCK`
- `REPLY_MAIL`
- `INVITE_PARTY_TARGET`

Datos:

- `player-data.yml` guarda títulos.
- `mail-data.yml` guarda cartas y bloqueos.

Las cartas expiran automáticamente según `mail.expire-after-days`.
